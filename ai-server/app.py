import os
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta

import requests
from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
import pandas as pd
import numpy as np
from prophet import Prophet
import openai  # OpenAI API 호출용

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 키가 없으면 LLM 호출이 실패하고 아래 fallback 문장이 사용됨
openai.api_key = os.getenv("OPENAI_API_KEY", "")

# 공공데이터포털 - 공영도매시장 실시간 경락 데이터
MARKET_API_URL = "https://apis.data.go.kr/B552845/katRealTime2/trades2"
MARKET_API_KEY = os.getenv("MARKET_API_KEY", "")
APPLE_LCLSF_CD = "06"  # 과실류
APPLE_MCLSF_CD = "01"  # 사과
SERIES_DAYS = 35       # Prophet 학습에 사용할 과거 일수

# 일자별 조회 결과 캐시: (date, market_code) -> {"price": float|None, "market_nm": str, "variety": str}
_daily_cache = {}
# 대시보드 결과 캐시: (date, market_code) -> (timestamp, payload)
_dashboard_cache = {}
DASHBOARD_CACHE_TTL = 600  # 10분


def _get_market_api(params):
    """공공데이터 게이트웨이가 간헐적으로 502를 반환하므로 1회 재시도한다."""
    for attempt in range(2):
        try:
            resp = requests.get(MARKET_API_URL, params=params, timeout=15)
            resp.raise_for_status()
            return resp.json()
        except Exception:
            if attempt == 1:
                raise
            time.sleep(0.5)


def fetch_daily_apple_price(day: str, market_code: str, variety_keyword: str = "후지"):
    """해당 일자의 사과 경락 데이터를 조회해 kg당 가중평균가를 계산한다.
    데이터가 없으면 price=None. 조회 실패한 날은 캐시하지 않고 그 날만 건너뛴다."""
    cache_key = (day, market_code)
    if cache_key in _daily_cache:
        return _daily_cache[cache_key]

    params = {
        "serviceKey": MARKET_API_KEY,
        "cond[trd_clcln_ymd::EQ]": day,
        "cond[gds_lclsf_cd::EQ]": APPLE_LCLSF_CD,
        "cond[gds_mclsf_cd::EQ]": APPLE_MCLSF_CD,
        "numOfRows": 1000,
        "returnType": "json",
    }
    if market_code:
        params["cond[whsl_mrkt_cd::EQ]"] = market_code

    rows = []
    try:
        for page in range(1, 4):  # 최대 3페이지(3,000건)까지만 사용
            params["pageNo"] = page
            body = _get_market_api(params)["response"]["body"]
            items = body.get("items") or {}
            page_rows = items.get("item") or []
            if isinstance(page_rows, dict):
                page_rows = [page_rows]
            rows.extend(page_rows)
            if page * 1000 >= int(body.get("totalCount", 0)):
                break
    except Exception:
        # 이 날짜 조회 실패: 캐시 없이 None 반환 → 시계열에서 하루만 빠진다
        return {"price": None, "market_nm": "", "variety": "사과"}

    # 품종 우선순위: 후지 거래가 있으면 후지만, 없으면 전체 사과
    variety_rows = [r for r in rows
                    if variety_keyword in (r.get("gds_sclsf_nm") or "")
                    or variety_keyword in (r.get("corp_gds_vrty_nm") or "")]
    used_rows = variety_rows if len(variety_rows) >= 5 else rows
    variety_nm = variety_keyword if used_rows is variety_rows else None

    total_won = 0.0
    total_kg = 0.0
    variety_count = {}
    market_nm = ""
    for r in used_rows:
        try:
            prc = float(r["scsbd_prc"])          # 포장 단위당 낙찰가(원)
            unit_kg = float(r["unit_qty"])       # 포장 단위중량(kg)
            qty = float(r["qty"])                # 수량
        except (KeyError, TypeError, ValueError):
            continue
        if prc <= 0 or unit_kg <= 0 or qty <= 0 or (r.get("unit_nm") or "kg") != "kg":
            continue
        total_won += prc * qty
        total_kg += unit_kg * qty
        market_nm = r.get("whsl_mrkt_nm") or market_nm
        nm = r.get("gds_sclsf_nm") or "사과"
        variety_count[nm] = variety_count.get(nm, 0) + 1

    price = round(total_won / total_kg) if total_kg > 0 else None
    if variety_nm is None:
        variety_nm = max(variety_count, key=variety_count.get) if variety_count else "사과"

    result = {"price": price, "market_nm": market_nm, "variety": variety_nm}
    _daily_cache[cache_key] = result
    return result


