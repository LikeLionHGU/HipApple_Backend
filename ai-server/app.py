import base64
import os
import sys
import tempfile
import time
from dotenv import load_dotenv
load_dotenv()
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
from pathlib import Path

import requests
from fastapi import FastAPI, File, Form, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
import pandas as pd
import numpy as np
from prophet import Prophet
import openai  # OpenAI API 호출용
import json
import sqlite3
from pydantic import BaseModel, Field
from typing import List, Tuple

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

# quality_classifier/(이미지+Storage 필드 기반 상/중/하 RandomForest 분류기, 학습/실험용)
# model.joblib이 아직 없으면(실데이터로 학습 전) None으로 두고 엔드포인트에서 503을 반환한다.
QC_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "quality_classifier")
QC_MODEL_PATH = os.path.join(QC_DIR, "model.joblib")
sys.path.insert(0, QC_DIR)
try:
    from predict import predict as classify_apple_quality  # quality_classifier/predict.py
except ImportError:
    classify_apple_quality = None

# --- Swagger UI (API 문서) 명세를 위한 Pydantic 응답 모델 정의 ---
class SearchInfo(BaseModel):
    formatted_title: str = Field(..., description="화면 상단 제목 (예: 2026년 08월 07일 · 가락시장 · 사과 · 후지)")
    date: str = Field(..., description="조회 기준 날짜")
    market: str = Field(..., description="도매시장 이름")
    item: str = Field(..., description="품목 (사과)")
    variety: str = Field(..., description="품종 (후지, 홍로 등)")

class CurrentPriceInfo(BaseModel):
    price_per_kg: int = Field(..., description="당일 kg당 평균 시세")
    currency: str = Field("KRW", description="통화 (KRW)")
    change_rate: float = Field(..., description="전일 대비 변동률 (%)")
    change_direction: str = Field(..., description="변동 방향 (UP, DOWN, EQUAL)")

class PriceSummary(BaseModel):
    today_price: int = Field(..., description="오늘 시세")
    today_basis_date: str = Field(..., description="오늘 시세 기준일 텍스트")
    weekly_average_price: int = Field(..., description="최근 7일 평균 시세")
    weekly_basis_range: str = Field(..., description="최근 7일 날짜 범위")
    monthly_average_price: int = Field(..., description="최근 한 달(조회 기간) 평균 시세")
    monthly_basis_range: str = Field(..., description="최근 한 달 기준일 텍스트")

class ChartDataPoint(BaseModel):
    date: str = Field(..., description="차트용 날짜 (예: 8/7)")
    price: int = Field(..., description="해당 날짜의 평균 시세")

class HistoryReport(BaseModel):
    date: str = Field(..., description="과거 이슈 날짜 (예: 2026.02.20)")
    content: str = Field(..., description="해당 시점의 핵심 시장 이슈 1문장 요약")

class AiMarketAnalysis(BaseModel):
    title: str = Field(..., description="분석 리포트 제목")
    report_text: str = Field(..., description="최근 시세 동향 및 미래 예측 분석 텍스트")
    history_reports: List[HistoryReport] = Field(..., description="과거 시세 변동 흐름(타임라인) 분석 리스트")

class DashboardResponse(BaseModel):
    status: str = Field(..., description="응답 상태 (success 등)")
    search_info: SearchInfo
    current_price_info: CurrentPriceInfo
    price_summary: PriceSummary
    chart_data: List[ChartDataPoint]
    future_chart_data: List[ChartDataPoint]
    ai_market_analysis: AiMarketAnalysis


class QualityAnalysisResponse(BaseModel):
    grade: str = Field(..., description="AI 등급 판정 (특/상/중/하/판정불가)")
    ripeness: str = Field(..., description="숙성 정도에 대한 짧은 문장")
    colorDescription: str = Field(..., description="색상/착색 상태에 대한 짧은 문장")
    shipmentComment: str = Field(..., description="출하 시점에 대한 조언 1~2문장")
    confidence: str = Field(..., description="판정 신뢰도 (high/medium/low)")


