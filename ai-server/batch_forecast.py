"""매일 1회 실행되는 예측 배치 (v2 — ML팀 파이프라인 사용).

1. 공공데이터 API에서 최근 사과 경락 데이터를 수집해 ml/data/apple_auction_raw.csv 에 누적
   (API가 최근 약 30일만 보관하므로 매일 쌓는 것이 핵심)
2. ml/common_hist.build_daily_ext() 로 학습과 동일한 피처 생성
   (2021~2023 히스토리 + 계절성 피처 포함 — t_index 기준이 히스토리 시작일)
3. ml/models/final_daily_model2.joblib 으로 향후 7거래일 recursive 예측
4. 신뢰범위를 부여해 out/forecasts.json 으로 저장 (Java 백엔드가 조회 전용으로 사용)
"""
import json
import math
import os
import statistics
import sys
import time
from datetime import date, datetime, timedelta, timezone

import joblib
import pandas as pd

BASE = os.path.dirname(os.path.abspath(__file__))
ML_DIR = os.path.join(BASE, "ml")
sys.path.insert(0, ML_DIR)

import common            # noqa: E402  (ml 패키지 — RAW_CSV 경로 등)
import common_hist as ch  # noqa: E402  (학습과 동일한 피처 파이프라인)
import download_data as dl  # noqa: E402  (fetch_page 재사용)

MODEL_PATH = os.path.join(ML_DIR, "models", "final_daily_model2.joblib")
OUT_PATH = os.path.join(BASE, "out", "forecasts.json")

KST = timezone(timedelta(hours=9))
BACKFILL_DAYS = 30     # API 보관기간
MIN_HISTORY = 8        # 예측에 필요한 최소 거래일 수 (현행 데이터 기준)
HISTORY_DAYS = 14      # 응답에 포함할 최근 실제 시세 일수
FORECAST_HORIZON = 7   # 예측 거래일 수


def fetch_day_rows(day: str):
    """해당 일자의 전국 사과 경락 원천 데이터 전체 (download_data.fetch_page 재사용)."""
    first, total = dl.fetch_page(day, 1)
    rows = list(first)
    pages = (total + dl.NUM_ROWS - 1) // dl.NUM_ROWS
    for p in range(2, pages + 1):
        more, _ = dl.fetch_page(day, p)
        rows.extend(more)
        time.sleep(0.15)
    return rows


def update_auction_csv():
    """ml/data/apple_auction_raw.csv 를 오늘까지 증분 갱신 (기존 데이터 유지·누적)."""
    today = datetime.now(KST).date()
    if os.path.exists(common.RAW_CSV):
        df = pd.read_csv(common.RAW_CSV, low_memory=False)
        max_day = str(df["trd_clcln_ymd"].max())
        # 마지막 이틀은 당일 갱신분이 있을 수 있어 다시 수집
        start = max(date.fromisoformat(max_day) - timedelta(days=1),
                    today - timedelta(days=BACKFILL_DAYS))
    else:
        df = pd.DataFrame()
        start = today - timedelta(days=BACKFILL_DAYS)

    new_rows = []
    d = start
    while d <= today:
        day = d.isoformat()
        try:
            rows = fetch_day_rows(day)
            print(f"[collect] {day}: {len(rows)}건")
            new_rows.extend(rows)
        except Exception as e:
            print(f"[collect] {day} 수집 실패: {e}", file=sys.stderr)
        d += timedelta(days=1)

    if new_rows:
        new_df = pd.DataFrame(new_rows)
        if len(df) > 0:
            df = df[df["trd_clcln_ymd"] < start.isoformat()]
            df = pd.concat([df, new_df], ignore_index=True)
        else:
            df = new_df
        df.drop_duplicates(inplace=True)
        os.makedirs(os.path.dirname(common.RAW_CSV), exist_ok=True)
        df.to_csv(common.RAW_CSV, index=False, encoding="utf-8-sig")
        print(f"[collect] 저장: {common.RAW_CSV} (총 {len(df):,}행)")


def next_trading_days(last_date: date, count: int):
    """일요일(휴장)을 건너뛴 다음 count개 거래일."""
    days, d = [], last_date
    while len(days) < count:
        d = d + timedelta(days=1)
        if d.weekday() == 6:  # Sunday
            continue
        days.append(d)
    return days


