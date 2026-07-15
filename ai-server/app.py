import os

from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
import pandas as pd
import numpy as np
from datetime import datetime
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


def process_market_analysis(date: str, market_code: str, item_code: str, variety_code: str):
    # (안전장치용 데모 데이터 생성 파트 - 실제 API 구축 시 params에 위 인자들을 매칭)
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

    past_7_days = df.tail(7)
    price_today = int(past_7_days.iloc[-1]['y'])
    price_7_days_ago = int(past_7_days.iloc[0]['y'])
    price_diff = price_today - price_7_days_ago

    past_trend_summary = f"지난 7일간 사과 도매가는 약 {abs(price_diff):,}원 {'상승' if price_diff > 0 else '하락'}하여 현재 {price_today:,}원을 기록했습니다."

    model = Prophet(yearly_seasonality=True, weekly_seasonality=True, daily_seasonality=False)
    model.fit(df)
    future_dates = model.make_future_dataframe(periods=7, freq='D')
    forecast = model.predict(future_dates)
    future_forecast = forecast.tail(7)

    best_row = future_forecast.loc[future_forecast['yhat'].idxmax()]
    best_price = int(best_row['yhat'])

    if price_today >= best_price:
        market_pressure = "현재 가격은 단기 고점일 가능성이 매우 높으며, 향후 출하량 재개 시 가격 하락 압력이 예상됩니다. 따라서 오늘 출하가 최선의 선택입니다."
    else:
        market_pressure = f"향후 추가적인 상승 모멘텀이 존재하며, 최고가 도달이 예상되어 출하 시기를 조정할 필요가 있습니다."

    # 3. LLM 프롬프트 조립
    llm_prompt = f"""
    당신은 농업 데이터 분석 전문 AI 비서입니다. 아래 [시계열 분석 정량 데이터]를 바탕으로 정제된 'AI 시장 분석 리포트' 문장을 생성해 주세요.
    - 친근한 대화체가 아닌, 신뢰감을 주는 비즈니스 표준어 문장으로만 딱 한 단락(3문장 내외) 작성하세요.
    - 문장 안에는 과거 동향 내용과 시장 압력 진단 내용이 자연스럽게 녹아들어야 합니다.

    [시계열 분석 정량 데이터]
    1. 과거 7일 동향 팩트: {past_trend_summary}
    2. 외부 환경 컨텍스트: 장마철 출하량 감소 현상 맞물림 / 주말부터 경북 지역 출하 재개 예정
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
    except Exception as e:
        report_text = f"{past_trend_summary} 이는 장마철 출하량 감소와 맞닿아 있으나, 이번 주말부터 경북 지역 출하가 재개되면서 가격 하락 압력이 예상됩니다. {market_pressure}"

    chart_list = []
    for _, row in past_7_days.iterrows():
        chart_list.append({
            "date": row['ds'].strftime('%m/%d').lstrip("0"),  # '06/04' -> '6/4'
            "price": int(row['y'])
        })

    return price_today, chart_list, report_text


@app.get("/api/price/dashboard")
def get_price_dashboard(
    date: str = Query(..., description="검색 대상 날짜 (YYYY-MM-DD)"),
    market_code: str = Query(..., description="도매시장 코드"),
    item_code: str = Query(..., description="품목 코드"),
    variety_code: str = Query(..., description="품종 코드")
):
    # 로직 실행
    price_today, chart_data, ai_report = process_market_analysis(date, market_code, item_code, variety_code)

    return {
      "status": "success",
      "search_info": {
        "formatted_title": f"{date[:4]}년 {int(date[5:7])}월 {int(date[8:10])}일 · 가락시장 · 사과 · 후지",
        "date": f"{date[:4]}년 {int(date[5:7])}월 {int(date[8:10])}일",
        "market": "가락시장",
        "item": "사과",
        "variety": "후지"
    },
      "current_price_info": {
        "price_per_kg": 2310,
        "currency": "KRW",
        "change_rate": 3.5,
        "change_direction": "UP"
      },
      "price_summary": {
        "today_price": 2310,
        "today_basis_date": f"{date[5:7]}월 {date[8:10]}일 기준",
        "weekly_average_price": 2311,
        "weekly_basis_range": "6/4~6/10",
        "monthly_average_price": 2405,
        "monthly_basis_range": "6월 전체 평균"
      },
      "chart_data": chart_data,
      "ai_market_analysis": {
        "title": "최근 7일 가격 동향 요약",
        "report_text": ai_report
      }
    }
