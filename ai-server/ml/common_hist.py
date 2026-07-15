"""
사과 '일별 대표시세' 예측 - 과거 히스토리(MAFRA 경락가격 API) 확장 모듈.

기존 common_daily.py 는 수정하지 않고, 그 위에 다음을 추가한다:
  1) load_history()      : data/apple_history_raw.csv (MAFRA 스키마) -> 현행 스키마 매핑.
                           파일이 없으면 빈 DataFrame 반환(현재 기본 경로).
  2) build_daily_ext()   : 현행 일별 시세에 히스토리 일별 시세를 이어붙인 확장 시계열.
                           lag/roll 피처는 '연속 구간(era) 내'에서만 계산 —
                           2023 히스토리와 2026 현행 사이의 갭을 넘는 lag 를 만들지 않는다.
                           + 계절성 피처(month, doy_sin, doy_cos) 추가.
  3) build_grade_table() : 히스토리에 등급(특/상/중/하)이 있으면
                           (품종 x 월 x 등급)별 'vwap 대비 가격비율' 테이블 생성
                           + data/grade_ratio.csv 저장. 히스토리 없으면 None.

히스토리가 없을 때 build_daily_ext() 는 common_daily.build_daily() 와 동일한 행
(+계절성 컬럼)을 반환한다 — 기존 동작을 깨지 않는다.

MAFRA -> 현행 스키마 매핑:
  DELNG_DE(YYYYMMDD)        -> trd_clcln_ymd ('YYYY-MM-DD')
  WHSAL_MRKT_CODE           -> whsl_mrkt_cd  (현행 API 와 동일 코드체계, 예: 110008)
  WHSAL_MRKT_NM             -> whsl_mrkt_nm  (시장코드 기준으로 '현행 명칭'으로 치환.
                                              코드 매핑 실패 시 OLD2NEW_MARKET_NAME dict,
                                              그래도 없으면 구명칭 그대로)
  STD_SPCIES_NEW_NM         -> gds_sclsf_nm  (없으면 STD_SPCIES_NM)
  STD_QLITY_NEW_NM          -> grade
  SBID_PRIC / DELNG_PRUT    -> price_per_kg  (포장단위 낙찰가 / 단위중량 kg — 의미 검증 필요:
                                              verify_price_semantics() 로 통계 확인)
  DELNG_QY * DELNG_PRUT     -> weight        (거래 중량 kg)
"""
import os
import numpy as np
import pandas as pd

import common
import common_daily as cd

BASE = os.path.dirname(os.path.abspath(__file__))
HIST_CSV = os.path.join(BASE, "data", "apple_history_raw.csv")
GRADE_RATIO_CSV = os.path.join(BASE, "data", "grade_ratio.csv")

# 등급 비율 테이블 최소 표본 가드 (dry-run/소량 데이터로 무의미한 테이블 생성 방지)
GRADE_MIN_TOTAL_ROWS = 500   # 등급 있는 히스토리 전체 최소 행수
GRADE_MIN_GROUP_N = 30       # (품종 x 월 x 등급) 셀당 최소 거래 건수

GROUP_KEYS = cd.GROUP_KEYS          # ["whsl_mrkt_nm", "gds_sclsf_nm"]
TARGET = cd.TARGET                  # "vwap"
TEST_START = cd.TEST_START          # common.TEST_START 재사용

# 계절성 피처 3개 추가 (연중 주기)
SEASONAL = ["month", "doy_sin", "doy_cos"]
CATEGORICAL = list(cd.CATEGORICAL)
NUMERIC = list(cd.NUMERIC) + SEASONAL
FEATURES = CATEGORICAL + NUMERIC

# 구시장명 -> 현행 시장명 수동 매핑 자리.
# 시장코드(whsl_mrkt_cd) 매핑이 우선이므로 보통 비워둬도 되지만,
# 코드가 결측/불일치인 행이 있으면 여기에 추가한다.
# 예: "서울강서도매시장": "서울강서",
OLD2NEW_MARKET_NAME = {
}


def _current_market_code_map():
    """현행 원본 데이터에서 (whsl_mrkt_cd -> whsl_mrkt_nm) 매핑을 만든다."""
    raw = pd.read_csv(common.RAW_CSV, low_memory=False,
                      usecols=["whsl_mrkt_cd", "whsl_mrkt_nm"])
    raw = raw.dropna().drop_duplicates()
    code = raw["whsl_mrkt_cd"].astype(str).str.strip().str.replace(r"\.0$", "", regex=True)
    return dict(zip(code, raw["whsl_mrkt_nm"].astype(str)))


