"""매일 1회 실행되는 예측 배치.

1. 공공데이터 API에서 최근 사과 경락 데이터를 수집해 SQLite에 누적
   (API가 최근 약 30일만 보관하므로 매일 쌓는 것이 핵심)
2. (시장 x 품종)별 lag/roll 피처를 재계산
3. final_daily_model.joblib 으로 향후 7거래일 recursive 예측
4. 신뢰범위를 부여해 forecasts.json 으로 저장 (Java 백엔드가 조회 전용으로 사용)
"""
import json
import os
import sqlite3
import statistics
import sys
import time
from datetime import datetime, timedelta, timezone

import joblib
import pandas as pd
import requests

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "data", "daily_price.sqlite")
MODEL_PATH = os.path.join(BASE_DIR, "final_daily_model.joblib")
OUT_PATH = os.path.join(BASE_DIR, "out", "forecasts.json")

MARKET_API_URL = "https://apis.data.go.kr/B552845/katRealTime2/trades2"
MARKET_API_KEY = os.getenv("MARKET_API_KEY", "")
APPLE_LCLSF_CD = "06"  # 과실류
APPLE_MCLSF_CD = "01"  # 사과

KST = timezone(timedelta(hours=9))
BACKFILL_DAYS = 30     # API 보관기간
MIN_HISTORY = 8        # 예측에 필요한 최소 거래일 수
HISTORY_DAYS = 14      # 응답에 포함할 최근 실제 시세 일수
FORECAST_HORIZON = 7   # 예측 거래일 수


def _get_api(params, retries=2):
    for attempt in range(retries + 1):
        try:
            resp = requests.get(MARKET_API_URL, params=params, timeout=20)
            resp.raise_for_status()
            return resp.json()
        except Exception:
            if attempt == retries:
                raise
            time.sleep(1)


def fetch_day_rows(day: str):
    """해당 일자의 전국 사과 경락 원천 데이터를 전부 수집."""
    rows, page = [], 1
    while page <= 10:  # 안전 상한 (사과는 하루 5천건 내외)
        params = {
            "serviceKey": MARKET_API_KEY,
            "cond[trd_clcln_ymd::EQ]": day,
            "cond[gds_lclsf_cd::EQ]": APPLE_LCLSF_CD,
            "cond[gds_mclsf_cd::EQ]": APPLE_MCLSF_CD,
            "numOfRows": 1000,
            "pageNo": page,
            "returnType": "json",
        }
        body = _get_api(params)["response"]["body"]
        items = body.get("items") or {}
        page_rows = items.get("item") or []
        if isinstance(page_rows, dict):
            page_rows = [page_rows]
        rows.extend(page_rows)
        if page * 1000 >= int(body.get("totalCount", 0)):
            break
        page += 1
    return rows


def aggregate_day(day: str):
    """일자 데이터를 (시장, 품종) 단위로 집계: vwap(원/kg), 거래량(kg), 건수."""
    acc = {}
    for r in fetch_day_rows(day):
        try:
            prc = float(r["scsbd_prc"])
            unit_kg = float(r["unit_qty"])
            qty = float(r["qty"])
        except (KeyError, TypeError, ValueError):
            continue
        if prc <= 0 or unit_kg <= 0 or qty <= 0 or (r.get("unit_nm") or "kg") != "kg":
            continue
        key = (r.get("whsl_mrkt_nm") or "", r.get("gds_sclsf_nm") or "사과")
        won, kg, n = acc.get(key, (0.0, 0.0, 0))
        acc[key] = (won + prc * qty, kg + unit_kg * qty, n + 1)
    return [
        (day, market, variety, round(won / kg, 2), round(kg, 1), n)
        for (market, variety), (won, kg, n) in acc.items()
        if kg > 0
    ]


