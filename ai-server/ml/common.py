"""
사과 경매 낙찰가 예측 - 공용 모듈.
모든 모델 에이전트가 이 모듈을 import 하여 '동일한' 데이터/전처리/분할/평가 기준을 공유한다.

타깃: price_per_kg = scsbd_prc / unit_qty  (kg당 낙찰가, 원)
분할: 시간 기반(time-based) — 마지막 5거래일을 테스트로.
평가: MAE, RMSE, R² (모두 원/kg 스케일)
"""
import os
import numpy as np
import pandas as pd

BASE = os.path.dirname(os.path.abspath(__file__))
RAW_CSV = os.path.join(BASE, "data", "apple_auction_raw.csv")

TARGET = "price_per_kg"

# 시간 기반 분할 기준: 이 날짜 이상이면 테스트(미래 예측 검증)
TEST_START = "2026-07-10"

CATEGORICAL = [
    "whsl_mrkt_nm",   # 도매시장
    "corp_nm",        # 법인
    "gds_sclsf_nm",   # 품종(소분류)
    "plor_sido",      # 원산지 시/도 (원산지명 첫 토큰)
    "pkg_nm",         # 포장
    "trd_se",         # 매매방법
]
NUMERIC = [
    "unit_qty",       # 포장 단위중량(kg)
    "qty",            # 거래 물량(포장 수)
    "dow",            # 요일 (0=월)
    "day",            # 일(day of month)
    "t_index",        # 추세 인덱스(시작일로부터 경과일)
]
FEATURES = CATEGORICAL + NUMERIC


def load_clean():
    """원본 CSV를 읽어 정제 + 피처 엔지니어링된 DataFrame을 반환."""
    df = pd.read_csv(RAW_CSV, low_memory=False)

    for c in ["scsbd_prc", "qty", "unit_qty"]:
        df[c] = pd.to_numeric(df[c], errors="coerce")

    # 1) 단위가 kg 인 건만 (일관된 kg당 가격 산출)
    df = df[df["unit_nm"] == "kg"].copy()

    # 2) 유효값 필터: 물량>0, 단위중량>0, 낙찰가>0
    df = df[(df["qty"] > 0) & (df["unit_qty"] > 0) & (df["scsbd_prc"] > 0)]

    # 3) 타깃 = kg당 낙찰가
    df["price_per_kg"] = df["scsbd_prc"] / df["unit_qty"]

    # 4) 이상치 제거: kg당 가격 1~99 백분위 범위로 클리핑(제거)
    lo, hi = df["price_per_kg"].quantile([0.01, 0.99])
    df = df[(df["price_per_kg"] >= lo) & (df["price_per_kg"] <= hi)]

    # 5) 피처 엔지니어링
    #    원산지 시/도 (첫 토큰) — 고유값 487 -> 대폭 축소
    df["plor_sido"] = (
        df["plor_nm"].astype(str).str.split().str[0].fillna("기타").replace("nan", "기타")
    )
    dt = pd.to_datetime(df["trd_clcln_ymd"])
    df["dow"] = dt.dt.dayofweek
    df["day"] = dt.dt.day
    df["t_index"] = (dt - dt.min()).dt.days

    # 범주형 결측 처리
    for c in CATEGORICAL:
        df[c] = df[c].astype(str).fillna("기타").replace("nan", "기타")

    df = df.reset_index(drop=True)
    return df


def get_split():
    """시간 기반 train/test 분할된 (train_df, test_df) 반환."""
    df = load_clean()
    test_mask = df["trd_clcln_ymd"] >= TEST_START
    train_df = df[~test_mask].reset_index(drop=True)
    test_df = df[test_mask].reset_index(drop=True)
    return train_df, test_df


def get_xy():
    """(X_train, y_train, X_test, y_test) 반환. y는 원/kg 원단위."""
    train_df, test_df = get_split()
    X_train, y_train = train_df[FEATURES], train_df[TARGET].values
    X_test, y_test = test_df[FEATURES], test_df[TARGET].values
    return X_train, y_train, X_test, y_test


def evaluate(y_true, y_pred):
    """공통 평가 지표 (원/kg 스케일). dict 반환."""
    y_true = np.asarray(y_true, dtype=float)
    y_pred = np.asarray(y_pred, dtype=float)
    err = y_pred - y_true
    mae = float(np.mean(np.abs(err)))
    rmse = float(np.sqrt(np.mean(err ** 2)))
    ss_res = float(np.sum(err ** 2))
    ss_tot = float(np.sum((y_true - y_true.mean()) ** 2))
    r2 = 1.0 - ss_res / ss_tot if ss_tot > 0 else float("nan")
    # MAPE (0 근처 방지 위해 실제값 사용, 안전)
    mape = float(np.mean(np.abs(err) / np.clip(np.abs(y_true), 1e-9, None)) * 100)
    return {"MAE": round(mae, 1), "RMSE": round(rmse, 1),
            "R2": round(r2, 4), "MAPE_%": round(mape, 2)}


if __name__ == "__main__":
    tr, te = get_split()
    print("train:", tr.shape, "| test:", te.shape)
    print("train 기간:", tr["trd_clcln_ymd"].min(), "~", tr["trd_clcln_ymd"].max())
    print("test  기간:", te["trd_clcln_ymd"].min(), "~", te["trd_clcln_ymd"].max())
    print("\n타깃(price_per_kg) 요약:")
    print(tr[TARGET].describe(percentiles=[.05, .5, .95]))
    print("\n피처별 고유값:")
    for c in CATEGORICAL:
        print(f"  {c:16s}: {tr[c].nunique()}")
    print("\n기준 베이스라인(전체 평균 예측) 성능:")
    import numpy as np
    base_pred = np.full(len(te), tr[TARGET].mean())
    print(evaluate(te[TARGET].values, base_pred))