def build_price_series(end_date: str, market_code: str):
    """end_date까지 SERIES_DAYS일간의 kg당 사과 평균가 시계열을 만든다."""
    end = datetime.strptime(end_date, "%Y-%m-%d")
    days = [(end - timedelta(days=i)).strftime("%Y-%m-%d") for i in range(SERIES_DAYS - 1, -1, -1)]

    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda d: fetch_daily_apple_price(d, market_code), days))

    records = [(d, r["price"]) for d, r in zip(days, results) if r["price"]]
    market_nm = next((r["market_nm"] for r in reversed(results) if r["market_nm"]), "가락시장")
    variety_nm = next((r["variety"] for r in reversed(results) if r["price"]), "사과")
    df = pd.DataFrame(records, columns=["ds", "y"])
    df["ds"] = pd.to_datetime(df["ds"])
    return df, market_nm, variety_nm


def build_demo_series(date: str):
    """공공 API 장애 시 사용하는 데모 데이터 (기존 안전장치 유지)."""
    dates = pd.date_range(start='2026-05-01', end=date, freq='D')
    prices = []
    for i, d in enumerate(dates):
        base = 35000
        trend = (i * 120)
        if d >= datetime(2026, 7, 5):
            trend += (i - 60) * 300
        random_noise = np.random.normal(0, 500)
        prices.append(base + trend + random_noise)
    df = pd.DataFrame({'ds': dates, 'y': prices})
    df.iloc[-1, df.columns.get_loc('y')] = 43800
    return df


def load_series(end_date: str, market_code: str):
    """시장 코드 기준으로 시계열을 만들고, 데이터가 부족하면 전국 기준으로 재시도."""
    df, market_nm, variety_nm = build_price_series(end_date, market_code)
    if len(df) < 10 and market_code:
        df, _, variety_nm = build_price_series(end_date, "")
        market_nm = "전국 도매시장"
    return df, market_nm, variety_nm