def ensure_db():
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS daily_price (
            trade_date TEXT NOT NULL,
            market     TEXT NOT NULL,
            variety    TEXT NOT NULL,
            vwap       REAL NOT NULL,
            volume_kg  REAL NOT NULL,
            n_trades   INTEGER NOT NULL,
            PRIMARY KEY (trade_date, market, variety)
        )
    """)
    return conn


def collect(conn):
    """최근 BACKFILL_DAYS 중 DB에 없는 날짜 + 최근 2일(당일 데이터 갱신)을 수집."""
    today = datetime.now(KST).date()
    have = {r[0] for r in conn.execute("SELECT DISTINCT trade_date FROM daily_price")}
    targets = []
    for i in range(BACKFILL_DAYS):
        d = (today - timedelta(days=i)).strftime("%Y-%m-%d")
        if d not in have or i <= 1:
            targets.append(d)
    for day in sorted(targets):
        try:
            recs = aggregate_day(day)
        except Exception as e:
            print(f"[collect] {day} 수집 실패: {e}", file=sys.stderr)
            continue
        conn.execute("DELETE FROM daily_price WHERE trade_date = ?", (day,))
        conn.executemany(
            "INSERT OR REPLACE INTO daily_price VALUES (?,?,?,?,?,?)", recs)
        conn.commit()
        print(f"[collect] {day}: {len(recs)}개 (시장x품종) 저장")


def build_features_row(prices, vols, ns, dow, t_index, medians):
    """직전까지의 시계열로 다음 거래일 예측용 피처 1행을 만든다."""
    def mean(xs):
        return sum(xs) / len(xs) if xs else None

    lag1 = prices[-1] if len(prices) >= 1 else None
    lag2 = prices[-2] if len(prices) >= 2 else None
    lag3 = prices[-3] if len(prices) >= 3 else None
    roll3 = mean(prices[-3:]) if len(prices) >= 3 else None
    roll7 = mean(prices[-7:]) if len(prices) >= 7 else None
    vol_std3 = statistics.stdev(prices[-3:]) if len(prices) >= 3 else None
    momentum = (lag1 - lag2) if (lag1 is not None and lag2 is not None) else None
    row = {
        "lag1": lag1, "lag2": lag2, "lag3": lag3,
        "roll3": roll3, "roll7": roll7, "vol_std3": vol_std3,
        "momentum": momentum,
        "prev_n": ns[-1] if ns else None,
        "prev_vol": vols[-1] if vols else None,
        "dow": dow, "t_index": t_index,
    }
    for k, v in row.items():
        if v is None and k in medians.index:
            row[k] = float(medians[k])
    return row


def next_trading_days(last_date, count):
    """일요일(휴장)을 건너뛴 다음 count개 거래일."""
    days, d = [], last_date
    while len(days) < count:
        d = d + timedelta(days=1)
        if d.weekday() == 6:  # Sunday
            continue
        days.append(d)
    return days


def forecast_all(conn):
    bundle = joblib.load(MODEL_PATH)
    model, medians, features = bundle["model"], bundle["medians"], bundle["features"]
    enc = model.regressor_.named_steps["pre"].named_transformers_["cat"]
    known_markets = set(enc.categories_[0])
    known_varieties = set(enc.categories_[1])

    df = pd.read_sql_query(
        "SELECT * FROM daily_price ORDER BY trade_date", conn)
    combos_out = []
    for (market, variety), g in df.groupby(["market", "variety"]):
        if market not in known_markets or variety not in known_varieties:
            continue
        if len(g) < MIN_HISTORY:
            continue
        g = g.sort_values("trade_date")
        dates = [datetime.strptime(d, "%Y-%m-%d").date() for d in g["trade_date"]]
        prices = list(g["vwap"])
        vols = list(g["volume_kg"])
        ns = list(g["n_trades"])
        as_of = dates[-1]

        band_std = statistics.stdev(prices[-3:]) if len(prices) >= 3 else float(medians["vol_std3"])
        band_std = max(band_std, prices[-1] * 0.03)

        sim_prices = list(prices)
        forecast = []
        for h, fdate in enumerate(next_trading_days(as_of, FORECAST_HORIZON), start=1):
            row = build_features_row(
                sim_prices, vols, ns, fdate.weekday(),
                len(sim_prices), medians)
            row["whsl_mrkt_nm"] = market
            row["gds_sclsf_nm"] = variety
            x = pd.DataFrame([row])[features]
            pred = float(model.predict(x)[0])
            pred = max(pred, 0.0)
            sim_prices.append(pred)
            spread = band_std * (h ** 0.5) * 1.28
            forecast.append({
                "date": fdate.strftime("%Y-%m-%d"),
                "price": int(round(pred)),
                "low": max(0, int(round(pred - spread))),
                "high": int(round(pred + spread)),
                "horizon": h,
            })

        history = [
            {"date": d.strftime("%Y-%m-%d"), "price": int(round(p))}
            for d, p in list(zip(dates, prices))[-HISTORY_DAYS:]
        ]
        combos_out.append({
            "market": market,
            "variety": variety,
            "asOf": as_of.strftime("%Y-%m-%d"),
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
    if not MARKET_API_KEY:
        print("MARKET_API_KEY 환경변수가 필요합니다.", file=sys.stderr)
        sys.exit(1)
    conn = ensure_db()
    collect(conn)
    forecast_all(conn)


if __name__ == "__main__":
    main()
