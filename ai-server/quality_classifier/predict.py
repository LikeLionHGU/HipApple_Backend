"""학습된 model.joblib로 새 사과 사진 + Storage 필드를 보고 상/중/하를 예측한다.

vision_llm_lab(딥러닝, 블랙박스)과 달리 이 모델은 RandomForest라 feature_importances_와
predict_proba()로 "왜 이렇게 판단했는지"를 어느 정도 설명할 수 있다.

사용:
  from predict import predict
  result = predict("apple.jpg", brix=15, hardness=10, storage_method="CA",
                    storage_days=30, amount=5, model_path="model.joblib")

  CLI:
  python3 predict.py apple.jpg --brix 15 --hardness 10 --storage-method CA \\
      --storage-days 30 --amount 5 --model model.joblib
"""
import argparse
import os
import sys

import joblib
import pandas as pd

BASE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, BASE)

from image_features import FEATURE_NAMES, extract_image_features  # noqa: E402

_bundle = None


def _load(model_path):
    global _bundle
    if _bundle is None:
        if not os.path.exists(model_path):
            raise FileNotFoundError(
                f"모델 파일이 없습니다: {model_path}\n먼저 `python3 train.py` 를 실행하세요."
            )
        _bundle = joblib.load(model_path)
    return _bundle


def _build_row(image_path, brix, hardness, storage_method, storage_days, amount):
    tabular = {
        "brix": float(brix),
        "hardness": float(hardness),
        "storageDays": float(storage_days),
        "amount": float(amount),
        "is_ca": 1.0 if str(storage_method).upper() == "CA" else 0.0,
    }
    image_values = extract_image_features(image_path)
    tabular.update(dict(zip(FEATURE_NAMES, image_values)))
    return tabular


def predict(image_path, brix, hardness, storage_method, storage_days, amount, model_path="model.joblib"):
    bundle = _load(model_path)
    model, feature_names = bundle["model"], bundle["feature_names"]

    row = _build_row(image_path, brix, hardness, storage_method, storage_days, amount)
    X = pd.DataFrame([row])[feature_names]

    label = model.predict(X)[0]
    proba = dict(zip(model.classes_, model.predict_proba(X)[0]))
    importances = sorted(zip(feature_names, model.feature_importances_), key=lambda kv: kv[1], reverse=True)

    return {
        "label": label,
        "probabilities": {k: round(float(v), 3) for k, v in proba.items()},
        "top_features": [(name, round(float(score), 3)) for name, score in importances[:5]],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("image", help="판정할 사과 사진 경로")
    parser.add_argument("--brix", type=float, required=True)
    parser.add_argument("--hardness", type=float, required=True)
    parser.add_argument("--storage-method", default="CA")
    parser.add_argument("--storage-days", type=float, required=True)
    parser.add_argument("--amount", type=float, required=True)
    parser.add_argument("--model", default="model.joblib")
    args = parser.parse_args()

    result = predict(
        args.image, args.brix, args.hardness, args.storage_method,
        args.storage_days, args.amount, model_path=args.model,
    )
    print(f"예측 등급: {result['label']}")
    print(f"클래스별 확률: {result['probabilities']}")
    print("판단에 가장 크게 기여한 특징:")
    for name, score in result["top_features"]:
        print(f"  {name:14s} {score:.3f}")


if __name__ == "__main__":
    main()