class QualityClassifyResponse(BaseModel):
    label: str = Field(..., description="예측 등급 (상/중/하)")
    probabilities: dict = Field(..., description="클래스별 확률")
    topFeatures: List[Tuple[str, float]] = Field(..., description="판단에 크게 기여한 특징 상위 5개 [이름, 중요도]")
# ------------------------------------------------------------------

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

# Prediction 이력 저장용 DB
BASE_DIR = Path(__file__).resolve().parent
DB_FILE = BASE_DIR / "prediction_history.db"

# 프론트 품종 선택값
VARIETY_OPTIONS = {
    "fuji": "후지",
    "01": "후지",
    "hongro": "홍로",
    "02": "홍로",
    "gala": "갈라",
    "03": "갈라",
    "arisoo": "아리수",
    "04": "아리수",
    "all": ""
}

def get_variety_keyword(variety_code: str) -> str:
    return VARIETY_OPTIONS.get(variety_code, "후지")

def init_prediction_db():
    conn = sqlite3.connect(DB_FILE)
    try:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS prediction_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                market_code TEXT NOT NULL,
                variety_code TEXT NOT NULL,
                predicted_price INTEGER NOT NULL,
                actual_price INTEGER NOT NULL,
                change_rate REAL NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(date, market_code, variety_code)
            )
        """)
        conn.commit()
    finally:
        conn.close()

init_prediction_db()


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
    cache_key = (day, market_code, variety_keyword)
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
        # 공공데이터포털 일부 품목/시장의 경우 scsbd_prc가 이미 1kg당 단가로 내려오거나,
        # 혹은 박스당 단가로 내려오는 혼선이 있습니다.
        # 500원대(너무 낮은 가격)로 계산되는 현상을 방지하기 위해
        # 만약 박스당 가격(prc)을 unit_kg로 나눈 값이 1000원 미만이라면, prc 자체가 1kg당 가격일 확률이 매우 높으므로 보정합니다.

        price_per_kg_for_this_row = prc / unit_kg
        if price_per_kg_for_this_row < 1000 and prc > 1000:
            # prc가 이미 1kg당 가격인 경우
            total_won += prc * (unit_kg * qty)
        else:
            # prc가 박스당 가격인 경우
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


def build_price_series(end_date: str, market_code: str, variety_keyword: str = "후지"):
    """end_date까지 SERIES_DAYS일간의 kg당 사과 평균가 시계열을 만든다."""
    end = datetime.strptime(end_date, "%Y-%m-%d")
    days = [(end - timedelta(days=i)).strftime("%Y-%m-%d") for i in range(SERIES_DAYS - 1, -1, -1)]

    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda d: fetch_daily_apple_price(d, market_code, variety_keyword), days))

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
        base = 4500
        trend = (i * 15)
        if d >= datetime(2026, 7, 5):
            trend += (i - 60) * 40
        random_noise = np.random.normal(0, 150)
        prices.append(base + trend + random_noise)
    df = pd.DataFrame({'ds': dates, 'y': prices})
    df.iloc[-1, df.columns.get_loc('y')] = 5300
    return df


def load_series(end_date: str, market_code: str, variety_keyword: str = "후지"):
    """시장 코드 기준으로 시계열을 만들고, 데이터가 부족하면 전국 기준으로 재시도."""
    df, market_nm, variety_nm = build_price_series(end_date, market_code, variety_keyword)
    if len(df) < 10 and market_code:
        df, _, variety_nm = build_price_series(end_date, "", variety_keyword)
        market_nm = "전국 도매시장"
    return df, market_nm, variety_nm


def process_market_analysis(date: str, market_code: str, item_code: str, variety_code: str):
    market_nm, variety_nm = "가락시장", "사과"
    variety_keyword = get_variety_keyword(variety_code)
    df = pd.DataFrame()
    if MARKET_API_KEY:
        try:
            df, market_nm, variety_nm = load_series(date, market_code, variety_keyword)
            # 실시간 API는 최근 약 한 달치만 보관하므로, 오래된 날짜 요청이면 최신 데이터로 대체
            today = datetime.now().strftime("%Y-%m-%d")
            if len(df) < 10 and date != today:
                df, market_nm, variety_nm = load_series(today, market_code, variety_keyword)
        except Exception:
            df = pd.DataFrame()
    is_real_data = len(df) >= 10
    if not is_real_data:
        df = build_demo_series(date)

    # Prophet 학습 전 데이터 정리
    df = df.copy()
    df["ds"] = pd.to_datetime(df["ds"], errors="coerce")
    df["y"] = pd.to_numeric(df["y"], errors="coerce")
    df = df.dropna(subset=["ds", "y"])
    df = df.drop_duplicates(subset=["ds"])
    df = df.sort_values("ds").reset_index(drop=True)

    if len(df) < 10:
        df = build_demo_series(date)

    past_7_days = df.tail(7)
    price_today = int(past_7_days.iloc[-1]['y'])
    price_7_days_ago = int(past_7_days.iloc[0]['y'])
    price_diff = price_today - price_7_days_ago
    price_prev = int(df.iloc[-2]['y']) if len(df) >= 2 else price_today

    past_trend_summary = f"지난 7일간 사과 도매가는 kg당 약 {abs(price_diff):,}원 {'상승' if price_diff > 0 else '하락'}하여 현재 {price_today:,}원을 기록했습니다."

    # --------------------------------------------------------
    # Prediction용: 기준일 당일 가격 예측
    # --------------------------------------------------------
    # 기준일의 실제 가격은 학습에서 제외한다.
    # 즉, 7/15에 분석하면 7/14까지의 데이터로 7/15를 예측한다.
    train_df = df.iloc[:-1][["ds", "y"]].copy()

    if len(train_df) < 9:
        demo_df = build_demo_series(date)
        train_df = demo_df.iloc[:-1][["ds", "y"]].copy()

    target_date = df.iloc[-1]["ds"]

    today_model = Prophet(
        yearly_seasonality=False,
        weekly_seasonality=True,
        daily_seasonality=False
    )
    today_model.fit(train_df)

    today_forecast = today_model.predict(
        pd.DataFrame({"ds": [target_date]})
    )

    predicted_today = max(
        0,
        int(round(today_forecast.iloc[0]["yhat"]))
    )

    # --------------------------------------------------------
    # Dashboard용: 기존처럼 향후 7일 중 최적 출하일 계산
    # --------------------------------------------------------
    model = Prophet(
        yearly_seasonality=False,
        weekly_seasonality=True,
        daily_seasonality=False
    )
    model.fit(df)

    future_dates = model.make_future_dataframe(
        periods=7,
        freq="D"
    )
    forecast = model.predict(future_dates)
    future_forecast = forecast.tail(7)

    best_row = future_forecast.loc[
        future_forecast["yhat"].idxmax()
    ]
    best_price = int(best_row["yhat"])

    if price_today >= best_price:
        market_pressure = "현재 가격은 단기 고점일 가능성이 매우 높으며, 향후 출하량 재개 시 가격 하락 압력이 예상됩니다. 따라서 오늘 출하가 최선의 선택입니다."
    else:
        market_pressure = f"향후 추가적인 상승 모멘텀이 존재하며, {best_row['ds'].strftime('%m월 %d일')}경 kg당 약 {best_price:,}원의 최고가 도달이 예상되어 출하 시기를 조정할 필요가 있습니다."

    # LLM 프롬프트 조립 (JSON 형식으로 현재 리포트와 과거 타임라인 리스트를 동시에 요구)
    llm_prompt = f"""
    당신은 농업 데이터 분석 전문 AI 비서입니다. 아래 [시계열 분석 정량 데이터]를 바탕으로 'AI 시장 분석 리포트'를 오직 JSON 형식으로만 생성해 주세요.
    반드시 아래 JSON 구조를 정확히 지켜주세요. 다른 인사말이나 설명은 절대 포함하지 마세요.

    {{
      "report_text": "현재 시점에 대한 3문장 내외의 신뢰감 있는 비즈니스 표준어 시장 분석 요약 (과거 동향과 미래 가격 압력 진단 포함)",
      "history_reports": [
        {{ "date": "YYYY.MM.DD", "content": "과거 해당 시점의 핵심 시장 이슈 1문장 요약 (예: 설 명절 이후 출하량 감소로...)" }}
      ]
    }}

    history_reports 배열에는 검색된 현재 날짜({date})를 기준으로 과거 6개월 동안의 핵심 시장 흐름을 가상으로 5~6개 만들어주세요. 제공된 정량 데이터의 맥락(가격 상승/하락)에 맞게 자연스럽게 생성하세요.

    [시계열 분석 정량 데이터]
    1. 분석 대상: {market_nm} 사과({variety_nm}) 경락가격 ({'실제 도매시장 경락 데이터' if is_real_data else '데모 데이터'})
    2. 과거 7일 동향 팩트: {past_trend_summary}
    3. 미래 가격 압력 진단: {market_pressure}
    """

    try:
        # OpenAI API 호출 (최신 모델 gpt-4o-mini 사용)
        response = openai.ChatCompletion.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": llm_prompt}],
            max_tokens=600,
            temperature=0.5
        )
        content = response.choices[0].message['content'].strip()

        # LLM이 간혹 JSON 블록(```json) 안에 응답을 감싸서 보내는 경우를 대비해 텍스트 파싱
        if content.startswith("```json"):
            content = content[7:]
        elif content.startswith("```"):
            content = content[3:]
        if content.endswith("```"):
            content = content[:-3]

        parsed = json.loads(content.strip())
        report_text = parsed.get("report_text", f"{past_trend_summary} Prophet 시계열 예측 결과, {market_pressure}")
        history_reports = parsed.get("history_reports", [])
    except Exception as e:
        # API 키가 없거나 호출 중 에러 발생 시, 프론트엔드가 깨지지 않도록 기본(Mock) 데이터 제공
        print(f"LLM Error: {e}")
        report_text = f"{past_trend_summary} Prophet 시계열 예측 결과, {market_pressure}"
        history_reports = [
            {"date": "2026.02.20", "content": "설 명절 이후 출하량 감소로 시장 가격이 일시적으로 하락했습니다."},
            {"date": "2026.03.01", "content": "저장 물량 감소와 도매시장 거래량 축소로 가격이 상승하기 시작했습니다."},
            {"date": "2026.04.20", "content": "기온 상승으로 인한 소비 증가가 가격 상승을 견인했습니다."},
            {"date": "2026.06.01", "content": "장마 예보로 출하 지연 우려가 반영되어 가격이 상승했습니다."},
            {"date": "2026.07.10", "content": "명절 대비 사전 물량 확보 수요 증가로 가격이 급등했습니다."}
        ]

    chart_list = []
    for _, row in past_7_days.iterrows():
        chart_list.append({
            "date": row['ds'].strftime('%m/%d').lstrip("0"),  # '06/04' -> '6/4'
            "price": int(row['y'])
        })

    future_chart_list = []
    for _, row in future_forecast.iterrows():
        future_chart_list.append({
            "date": row['ds'].strftime('%Y-%m-%d'),
            "price": int(row['yhat'])
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
    return summary, chart_list, future_chart_list, report_text, history_reports, market_nm, variety_nm, predicted_today


@app.get("/api/price/dashboard", response_model=DashboardResponse)
def get_price_dashboard(
    date: str = Query(..., description="검색 대상 날짜 (YYYY-MM-DD)"),
    market_code: str = Query(..., description="도매시장 코드"),
    item_code: str = Query(..., description="품목 코드"),
    variety_code: str = Query(..., description="품종 코드")
):
    cache_key = (date, market_code, item_code, variety_code)
    cached = _dashboard_cache.get(cache_key)
    if cached and time.time() - cached[0] < DASHBOARD_CACHE_TTL:
        return cached[1]

    summary, chart_data, future_chart_data, ai_report, history_reports, market_nm, variety_nm, _ = process_market_analysis(
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
      "future_chart_data": future_chart_data,
      "ai_market_analysis": {
        "title": "최근 7일 가격 동향 요약",
        "report_text": ai_report,
        "history_reports": history_reports
      }
    }
    _dashboard_cache[cache_key] = (time.time(), payload)
    return payload


@app.get("/api/price/future-comments")
def get_future_comments(
    date: str = Query(..., description="검색 대상 날짜 (YYYY-MM-DD)"),
    market_code: str = Query(..., description="도매시장 코드"),
    item_code: str = Query(..., description="품목 코드"),
    variety_code: str = Query(..., description="품종 코드")
):
    market_nm, variety_nm = "가락시장", "사과"
    df = pd.DataFrame()
    if MARKET_API_KEY:
        try:
            df, market_nm, variety_nm = load_series(date, market_code, get_variety_keyword(variety_code))
            today = datetime.now().strftime("%Y-%m-%d")
            if len(df) < 10 and date != today:
                df, market_nm, variety_nm = load_series(today, market_code)
        except Exception:
            df = pd.DataFrame()

    if len(df) < 10:
        df = build_demo_series(date)

    model = Prophet(yearly_seasonality=False, weekly_seasonality=True, daily_seasonality=False)
    model.fit(df)
    future_dates = model.make_future_dataframe(periods=7, freq='D')
    forecast = model.predict(future_dates)
    future_forecast = forecast.tail(7)

    future_prices_str = ", ".join([f"{row['ds'].strftime('%m/%d')}: {int(row['yhat'])}원" for _, row in future_forecast.iterrows()])

    llm_prompt = f"""
    당신은 농산물 가격 예측을 설명해주는 전문 AI 비서입니다.
    아래는 Prophet 시계열 모델이 예측한 미래 7일 치 사과({variety_nm}, {market_nm} 기준)의 예상 도매가격(kg당)입니다.

    [미래 7일 예측 가격]
    {future_prices_str}

    위 가격 변동 흐름(상승/하락/유지)을 바탕으로, 하루하루의 트렌드를 분석해서 총 7개의 예측 문장을 만들어주세요.
    반드시 아래 JSON 형식으로만 응답해야 하며, 다른 인사말이나 설명은 절대 포함하지 마세요.

    {{
      "future_reports": [
        {{ "date": "YYYY.MM.DD", "content": "해당 일자의 가격 변동이나 시장 상황에 대한 분석 1문장" }}
      ]
    }}

    조건:
    - 날짜(date)는 제공된 미래 7일의 실제 날짜를 사용하세요 (YYYY.MM.DD 형식).
    - 내용(content)은 가격이 왜 오를지, 내릴지 그럴듯한 농업 시장 논리(예: 수요 증가, 공급 부족 등)를 곁들여 작성하세요.
    - 정확히 7개의 객체가 배열 안에 있어야 합니다.
    """

    try:
        response = openai.ChatCompletion.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": llm_prompt}],
            max_tokens=600,
            temperature=0.6
        )
        content = response.choices[0].message['content'].strip()

        if content.startswith("```json"):
            content = content[7:]
        elif content.startswith("```"):
            content = content[3:]
        if content.endswith("```"):
            content = content[:-3]

        parsed = json.loads(content.strip())
        future_reports = parsed.get("future_reports", [])
    except Exception as e:
        print(f"LLM Error in future-comments: {e}")
        # Fallback
        future_reports = []
        for i, (_, row) in enumerate(future_forecast.iterrows()):
            d_str = row['ds'].strftime('%Y.%m.%d')
            if i % 2 == 0:
                future_reports.append({"date": d_str, "content": "기온 상승으로 인한 소비 증가가 가격에 일부 반영될 것으로 예측됩니다."})
            else:
                future_reports.append({"date": d_str, "content": "사전 물량 확보 수요 증가로 가격이 일시적인 강세를 보일 수 있습니다."})

    return {"future_reports": future_reports}


# --- 사진 기반 AI 사과 품질 판정 --------------------------------------------

ALLOWED_PHOTO_TYPES = {"image/jpeg", "image/png", "image/webp"}
MAX_PHOTO_BYTES = 8 * 1024 * 1024  # 8MB

QUALITY_ANALYSIS_FALLBACK = {
    "grade": "판정불가",
    "ripeness": "분석 실패",
    "colorDescription": "-",
    "shipmentComment": "일시적으로 AI 분석에 실패했습니다. 잠시 후 다시 시도해주세요.",
    "confidence": "low",
}

QUALITY_ANALYSIS_PROMPT = """당신은 사과 품질을 사진만으로 육안 판정하는 AI 검수 보조입니다.
제공된 사과 사진 한 장을 보고, 다른 정보 없이 오직 사진에서 보이는 것만으로 판단하세요.
반드시 아래 JSON 형식으로만 답하세요. 다른 설명, 인사말, 코드블록 표시는 절대 포함하지 마세요.