def process_market_analysis(date: str, market_code: str, item_code: str, variety_code: str):
    market_nm, variety_nm = "가락시장", "사과"
    df = pd.DataFrame()
    if MARKET_API_KEY:
        try:
            df, market_nm, variety_nm = load_series(date, market_code)
            # 실시간 API는 최근 약 한 달치만 보관하므로, 오래된 날짜 요청이면 최신 데이터로 대체
            today = datetime.now().strftime("%Y-%m-%d")
            if len(df) < 10 and date != today:
                df, market_nm, variety_nm = load_series(today, market_code)
        except Exception:
            df = pd.DataFrame()
    is_real_data = len(df) >= 10
    if not is_real_data:
        df = build_demo_series(date)

    past_7_days = df.tail(7)
    price_today = int(past_7_days.iloc[-1]['y'])
    price_7_days_ago = int(past_7_days.iloc[0]['y'])
    price_diff = price_today - price_7_days_ago
    price_prev = int(df.iloc[-2]['y']) if len(df) >= 2 else price_today

    past_trend_summary = f"지난 7일간 사과 도매가는 kg당 약 {abs(price_diff):,}원 {'상승' if price_diff > 0 else '하락'}하여 현재 {price_today:,}원을 기록했습니다."

    model = Prophet(yearly_seasonality=False, weekly_seasonality=True, daily_seasonality=False)
    model.fit(df)
    future_dates = model.make_future_dataframe(periods=7, freq='D')
    forecast = model.predict(future_dates)
    future_forecast = forecast.tail(7)

    best_row = future_forecast.loc[future_forecast['yhat'].idxmax()]
    best_price = int(best_row['yhat'])

    if price_today >= best_price:
        market_pressure = "현재 가격은 단기 고점일 가능성이 매우 높으며, 향후 출하량 재개 시 가격 하락 압력이 예상됩니다. 따라서 오늘 출하가 최선의 선택입니다."
    else:
        market_pressure = f"향후 추가적인 상승 모멘텀이 존재하며, {best_row['ds'].strftime('%m월 %d일')}경 kg당 약 {best_price:,}원의 최고가 도달이 예상되어 출하 시기를 조정할 필요가 있습니다."

    # LLM 프롬프트 조립
    llm_prompt = f"""
    당신은 농업 데이터 분석 전문 AI 비서입니다. 아래 [시계열 분석 정량 데이터]를 바탕으로 정제된 'AI 시장 분석 리포트' 문장을 생성해 주세요.
    - 친근한 대화체가 아닌, 신뢰감을 주는 비즈니스 표준어 문장으로만 딱 한 단락(3문장 내외) 작성하세요.
    - 문장 안에는 과거 동향 내용과 시장 압력 진단 내용이 자연스럽게 녹아들어야 합니다.
    - 제공된 데이터에 없는 사실을 지어내지 마세요.

    [시계열 분석 정량 데이터]
    1. 분석 대상: {market_nm} 사과({variety_nm}) 경락가격 ({'실제 도매시장 경락 데이터' if is_real_data else '데모 데이터'})
    2. 과거 7일 동향 팩트: {past_trend_summary}
    3. 미래 가격 압력 진단: {market_pressure}
    """

    try:
        response = openai.ChatCompletion.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": llm_prompt}],
            max_tokens=300,
            temperature=0.5
        )
        report_text = response.choices[0].message['content'].strip()
    except Exception:
        report_text = f"{past_trend_summary} Prophet 시계열 예측 결과, {market_pressure}"

    chart_list = []
    for _, row in past_7_days.iterrows():
        chart_list.append({
            "date": row['ds'].strftime('%m/%d').lstrip("0"),  # '06/04' -> '6/4'
            "price": int(row['y'])
        })

    summary = {
        "today_price": price_today,
        "prev_price": price_prev,
        "weekly_avg": int(past_7_days['y'].mean()),
        "weekly_range": f"{past_7_days.iloc[0]['ds'].strftime('%m/%d').lstrip('0')}~{past_7_days.iloc[-1]['ds'].strftime('%m/%d').lstrip('0')}",
        "monthly_avg": int(df['y'].mean()),
        "monthly_range": f"최근 {len(df)}일 평균",
        "basis_date": past_7_days.iloc[-1]['ds'].strftime("%m월 %d일"),
    }
    return summary, chart_list, report_text, market_nm, variety_nm


@app.get("/api/price/dashboard")
def get_price_dashboard(
    date: str = Query(..., description="검색 대상 날짜 (YYYY-MM-DD)"),
    market_code: str = Query(..., description="도매시장 코드"),
    item_code: str = Query(..., description="품목 코드"),
    variety_code: str = Query(..., description="품종 코드")
):
    cache_key = (date, market_code)
    cached = _dashboard_cache.get(cache_key)
    if cached and time.time() - cached[0] < DASHBOARD_CACHE_TTL:
        return cached[1]

    summary, chart_data, ai_report, market_nm, variety_nm = process_market_analysis(
        date, market_code, item_code, variety_code)

    change_rate = 0.0
    if summary["prev_price"]:
        change_rate = round((summary["today_price"] - summary["prev_price"]) / summary["prev_price"] * 100, 1)

    payload = {
      "status": "success",
      "search_info": {
        "formatted_title": f"{date[:4]}년 {int(date[5:7])}월 {int(date[8:10])}일 · {market_nm} · 사과 · {variety_nm}",
        "date": f"{date[:4]}년 {int(date[5:7])}월 {int(date[8:10])}일",
        "market": market_nm,
        "item": "사과",
        "variety": variety_nm
      },
      "current_price_info": {
        "price_per_kg": summary["today_price"],
        "currency": "KRW",
        "change_rate": abs(change_rate),
        "change_direction": "UP" if change_rate >= 0 else "DOWN"
      },
      "price_summary": {
        "today_price": summary["today_price"],
        "today_basis_date": f"{summary['basis_date']} 기준",
        "weekly_average_price": summary["weekly_avg"],
        "weekly_basis_range": summary["weekly_range"],
        "monthly_average_price": summary["monthly_avg"],
        "monthly_basis_range": summary["monthly_range"]
      },
      "chart_data": chart_data,
      "ai_market_analysis": {
        "title": "최근 7일 가격 동향 요약",
        "report_text": ai_report
      }
    }
    _dashboard_cache[cache_key] = (time.time(), payload)
    return payload
