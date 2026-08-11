"""dataset.csv(사진 + Storage 필드 + 라벨)로 상/중/하 분류기를 학습시킨다.

이미지 특징(image_features.py) + Storage 탭형 필드(brix/hardness/storageMethod/
storageDays/amount)를 이어붙여 RandomForestClassifier를 학습한다. 데이터가 적으면
정확도가 낮게 나올 수 있는데, 이 단계의 목적은 "파이프라인이 정상 동작하는지" 확인하는
것이지 최고 정확도를 내는 게 아니다.

사용:
  python3 train.py --dataset dataset.csv --out model.joblib
"""
import argparse
import sys

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix
from sklearn.model_selection import train_test_split

from image_features import FEATURE_NAMES, extract_image_features

TABULAR_FEATURE_NAMES = ["brix", "hardness", "storageDays", "amount", "is_ca"]
LABELS = ["하", "중", "상"]  # 혼동행렬 출력 순서 고정용


def build_feature_matrix(df: pd.DataFrame):
    tabular = pd.DataFrame({
        "brix": df["brix"].astype(float),
        "hardness": df["hardness"].astype(float),
        "storageDays": df["storageDays"].astype(float),
        "amount": df["amount"].astype(float),
        # StorageService.findOne()과 동일한 CA/비CA 이분법 (java 쪽 로직과 일관성 유지)
        "is_ca": df["storageMethod"].str.upper().eq("CA").astype(float),
    })

    image_rows = []
    for image_path in df["image"]:
        image_rows.append(extract_image_features(image_path))
    image_df = pd.DataFrame(image_rows, columns=FEATURE_NAMES)

    X = pd.concat([tabular.reset_index(drop=True), image_df.reset_index(drop=True)], axis=1)
    return X


def train(dataset_path: str, out_path: str, test_size: float = 0.2, seed: int = 42):
    df = pd.read_csv(dataset_path)
    if len(df) < 10:
        print(f"⚠️ 데이터가 {len(df)}건뿐입니다. 학습/평가가 매우 불안정할 수 있습니다 "
              f"— 파이프라인 동작 확인용으로는 충분합니다.")

    print("이미지 특징 추출 중...")
    X = build_feature_matrix(df)
    y = df["label"]

    stratify = y if y.value_counts().min() >= 2 else None
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=test_size, random_state=seed, stratify=stratify
    )

    model = RandomForestClassifier(
        n_estimators=300, max_depth=6, class_weight="balanced", random_state=seed
    )
    model.fit(X_train, y_train)

    y_pred = model.predict(X_test)
    acc = accuracy_score(y_test, y_pred)
    print(f"\ntest accuracy: {acc:.2f}  (test set {len(y_test)}건 — 데이터가 적으면 참고용 수치일 뿐입니다)")
    print("\nclassification report:")
    print(classification_report(y_test, y_pred, zero_division=0))

    labels_present = [l for l in LABELS if l in set(y_test) | set(y_pred)]
    print("confusion matrix", labels_present, ":")
    print(confusion_matrix(y_test, y_pred, labels=labels_present))

    importances = sorted(
        zip(X.columns, model.feature_importances_), key=lambda kv: kv[1], reverse=True
    )
    print("\nfeature importances (상위 8개, 설명 가능성이 이 모델의 장점):")
    for name, score in importances[:8]:
        print(f"  {name:14s} {score:.3f}")

    # 전체 데이터(학습 + 평가 포함)에 대한 예측 결과 출력
    print(f"\n전체 {len(df)}건 예측 결과 (학습에 쓰인 데이터 포함):")
    y_pred_all = model.predict(X)
    result_df = pd.DataFrame({
        "image": df["image"],
        "실제": y,
        "예측": y_pred_all,
        "일치": (y.values == y_pred_all)
    })
    print(result_df.to_string(index=False))

    mismatch_count = (~result_df["일치"]).sum()
    print(f"\n전체 {len(df)}건 중 {mismatch_count}건 불일치")

    joblib.dump({
        "model": model,
        "feature_names": list(X.columns),
    }, out_path)
    print(f"\n저장 완료: {out_path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dataset", default="dataset.csv")
    parser.add_argument("--out", default="model.joblib")
    parser.add_argument("--test-size", type=float, default=0.2)
    args = parser.parse_args()

    try:
        train(args.dataset, args.out, test_size=args.test_size)
    except FileNotFoundError as e:
        print(f"파일을 찾을 수 없습니다: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()