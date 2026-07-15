"""
전국 공영도매시장 실시간 경매정보 API에서 '사과' 낙찰가 데이터를 내려받는 스크립트.
- 대분류코드 06(과실류) + 중분류코드 01(사과) 필터
- 최근 약 2개월, 3일 간격으로 샘플링한 날짜들을 페이지네이션하여 수집
- 결과를 ml/data/apple_auction_raw.csv 로 저장
"""
import os
import time
from datetime import date, timedelta

import requests
import pandas as pd

URL = "https://apis.data.go.kr/B552845/katRealTime2/trades2"
KEY = os.getenv("MARKET_API_KEY", "")

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data", "apple_auction_raw.csv")

NUM_ROWS = 1000          # API 페이지당 최대 건수
END_DATE = date(2026, 7, 14)
START_DATE = date(2026, 6, 15)
STEP_DAYS = 1            # 가용 구간을 매일 촘촘히 수집


def build_dates():
    dates, d = [], END_DATE
    while d >= START_DATE:
        dates.append(d.isoformat())
        d -= timedelta(days=STEP_DAYS)
    return list(reversed(dates))


def fetch_page(ymd, page):
    params = {
        "serviceKey": KEY,
        "returnType": "JSON",
        "pageNo": page,
        "numOfRows": NUM_ROWS,
        "cond[trd_clcln_ymd::EQ]": ymd,
        "cond[gds_lclsf_cd::EQ]": "06",   # 과실류
        "cond[gds_mclsf_cd::EQ]": "01",   # 사과
    }
    for attempt in range(4):
        try:
            r = requests.get(URL, params=params, timeout=60)
            j = r.json()
            body = j["response"]["body"]
            items = body.get("items", {})
            rows = items.get("item", []) if isinstance(items, dict) else []
            if isinstance(rows, dict):   # 단건이면 dict로 옴
                rows = [rows]
            return rows, int(body.get("totalCount", 0))
        except Exception as e:
            print(f"    ! {ymd} p{page} 재시도 {attempt+1}/4: {e}")
            time.sleep(1.5 * (attempt + 1))
    return [], 0


def main():
    dates = build_dates()
    print(f"수집 대상 날짜 {len(dates)}개: {dates[0]} ~ {dates[-1]}")
    all_rows = []
    for ymd in dates:
        first, total = fetch_page(ymd, 1)
        if total == 0:
            print(f"{ymd}: 데이터 없음(휴장 추정) 건너뜀")
            continue
        rows = list(first)
        pages = (total + NUM_ROWS - 1) // NUM_ROWS
        for p in range(2, pages + 1):
            more, _ = fetch_page(ymd, p)
            rows.extend(more)
            time.sleep(0.15)
        print(f"{ymd}: totalCount={total}, 수집={len(rows)} ({pages}페이지)")
        all_rows.extend(rows)
        time.sleep(0.2)

    df = pd.DataFrame(all_rows)
    df.drop_duplicates(inplace=True)
    df.to_csv(OUT, index=False, encoding="utf-8-sig")
    print(f"\n저장 완료: {OUT}")
    print(f"총 행 수: {len(df):,} | 컬럼 수: {df.shape[1]}")
    print("컬럼:", list(df.columns))


if __name__ == "__main__":
    main()
