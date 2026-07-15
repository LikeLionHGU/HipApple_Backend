# HipApple 사과 시세 예측 모델 v2 — 배포 패키지

백엔드(Python 배치)용 전체 세트입니다. **이 폴더 하나로 예측이 돌아갑니다.**

## 1. 환경 준비 (Python 3.10+)

```bash
pip install -r requirements.txt
```

> ⚠️ scikit-learn은 반드시 **1.9.0** (모델 학습 버전과 일치해야 joblib 로드 가능)

## 2. 동작 확인

```bash
python3 predict_daily2.py 서울가락 후지
```

정상이면 아래처럼 출력됩니다:

```
예측 다음 대표시세: 3,424.3 원/kg
등급별 예상가:  특: 6,330  상: 2,598  ...
```

## 3. 매일 배치 (cron 권장, 새벽 1회)

```bash
# ① 최신 경매 데이터 수집 (data/apple_auction_raw.csv 갱신)
python3 download_data.py

# ② 예측 실행 → 결과를 DB/JSON으로 저장하는 로직은 이 함수를 사용
python3 -c "
from predict_daily2 import predict_next
print(predict_next('서울가락', '후지'))   # 원/kg float 반환
"
```

`download_data.py`의 수집 날짜 범위(START_DATE/END_DATE)는 배치에서
'어제~오늘'로 좁혀 쓰는 것을 권장합니다 (기본값은 초기 적재용 30일).

## 4. 파일 구성

| 경로 | 역할 |
|------|------|
| `models/final_daily_model2.joblib` | 예측 모델 (HistGradientBoosting, 원/kg) |
| `data/grade_ratio.csv` | (품종×월×등급) 가격비율 → 등급별 예상가 계산용 |
| `data/apple_history_raw.csv` | 과거 데이터(2021~2023, 정적) — 피처 기준 유지용, 삭제 금지 |
| `data/apple_auction_raw.csv` | 현행 경매 데이터 — `download_data.py`가 매일 갱신 |
| `predict_daily2.py` | 예측 진입점: `predict_next(시장, 품종)` |
| `common_hist.py` / `common_daily.py` / `common.py` | 피처 생성 파이프라인 (수정 불필요) |
| `download_data.py` | 현행 데이터 수집기 (전국 공영도매시장 실시간 경매정보 API) |

## 5. 성능 참고 (시간기반 테스트 실측)

- 일별 대표시세: MAE 843원/kg, R² 0.36, MAPE ~30%
- 등급별 예상가는 과거 등급별 가격비율 기반 추정치 (예: 후지 7월 특 = 평균×1.85)
- 다음 1거래일 예측이 가장 신뢰도 높음. 여러 날 예측은 오차가 누적됨.

문의: 프론트 레포 `ml/README.md`에 전체 문서 있음.
