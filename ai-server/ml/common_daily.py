"""
사과 '일별 대표 시세' 예측 - 공용 모듈.
개별 경매건이 아니라 (도매시장 x 품종)별 하루 대표 시세(물량가중 평균, 원/kg)를 예측한다.
등급 노이즈가 평균으로 상쇄되어 개별건 모델보다 오차가 크게 작다.

타깃: vwap = Σ(price_per_kg * weight) / Σ(weight)   (물량가중 평균 kg당 시세)
분할: 시간 기반 — common.TEST_START(2026-07-10) 이상이면 test.
평가: common.evaluate (MAE/RMSE/R2/MAPE, 원/kg)
"""
import os
import numpy as np
import pandas as pd

import common  # 개별건 모듈의 load_clean / evaluate / TEST_START 재사용

BASE = os.path.dirname(os.path.abspath(__file__))

GROUP_KEYS = ["whsl_mrkt_nm", "gds_sclsf_nm"]   # 시세를 정의하는 단위
TARGET = "vwap"
TEST_START = common.TEST_START

# (시계열) 과거 정보만 사용하는 피처 — 누수 없음
CATEGORICAL = ["whsl_mrkt_nm", "gds_sclsf_nm"]
NUMERIC = [
    "lag1", "lag2", "lag3",       # 최근 1~3거래일 시세
    "roll3", "roll7",             # 최근 3/7거래일 평균
    "vol_std3",                   # 최근 3일 변동성(표준편차)
    "momentum",                   # lag1 - lag2 (단기 추세)
    "prev_n", "prev_vol",         # 직전일 거래 건수/물량(유동성)
    "dow", "t_index",             # 요일, 추세 인덱스
]
FEATURES = CATEGORICAL + NUMERIC


def build_daily():
    """개별건 정제 데이터 -> (시장 x 품종 x 일) 시세 + 시계열 피처 DataFrame."""
    df = common.load_clean()
    df["weight"] = df["qty"] * df["unit_qty"]                 # 거래 중량(kg)
    df["amount"] = df["price_per_kg"] * df["weight"]          # 거래 금액

    agg = (
        df.groupby(GROUP_KEYS + ["trd_clcln_ymd"])
          .agg(amount=("amount", "sum"), weight=("weight", "sum"),
               n=("price_per_kg", "size"))
          .reset_index()
    )
    agg = agg[agg["weight"] > 0].copy()
    agg["vwap"] = agg["amount"] / agg["weight"]
    agg["date"] = pd.to_datetime(agg["trd_clcln_ymd"])
    agg = agg.sort_values(GROUP_KEYS + ["date"]).reset_index(drop=True)

    g = agg.groupby(GROUP_KEYS)
    agg["lag1"] = g["vwap"].shift(1)
    agg["lag2"] = g["vwap"].shift(2)
    agg["lag3"] = g["vwap"].shift(3)
    agg["roll3"] = g["vwap"].transform(lambda s: s.shift(1).rolling(3, min_periods=1).mean())
    agg["roll7"] = g["vwap"].transform(lambda s: s.shift(1).rolling(7, min_periods=1).mean())
    agg["vol_std3"] = g["vwap"].transform(lambda s: s.shift(1).rolling(3, min_periods=2).std())
    agg["momentum"] = agg["lag1"] - agg["lag2"]
    agg["prev_n"] = g["n"].shift(1)
    agg["prev_vol"] = g["weight"].shift(1)
    agg["dow"] = agg["date"].dt.dayofweek
    agg["t_index"] = (agg["date"] - agg["date"].min()).dt.days

    # lag1 은 반드시 있어야 함(예측 시점에 최소 직전 시세 필요)
    agg = agg.dropna(subset=["lag1"]).reset_index(drop=True)
    return agg


def get_split():
    d = build_daily()
    test_mask = d["trd_clcln_ymd"] >= TEST_START
    return d[~test_mask].reset_index(drop=True), d[test_mask].reset_index(drop=True)


def _fill(df, medians):
    df = df.copy()
    for c in NUMERIC:
        df[c] = df[c].fillna(medians[c])
    return df


def get_xy():
    """(X_train, y_train, X_test, y_test, fill_medians) 반환. 결측 수치는 train 중앙값으로."""
    train_df, test_df = get_split()
    medians = train_df[NUMERIC].median()
    Xtr = _fill(train_df, medians)[FEATURES]
    Xte = _fill(test_df, medians)[FEATURES]
    return Xtr, train_df[TARGET].values, Xte, test_df[TARGET].values, medians


evaluate = common.evaluate


if __name__ == "__main__":
    tr, te = get_split()
    print("daily group-days -> train:", tr.shape, "| test:", te.shape)
    print("train 기간:", tr["trd_clcln_ymd"].min(), "~", tr["trd_clcln_ymd"].max())
    print("test  기간:", te["trd_clcln_ymd"].min(), "~", te["trd_clcln_ymd"].max())
    print("\n타깃 vwap 요약:\n", tr[TARGET].describe(percentiles=[.05, .5, .95]))
    print("\n[베이스라인] 어제값(lag1):", common.evaluate(te[TARGET].values, te["lag1"].values))
    r3 = te["roll3"].fillna(te["lag1"]).values
    print("[베이스라인] 최근3일평균(roll3):", common.evaluate(te[TARGET].values, r3))
    print("[베이스라인] train 전체평균:", common.evaluate(te[TARGET].values, np.full(len(te), tr[TARGET].mean())))