def make_feature_row(market, variety, prices, prev_n, prev_vol, fdate: date, min_date, medians):
    """common_hist.build_daily_ext 와 동일한 정의로 미래 거래일 1행의 피처를 만든다."""
    row = {
        "whsl_mrkt_nm": market,
        "gds_sclsf_nm": variety,
        "lag1": prices[-1],
        "lag2": prices[-2] if len(prices) >= 2 else None,
        "lag3": prices[-3] if len(prices) >= 3 else None,
        # roll: shift(1).rolling(n, min_periods=1).mean() 과 동일
        "roll3": sum(prices[-3:]) / len(prices[-3:]),
        "roll7": sum(prices[-7:]) / len(prices[-7:]),
        # vol_std3: shift(1).rolling(3, min_periods=2).std() (ddof=1) 과 동일
        "vol_std3": statistics.stdev(prices[-3:]) if len(prices) >= 2 else None,
        "momentum": (prices[-1] - prices[-2]) if len(prices) >= 2 else None,
        "prev_n": prev_n,
        "prev_vol": prev_vol,
        "dow": fdate.weekday(),
        "t_index": (pd.Timestamp(fdate) - min_date).days,
        "month": fdate.month,
    }
    doy = fdate.timetuple().tm_yday
    row["doy_sin"] = math.sin(2 * math.pi * doy / 365.25)
    row["doy_cos"] = math.cos(2 * math.pi * doy / 365.25)
    for c in ch.NUMERIC:
        if row.get(c) is None and c in medians.index:
            row[c] = float(medians[c])
    return row


def forecast_all():
    bundle = joblib.load(MODEL_PATH)
    model, medians, features = bundle["model"], bundle["medians"], bundle["features"]
    try:
        enc = model.regressor_.named_steps["pre"].named_transformers_["cat"]
        known_markets, known_varieties = set(enc.categories_[0]), set(enc.categories_[1])
    except Exception:
        known_markets = known_varieties = None

    ext = ch.build_daily_ext(include_history=True)
    min_date = ext["date"].min()  # t_index 기준(히스토리 포함 시작일) — 학습과 동일
    cur = ext[ext["era"] == "cur"].copy()

    # 품종 미기재 물량(소분류명 "사과")은 "기타"에 합산 — 드롭다운에 "사과" 품종이 뜨지 않도록.
    # 같은 (시장, 기타, 날짜)로 합쳐질 수 있으므로 물량 가중으로 재집계한다.
    cur["gds_sclsf_nm"] = cur["gds_sclsf_nm"].replace({"사과": "기타"})
    cur = (cur.groupby(ch.GROUP_KEYS + ["date"])
              .agg(amount=("amount", "sum"), weight=("weight", "sum"), n=("n", "sum"))
              .reset_index())
    cur["vwap"] = cur["amount"] / cur["weight"]
    print(f"[forecast] 피처 프레임 {ext.shape}, 현행 구간 {cur.shape}, t_index 기준일 {min_date.date()}")

    combos_out = []
    for (market, variety), g in cur.groupby(ch.GROUP_KEYS):
        if known_markets is not None and (market not in known_markets or variety not in known_varieties):
            continue
        if len(g) < MIN_HISTORY:
            continue
        g = g.sort_values("date")
        prices = [float(v) for v in g["vwap"]]
        prev_n = float(g["n"].iloc[-1])
        prev_vol = float(g["weight"].iloc[-1])
        as_of = g["date"].iloc[-1].date()

        band = statistics.stdev(prices[-3:]) if len(prices) >= 3 else float(medians["vol_std3"])
        band = max(band, prices[-1] * 0.03)

        sim = list(prices)
        forecast = []
        for h, fdate in enumerate(next_trading_days(as_of, FORECAST_HORIZON), start=1):
            row = make_feature_row(market, variety, sim, prev_n, prev_vol, fdate, min_date, medians)
            x = pd.DataFrame([row])[features]
            pred = max(float(model.predict(x)[0]), 0.0)
            sim.append(pred)
            spread = band * (h ** 0.5) * 1.28
            forecast.append({
                "date": fdate.isoformat(),
                "price": int(round(pred)),
                "low": max(0, int(round(pred - spread))),
                "high": int(round(pred + spread)),
                "horizon": h,
            })

        history = [
            {"date": d.date().isoformat(), "price": int(round(p))}
            for d, p in list(zip(g["date"], prices))[-HISTORY_DAYS:]
        ]
        combos_out.append({
            "market": market,
            "variety": variety,
            "asOf": as_of.isoformat(),
            "history": history,
            "forecast": forecast,
        })

    out = {
        "generatedAt": datetime.now(KST).isoformat(timespec="seconds"),
        "markets": sorted({c["market"] for c in combos_out}),
        "varieties": sorted({c["variety"] for c in combos_out}),
        "combos": combos_out,
    }
    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    tmp = OUT_PATH + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False)
    os.replace(tmp, OUT_PATH)
    print(f"[forecast] {len(combos_out)}개 (시장x품종) 예측 저장 -> {OUT_PATH}")


def main():
    if not os.getenv("MARKET_API_KEY"):
        print("MARKET_API_KEY 환경변수가 필요합니다.", file=sys.stderr)
        sys.exit(1)
    update_auction_csv()
    forecast_all()


if __name__ == "__main__":
    main()
