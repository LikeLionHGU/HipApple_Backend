# 사과 품질 상/중/하 분류기 (이미지 특징 + Storage 데이터, scikit-learn)

`vision_llm_lab`(CLIP + 소형 언어모델, 문장을 직접 생성하는 딥러닝 모델)과는 다른 접근입니다.
이 모듈은:

1. 사진에서 **설명 가능한 수치 특징**(색상/밝기/질감)을 직접 뽑고
2. Storage 엔티티에 이미 있는 필드(`brix`, `hardness`, `storageMethod`, 저장일수, `amount`)와 이어붙여
3. **RandomForestClassifier**로 상/중/하 3단계를 분류합니다.

`ai-server/ml/`의 가격 예측 파이프라인과 마찬가지로 scikit-learn 기반이라 GPU가 필요 없고,
`feature_importances_` / `predict_proba()`로 "어떤 특징이 판정에 얼마나 기여했는지" 확인할 수 있어
`vision_llm_lab`보다 훨씬 설명 가능합니다(대신 표현력은 더 단순합니다).

## 파일 구성

| 파일 | 역할 |
|------|------|
| `image_features.py` | 사진 1장 -> 색상/밝기/질감 수치 특징 12개 (`FEATURE_NAMES`) |
| `label_data.py` | manifest.csv(사진+Storage필드) -> gpt-4o-mini로 자동 라벨링 -> dataset.csv |
| `train.py` | dataset.csv로 RandomForest 학습, 정확도/혼동행렬/feature importance 출력, model.joblib 저장 |
| `predict.py` | 새 사진 + Storage 필드로 상/중/하 예측 (`predict()` 함수 또는 CLI) |

## 실행 순서

### 1. manifest.csv 준비

사진과, 그 사진이 찍힌 저장고 배치의 Storage 필드를 매칭한 CSV를 직접 만듭니다
(Storage 엔티티 필드명과 동일하게 맞췄습니다):

```csv
image,brix,hardness,storageMethod,storageDays,amount
photos/apple_001.jpg,15,10,CA,30,5
photos/apple_002.jpg,12,8,MA,60,3
```

### 2. 자동 라벨링

```bash
pip install -r requirements.txt
export OPENAI_API_KEY=sk-...
python3 label_data.py --manifest manifest.csv --out dataset.csv
```

gpt-4o-mini가 특/상/중/하로 판정한 뒤, 특→상 / 상→상 / 중→중 / 하→하로 매핑해 3단계로 저장합니다
(`판정불가`는 학습 데이터에서 제외).

### 3. 학습

```bash
python3 train.py --dataset dataset.csv --out model.joblib
```

정확도, classification report, 혼동행렬, feature importance가 출력됩니다.
**데이터가 적으면(수십 건 이하) 정확도가 낮게 나올 수 있는데, 이 단계의 목적은 파이프라인이
정상 동작하는지 확인하는 것이지 최고 정확도를 내는 게 아닙니다.**

### 4. 예측

```bash
python3 predict.py test_apple.jpg --brix 15 --hardness 10 --storage-method CA \
    --storage-days 30 --amount 5 --model model.joblib
```

예측 등급, 클래스별 확률, 판단에 가장 크게 기여한 특징 순위가 출력됩니다.

## 한계

- `image_features.py`는 고전적인 색상/밝기 통계라 흠집/멍처럼 국소적인 결함을 정교하게 잡지 못합니다
  (전역 통계라서, 예를 들어 작은 반점 하나는 평균값에 거의 영향을 주지 않습니다)
- 학습 라벨이 GPT-4o-mini의 판정을 그대로 흉내낸 것이므로, 선생 모델의 오류/편향을 그대로 물려받습니다
- Storage 필드와 사진이 정확히 매칭된 manifest.csv가 있어야 하는데, 실제 운영 데이터에서 이 매칭을
  구성하는 과정 자체가 별도 작업입니다