{
  "grade": "특|상|중|하 중 하나",
  "ripeness": "사진에서 보이는 숙성 정도에 대한 짧은 한 문장",
  "color_description": "색상/착색 상태에 대한 짧은 한 문장",
  "shipment_comment": "사진 속 사과 상태를 근거로 한 출하 시점 조언 1~2문장",
  "confidence": "high|medium|low"
}

판정 기준:
- grade는 색상 균일도, 표면 흠집·멍·반점 여부, 전체적인 신선도로 종합 판단하세요.
- 품종 정보가 주어지지 않았으므로 특정 품종 기준(예: 후지 대비 착색도)을 가정하지 말고,
  사진에 보이는 사과 자체의 상태만으로 판단하세요.
- 사진이 흐리거나, 조명이 나쁘거나, 사과가 프레임에 온전히 담기지 않아 판정이 어려우면
  confidence를 "low"로 낮추고 grade는 보수적으로("중" 이하로) 답하세요.
- 사과가 아닌 사진이거나 사과를 식별할 수 없으면 grade를 "판정불가"로 하고,
  ripeness/color_description/shipment_comment에는 그 이유를 간단히 적으세요.
"""


@app.post("/api/quality/analyze", response_model=QualityAnalysisResponse)
async def analyze_apple_quality(
    photo: UploadFile = File(None),
    brix: float = Form(None),
    hardness: float = Form(None),
    storage_method: str = Form(None),
    storage_days: int = Form(None)
):
    if not openai.api_key:
        return QUALITY_ANALYSIS_FALLBACK

    has_photo = photo is not None and photo.filename != ""
    data_url = None

    if has_photo:
        if photo.content_type not in ALLOWED_PHOTO_TYPES:
            raise HTTPException(status_code=400, detail="jpeg/png/webp 이미지만 지원합니다.")
        image_bytes = await photo.read()
        if not image_bytes:
            raise HTTPException(status_code=400, detail="빈 파일입니다.")
        if len(image_bytes) > MAX_PHOTO_BYTES:
            raise HTTPException(status_code=400, detail="이미지 용량은 8MB를 초과할 수 없습니다.")
        data_url = f"data:{photo.content_type};base64,{base64.b64encode(image_bytes).decode('utf-8')}"

    storage_info = ""
    if brix is not None:
        storage_info += f"당도: {brix} Brix, "
    if hardness is not None:
        storage_info += f"경도: {hardness} kgf, "
    if storage_method is not None:
        storage_info += f"저장방식: {storage_method}, "
    if storage_days is not None:
        storage_info += f"저장일수: {storage_days}일"

    if has_photo:
        prompt_text = QUALITY_ANALYSIS_PROMPT
        if storage_info:
            prompt_text += f"\n\n참고 저장고 데이터: {storage_info}\n이 데이터를 고려하여 종합적으로 판정하세요."

        messages = [{
            "role": "user",
            "content": [
                {"type": "text", "text": prompt_text},
                {"type": "image_url", "image_url": {"url": data_url}},
            ],
        }]
    else:
        prompt_text = f"""당신은 사과 저장 데이터(당도, 경도 등)만으로 품질을 추정하는 AI 검수 보조입니다.