def load_history(path=HIST_CSV):
    """MAFRA 히스토리 CSV -> 현행 스키마 DataFrame.

    반환 컬럼: trd_clcln_ymd, whsl_mrkt_cd, whsl_mrkt_nm, gds_sclsf_nm,
               grade, price_per_kg, weight, n(=1 아님, 개별 행)
    파일이 없으면 '빈' DataFrame(동일 컬럼) 반환 — 현재 기본 경로.
    """
    cols = ["trd_clcln_ymd", "whsl_mrkt_cd", "whsl_mrkt_nm",
            "gds_sclsf_nm", "grade", "price_per_kg", "weight"]
    if not os.path.exists(path):
        return pd.DataFrame(columns=cols)

    df = pd.read_csv(path, low_memory=False)

    # --- 필수 컬럼 확인 ---
    for c in ["DELNG_DE", "SBID_PRIC", "DELNG_PRUT", "DELNG_QY"]:
        if c not in df.columns:
            raise ValueError(f"히스토리 CSV 에 필수 컬럼 없음: {c}")

    out = pd.DataFrame()

    # 날짜: YYYYMMDD -> 'YYYY-MM-DD'
    de = df["DELNG_DE"].astype(str).str.strip().str.replace(r"\.0$", "", regex=True)
    out["trd_clcln_ymd"] = pd.to_datetime(de, format="%Y%m%d",
                                          errors="coerce").dt.strftime("%Y-%m-%d")

    # 시장 코드/명칭 — 코드 기준 현행 명칭 매핑 우선
    code = (df.get("WHSAL_MRKT_CODE", pd.Series(index=df.index, dtype=object))
              .astype(str).str.strip().str.replace(r"\.0$", "", regex=True))
    old_nm = df.get("WHSAL_MRKT_NM", pd.Series(index=df.index, dtype=object)).astype(str)
    code_map = _current_market_code_map()
    out["whsl_mrkt_cd"] = code
    out["whsl_mrkt_nm"] = (
        code.map(code_map)                       # 1순위: 시장코드 -> 현행명
            .fillna(old_nm.map(OLD2NEW_MARKET_NAME))  # 2순위: 구명칭 수동 매핑
            .fillna(old_nm)                      # 3순위: 구명칭 그대로
    )

    # 품종: 신품종명 우선, 없으면 구품종명
    sp_new = df.get("STD_SPCIES_NEW_NM", pd.Series(index=df.index, dtype=object))
    sp_old = df.get("STD_SPCIES_NM", pd.Series(index=df.index, dtype=object))
    out["gds_sclsf_nm"] = sp_new.fillna(sp_old).astype(str).str.strip()

    # 등급 (특/상/중/하 등)
    out["grade"] = (df.get("STD_QLITY_NEW_NM", pd.Series(index=df.index, dtype=object))
                      .astype(str).str.strip())

    # 가격/중량
    pric = pd.to_numeric(df["SBID_PRIC"], errors="coerce")
    prut = pd.to_numeric(df["DELNG_PRUT"], errors="coerce")
    qy = pd.to_numeric(df["DELNG_QY"], errors="coerce")
    out["price_per_kg"] = pric / prut
    out["weight"] = qy * prut

    # 유효값 필터 (common.load_clean 과 같은 정신)
    out = out[(pric > 0) & (prut > 0) & (qy > 0)]
    out = out.dropna(subset=["trd_clcln_ymd", "price_per_kg", "weight"])

    # 이상치 제거: kg당 가격 1~99 백분위 (common.load_clean 4단계와 동일 규칙)
    if len(out) > 0:
        lo, hi = out["price_per_kg"].quantile([0.01, 0.99])
        out = out[(out["price_per_kg"] >= lo) & (out["price_per_kg"] <= hi)]

    return out.reset_index(drop=True)[cols]


def verify_price_semantics(hist=None):
    """SBID_PRIC/DELNG_PRUT 이 실제 원/kg 시세 범위(약 1500~6000원/kg대)에
    부합하는지 통계 요약을 출력. 히스토리 확보 후 반드시 1회 확인할 것."""
    hist = load_history() if hist is None else hist
    if len(hist) == 0:
        print("[verify] 히스토리 없음 — 검증 생략")
        return None
    desc = hist["price_per_kg"].describe(percentiles=[.05, .25, .5, .75, .95])
    print("[verify] price_per_kg = SBID_PRIC / DELNG_PRUT 분포:")
    print(desc)
    med = float(desc["50%"])
    ok = 1000 <= med <= 10000
    print(f"[verify] 중앙값 {med:,.0f} 원/kg -> 사과 도매 시세 범위 부합: {ok}")
    return desc


