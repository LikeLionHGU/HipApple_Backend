"""
사과 '일별 대표시세(vwap, 원/kg)' 예측 v2 — 히스토리+계절성 확장 모델용.

models/final_daily_model2.joblib 을 로드해, (도매시장 x 품종)의 가장 최근 관측 행
피처로 다음 대표시세를 예측한다. 피처(t_index 포함)는 반드시 학습과 동일한
common_hist.build_daily_ext() 로 생성한다 — 히스토리 유무에 따라 t_index 기준이
달라지므로 직접 만들면 안 됨.

등급 비율 테이블(data/grade_ratio.csv)이 있으면 등급별(특/상/중/하) 예상가도 함께 출력.

사용:
  from predict_daily2 import predict_next
  krw_per_kg = predict_next("서울가락", "후지")

  CLI:  python3 predict_daily2.py 서울가락 후지
"""
import os
import sys

BASE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, BASE)

import pandas as pd
import joblib

import common_hist as ch
MODEL_PATH = os.path.join(BASE, "models", "final_daily_model2.joblib")

_bundle = None
_daily = None


def _load():
    global _bundle, _daily
    if _bundle is None:
        if not os.path.exists(MODEL_PATH):
            raise FileNotFoundError(
                f"모델 파일이 없습니다: {MODEL_PATH}\n먼저 `python3 train_daily2.py` 를 실행하세요."
            )
        _bundle = joblib.load(MODEL_PATH)
    if _daily is None:
        # 학습과 동일한 피처 생성 경로(t_index 기준 일치)
        _daily = ch.build_daily_ext(include_history=True)
    return _bundle, _daily


def _match_group(daily, whsl_mrkt_nm, gds_sclsf_nm):
    m = daily["whsl_mrkt_nm"].astype(str)
    g = daily["gds_sclsf_nm"].astype(str)
    exact = daily[(m == whsl_mrkt_nm) & (g == gds_sclsf_nm)]
    if len(exact):
        return exact
    return daily[m.str.contains(whsl_mrkt_nm, na=False)
                 & g.str.contains(gds_sclsf_nm, na=False)]


def _grade_prices(pred, gds_sclsf_nm, month):
    """등급 비율 테이블이 있으면 등급별 예상가 dict 반환, 없으면 None."""
    if not os.path.exists(ch.GRADE_RATIO_CSV):
        return None
    tab = pd.read_csv(ch.GRADE_RATIO_CSV)
    sub = tab[(tab["gds_sclsf_nm"] == gds_sclsf_nm) & (tab["month"] == month)]
    if len(sub) == 0:
        return None
    return {row["grade"]: round(pred * row["ratio"], 1) for _, row in sub.iterrows()}


def predict_next(whsl_mrkt_nm, gds_sclsf_nm):
    """(도매시장, 품종)의 최근 행 피처로 다음 대표시세(원/kg)를 예측."""
    bundle, daily = _load()
    model, medians, features = bundle["model"], bundle["medians"], bundle["features"]

    sub = _match_group(daily, whsl_mrkt_nm, gds_sclsf_nm)
    if len(sub) == 0:
        combos = (daily[["whsl_mrkt_nm", "gds_sclsf_nm"]].drop_duplicates()
                  .head(20).to_string(index=False))
        return (f"[안내] '{whsl_mrkt_nm}' x '{gds_sclsf_nm}' 조합의 과거 데이터가 없습니다.\n"
                f"사용 가능한 (시장, 품종) 예시:\n{combos}")

    latest = sub.sort_values("date").iloc[[-1]].copy()
    mkt = latest["whsl_mrkt_nm"].iloc[0]
    var = latest["gds_sclsf_nm"].iloc[0]
    asof = latest["trd_clcln_ymd"].iloc[0]

    for c in ch.NUMERIC:
        latest[c] = latest[c].fillna(medians[c])

    X = latest[features]
    pred = float(model.predict(X)[0])

    print(f"  대상    : {mkt} x {var}")
    print(f"  기준시점: {asof} (가장 최근 관측)")
    print(f"  최근시세: lag1={latest['lag1'].iloc[0]:,.0f}  "
          f"roll3={latest['roll3'].iloc[0]:,.0f}  "
          f"roll7={latest['roll7'].iloc[0]:,.0f} (원/kg)")

    month = int(pd.to_datetime(asof).month)
    grades = _grade_prices(pred, var, month)
    if grades:
        # 실데이터 등급 체계: 특/상/보통/4~8등/무등급 등 — 비싼 순으로 전부 출력
        print("  등급별 예상가(과거 비율 기반):")
        for g, p in sorted(grades.items(), key=lambda kv: -kv[1]):
            print(f"    {g}: {p:,.1f} 원/kg")
    return pred


def main():
    args = sys.argv[1:]
    mkt, var = (args[0], args[1]) if len(args) >= 2 else ("서울가락", "후지")
    print(f"예측 요청(v2): 시장='{mkt}', 품종='{var}'")
    result = predict_next(mkt, var)
    if isinstance(result, str):
        print(result)
    else:
        print(f"\n예측 다음 대표시세: {result:,.1f} 원/kg")


if __name__ == "__main__":
    main()