사진이 없으므로 아래 데이터에 기반해 보수적으로 판정하세요.
반드시 아래 JSON 형식으로만 답하세요. 다른 설명, 인사말, 코드블록 표시는 절대 포함하지 마세요.

{{
  "grade": "특|상|중|하|판정불가 중 하나",
  "ripeness": "데이터 상 추정되는 숙성 정도에 대한 짧은 한 문장",
  "color_description": "사진이 없으므로 '확인 불가' 등으로 짧게 표기",
  "shipment_comment": "저장 일수와 당도, 경도를 고려한 출하 시점 조언 1~2문장",
  "confidence": "high|medium|low"
}}

제공된 데이터:
{storage_info if storage_info else "데이터 없음"}

판정 기준:
- 사진이 없으므로 confidence는 무조건 "low" 또는 "medium"으로 설정하세요.
- 데이터가 전혀 없다면 grade를 "판정불가"로 하세요."""
        messages = [{"role": "user", "content": prompt_text}]

    try:
        response = openai.ChatCompletion.create(
            model="gpt-4o-mini",
            messages=messages,
            max_tokens=400,
            temperature=0.2,
        )
        content = response.choices[0].message["content"].strip()

        if content.startswith("```json"):
            content = content[7:]
        elif content.startswith("```"):
            content = content[3:]
        if content.endswith("```"):
            content = content[:-3]

        parsed = json.loads(content.strip())
        return {
            "grade": parsed.get("grade", QUALITY_ANALYSIS_FALLBACK["grade"]),
            "ripeness": parsed.get("ripeness", QUALITY_ANALYSIS_FALLBACK["ripeness"]),
            "colorDescription": parsed.get("color_description", QUALITY_ANALYSIS_FALLBACK["colorDescription"]),
            "shipmentComment": parsed.get("shipment_comment", QUALITY_ANALYSIS_FALLBACK["shipmentComment"]),
            "confidence": parsed.get("confidence", QUALITY_ANALYSIS_FALLBACK["confidence"]),
        }
    except Exception as e:
        print(f"Quality Analysis LLM Error: {e}")
        return QUALITY_ANALYSIS_FALLBACK


@app.post("/api/quality/classify", response_model=QualityClassifyResponse)
async def classify_apple_quality_endpoint(
    photo: UploadFile = File(...),
    brix: float = Form(...),
    hardness: float = Form(...),
    storage_method: str = Form(...),
    storage_days: float = Form(...),
    amount: float = Form(...),
):
    """quality_classifier/(이미지 특징 + Storage 필드 -> RandomForest) 기반 실험적 상/중/하 분류.
    gpt-4o-mini를 쓰는 /api/quality/analyze와 달리 로컬에서 학습한 model.joblib으로 판정한다."""
    if photo.content_type not in ALLOWED_PHOTO_TYPES:
        raise HTTPException(status_code=400, detail="jpeg/png/webp 이미지만 지원합니다.")

    image_bytes = await photo.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="빈 파일입니다.")
    if len(image_bytes) > MAX_PHOTO_BYTES:
        raise HTTPException(status_code=400, detail="이미지 용량은 8MB를 초과할 수 없습니다.")

    if classify_apple_quality is None or not os.path.exists(QC_MODEL_PATH):
        raise HTTPException(status_code=503, detail="분류 모델이 아직 준비되지 않았습니다 (model.joblib 없음).")

    suffix = Path(photo.filename or "photo.jpg").suffix or ".jpg"
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        tmp.write(image_bytes)
        tmp_path = tmp.name

    try:
        result = classify_apple_quality(
            tmp_path, brix, hardness, storage_method, storage_days, amount,
            model_path=QC_MODEL_PATH,
        )
    finally:
        os.remove(tmp_path)

    return {
        "label": result["label"],
        "probabilities": result["probabilities"],
        "topFeatures": result["top_features"],
    }
class PricePredictionItem(BaseModel):
    date: str = Field(..., description="날짜 (YYYY-MM-DD)")
    predictedPrice: int = Field(..., description="AI 예측가 (원)")
    actualPrice: int = Field(..., description="실제/현재가 (원)")
    changeRate: float = Field(..., description="변동률 (%)")



# ============================================================
# Prediction 이력
# ============================================================

class PricePredictionItem(BaseModel):
    date: str = Field(..., description="AI 분석을 실행한 날짜")
    predictedPrice: int = Field(..., description="AI가 해당 날짜에 예측한 가격")
    actualPrice: int = Field(..., description="해당 날짜의 실제 가격")
    changeRate: float = Field(..., description="예측값과 실제값의 오차율 (%)")


@app.post(
    "/api/price-predictions/analyze",
    response_model=PricePredictionItem,
    summary="AI 분석 버튼 클릭 시 당일 예측값 저장"
)
def run_price_prediction_analysis(
    date: str = Query(..., description="AI 분석을 실행한 날짜 (YYYY-MM-DD)"),
    market_code: str = Query(default="110001", description="도매시장 코드"),
    item_code: str = Query(default="0601", description="품목 코드"),
    variety_code: str = Query(default="fuji", description="품종 코드")
):
    """
    Dashboard에서 AI 분석 버튼을 눌렀을 때만 호출한다.

    예:
    7/15에 AI 분석 클릭
    -> 7/14까지의 가격으로 7/15 가격 예측
    -> 실제 7/15 가격과 비교
    -> Prediction DB에 7/15 한 줄 저장
    """

    try:
        (
            summary,
            chart_data,
            future_chart_data,
            ai_report,
            history_reports,
            market_nm,
            variety_nm,
            predicted_today
        ) = process_market_analysis(
            date,
            market_code,
            item_code,
            variety_code
        )

        actual_price = int(summary["today_price"])
        predicted_price = int(predicted_today)

        error_rate = (
            round(
                abs(predicted_price - actual_price)
                / actual_price
                * 100,
                1
            )
            if actual_price
            else 0.0
        )

        conn = sqlite3.connect(DB_FILE, timeout=10)
        try:
            conn.execute("PRAGMA busy_timeout=10000")
            conn.execute(
                """
                INSERT INTO prediction_history (
                    date,
                    market_code,
                    variety_code,
                    predicted_price,
                    actual_price,
                    change_rate
                )
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(date, market_code, variety_code)
                DO UPDATE SET
                    predicted_price = excluded.predicted_price,
                    actual_price = excluded.actual_price,
                    change_rate = excluded.change_rate,
                    created_at = CURRENT_TIMESTAMP
                """,
                (
                    date,
                    market_code,
                    variety_code,
                    predicted_price,
                    actual_price,
                    error_rate
                )
            )
            conn.commit()
        finally:
            conn.close()

        return {
            "date": date,
            "predictedPrice": predicted_price,
            "actualPrice": actual_price,
            "changeRate": error_rate
        }

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"AI 분석 결과 저장 중 오류 발생: {str(e)}"
        )


@app.get(
    "/api/price-predictions",
    response_model=List[PricePredictionItem],
    summary="누적된 AI 가격 예측 이력 조회"
)
def get_price_predictions(
    market_code: str = Query(default="110001"),
    variety_code: str = Query(default="fuji")
):
    """
    Dashboard에서 실제로 AI 분석을 눌렀던 날짜만 조회한다.
    """

    try:
        conn = sqlite3.connect(DB_FILE, timeout=10)
        conn.row_factory = sqlite3.Row

        try:
            rows = conn.execute(
                """
                SELECT
                    date,
                    predicted_price,
                    actual_price,
                    change_rate
                FROM prediction_history
                WHERE market_code = ?
                  AND variety_code = ?
                ORDER BY date ASC
                """,
                (market_code, variety_code)
            ).fetchall()
        finally:
            conn.close()

        return [
            {
                "date": row["date"],
                "predictedPrice": int(row["predicted_price"]),
                "actualPrice": int(row["actual_price"]),
                "changeRate": float(row["change_rate"])
            }
            for row in rows
        ]

    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"예측 기록 조회 중 오류 발생: {str(e)}"
        )