def build_daily_ext(include_history=True, hist=None):
    """현행 + (있으면) 히스토리를 이어붙인 (시장 x 품종 x 일) 시세 + 피처 DataFrame.

    - lag/roll 은 GROUP_KEYS + era 로 그룹핑해 '연속 구간 내'에서만 계산
      (era='hist'/'cur' — 2023->2026 갭을 넘어 lag 를 만들지 않음).
    - 계절성 피처 month/doy_sin/doy_cos 추가.
    - 히스토리 없으면 common_daily.build_daily() 와 동일 행 + 계절성 컬럼.
    """
    # --- 현행 데이터: common_daily.build_daily 의 집계 단계와 동일 ---
    df = common.load_clean()
    df["weight"] = df["qty"] * df["unit_qty"]
    df["amount"] = df["price_per_kg"] * df["weight"]
    cur = (
        df.groupby(GROUP_KEYS + ["trd_clcln_ymd"])
          .agg(amount=("amount", "sum"), weight=("weight", "sum"),
               n=("price_per_kg", "size"))
          .reset_index()
    )
    cur["era"] = "cur"

    parts = [cur]

    # --- 히스토리 데이터: 동일 (시장 x 품종 x 일) 집계 ---
    if include_history:
        h = load_history() if hist is None else hist
        if len(h) > 0:
            h = h.copy()
            h["amount"] = h["price_per_kg"] * h["weight"]
            hh = (
                h.groupby(GROUP_KEYS + ["trd_clcln_ymd"])
                 .agg(amount=("amount", "sum"), weight=("weight", "sum"),
                      n=("price_per_kg", "size"))
                 .reset_index()
            )
            # 현행 구간과 날짜가 겹치면 현행 데이터를 우선(중복 제거)
            cur_min = cur["trd_clcln_ymd"].min()
            hh = hh[hh["trd_clcln_ymd"] < cur_min]
            hh["era"] = "hist"
            if len(hh) > 0:
                parts.insert(0, hh)

    agg = pd.concat(parts, ignore_index=True)
    agg = agg[agg["weight"] > 0].copy()
    agg["vwap"] = agg["amount"] / agg["weight"]
    agg["date"] = pd.to_datetime(agg["trd_clcln_ymd"])
    agg = agg.sort_values(GROUP_KEYS + ["date"]).reset_index(drop=True)

    # --- 연속 구간(segment) 분할: '히스토리 구간에만' 적용 ---
    # 히스토리가 '월별 대표 주간' 샘플링으로 수집될 수 있으므로(주간 블록 사이 ~3주 갭),
    # hist 구간에서 GAP_DAYS 초과 갭이 나오면 lag/roll 를 끊는다.
    # cur(현행) 구간은 분할하지 않는다 — 드물게 거래되는 품종의 '직전 관측' lag 의미를
    # common_daily(v1)와 동일하게 유지(기존 동작 보존).
    GAP_DAYS = 5
    gap = agg.groupby(GROUP_KEYS + ["era"])["date"].diff().dt.days
    new_seg = ((gap > GAP_DAYS) & (agg["era"] == "hist")).fillna(False)
    agg["segment"] = new_seg.groupby(
        [agg[k] for k in GROUP_KEYS] + [agg["era"]]).cumsum()

    # --- lag/roll: 연속 구간(era+segment) 내에서만 (common_daily 와 동일 정의) ---
    g = agg.groupby(GROUP_KEYS + ["era", "segment"])
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

    # --- 계절성 (연중 주기) ---
    agg["month"] = agg["date"].dt.month
    doy = agg["date"].dt.dayofyear
    agg["doy_sin"] = np.sin(2 * np.pi * doy / 365.25)
    agg["doy_cos"] = np.cos(2 * np.pi * doy / 365.25)

    agg = agg.dropna(subset=["lag1"]).reset_index(drop=True)
    return agg


def build_grade_table(hist=None, save=True):
    """(품종 x 월 x 등급)별 'vwap 대비 등급별 가격비율' 테이블.

    ratio = (해당 품종·월·등급의 물량가중 평균가) / (해당 품종·월 전체 vwap)
    히스토리가 없거나 등급 정보가 없으면 None.
    save=True 면 data/grade_ratio.csv 로 저장.
    """
    hist = load_history() if hist is None else hist
    if len(hist) == 0:
        return None
    h = hist.copy()
    h["grade"] = h["grade"].astype(str).str.strip()
    h = h[~h["grade"].isin(["", "nan", "None"])]
    # 최소 표본 가드: dry-run 수준의 소량 데이터로 무의미한 비율 테이블을 만들지 않음
    if len(h) < GRADE_MIN_TOTAL_ROWS:
        print(f"[grade_table] 등급 히스토리 {len(h)}행 < 최소 {GRADE_MIN_TOTAL_ROWS}행 "
              f"-> 테이블 생성 생략 (실데이터 수집 후 재실행)")
        return None

    h["month"] = pd.to_datetime(h["trd_clcln_ymd"]).dt.month
    h["amount"] = h["price_per_kg"] * h["weight"]

    # 분모: (품종 x 월) 전체 vwap
    tot = (h.groupby(["gds_sclsf_nm", "month"])
             .agg(t_amount=("amount", "sum"), t_weight=("weight", "sum"))
             .reset_index())
    tot["vwap_all"] = tot["t_amount"] / tot["t_weight"]

    # 분자: (품종 x 월 x 등급) vwap
    grd = (h.groupby(["gds_sclsf_nm", "month", "grade"])
             .agg(g_amount=("amount", "sum"), g_weight=("weight", "sum"),
                  n=("price_per_kg", "size"))
             .reset_index())
    grd["vwap_grade"] = grd["g_amount"] / grd["g_weight"]
    # 그룹 최소 표본: 거래 건수가 적은 (품종x월x등급) 셀은 비율 신뢰 불가 -> 제외
    grd = grd[grd["n"] >= GRADE_MIN_GROUP_N]
    if len(grd) == 0:
        print(f"[grade_table] 그룹당 최소 {GRADE_MIN_GROUP_N}건을 넘는 셀이 없음 -> 생성 생략")
        return None

    tab = grd.merge(tot[["gds_sclsf_nm", "month", "vwap_all"]],
                    on=["gds_sclsf_nm", "month"], how="left")
    tab["ratio"] = tab["vwap_grade"] / tab["vwap_all"]
    tab = tab[["gds_sclsf_nm", "month", "grade",
               "vwap_grade", "vwap_all", "ratio", "n"]]
    tab = tab.sort_values(["gds_sclsf_nm", "month", "grade"]).reset_index(drop=True)

    if save:
        os.makedirs(os.path.dirname(GRADE_RATIO_CSV), exist_ok=True)
        tab.to_csv(GRADE_RATIO_CSV, index=False)
    return tab


def get_split(include_history=True):
    d = build_daily_ext(include_history=include_history)
    test_mask = d["trd_clcln_ymd"] >= TEST_START
    return d[~test_mask].reset_index(drop=True), d[test_mask].reset_index(drop=True)


def _fill(df, medians):
    df = df.copy()
    for c in NUMERIC:
        df[c] = df[c].fillna(medians[c])
    return df


def get_xy(include_history=True):
    """(X_train, y_train, X_test, y_test, fill_medians). common_daily.get_xy 와 동일 규칙."""
    train_df, test_df = get_split(include_history=include_history)
    medians = train_df[NUMERIC].median()
    Xtr = _fill(train_df, medians)[FEATURES]
    Xte = _fill(test_df, medians)[FEATURES]
    return Xtr, train_df[TARGET].values, Xte, test_df[TARGET].values, medians


evaluate = common.evaluate


if __name__ == "__main__":
    h = load_history()
    print(f"history rows: {len(h)}  (파일: {HIST_CSV}, 존재={os.path.exists(HIST_CSV)})")
    if len(h):
        print("history 기간:", h["trd_clcln_ymd"].min(), "~", h["trd_clcln_ymd"].max())
        verify_price_semantics(h)
        tab = build_grade_table(h)
        print("grade table:", None if tab is None else tab.shape)

    ext = build_daily_ext()
    base = cd.build_daily()
    print(f"\nbuild_daily_ext: {ext.shape} | common_daily.build_daily: {base.shape}")
    print("era 분포:", ext["era"].value_counts().to_dict())
    tr, te = get_split()
    print("train:", tr.shape, "| test:", te.shape)
