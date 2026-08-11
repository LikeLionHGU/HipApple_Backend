"""사진 한 장에서 색상/밝기/질감 관련 수치 특징을 뽑아낸다.

딥러닝(CLIP 등) 임베딩만큼 강력하진 않지만, 값 하나하나가 무슨 의미인지 사람이 읽을 수 있다는
장점이 있다 — 학습된 RandomForest의 feature_importances_로 "어떤 특징이 등급 판정에
얼마나 기여했는지"까지 확인할 수 있다. 이게 vision_llm_lab(딥러닝, 블랙박스)과 이 모듈
(고전 CV + 트리 모델, 설명 가능)의 가장 큰 차이다.
"""
import numpy as np
from PIL import Image

FEATURE_NAMES = [
    "mean_r", "mean_g", "mean_b",
    "std_r", "std_g", "std_b",
    "mean_hue", "mean_saturation", "mean_value",
    "brightness", "colorfulness", "edge_density",
]


def extract_image_features(image_path, resize=(256, 256)):
    """이미지 경로 -> FEATURE_NAMES 순서에 맞는 float 리스트."""
    image = Image.open(image_path).convert("RGB").resize(resize)
    arr = np.asarray(image, dtype=np.float32) / 255.0  # (H, W, 3)

    r, g, b = arr[..., 0], arr[..., 1], arr[..., 2]
    mean_r, mean_g, mean_b = r.mean(), g.mean(), b.mean()
    std_r, std_g, std_b = r.std(), g.std(), b.std()

    hsv = np.asarray(image.convert("HSV"), dtype=np.float32) / 255.0
    mean_hue, mean_sat, mean_val = hsv[..., 0].mean(), hsv[..., 1].mean(), hsv[..., 2].mean()

    brightness = float((0.299 * r + 0.587 * g + 0.114 * b).mean())

    # 컬러풀니스 근사치 (Hasler-Susstrunk)
    rg = r - g
    yb = 0.5 * (r + g) - b
    colorfulness = float(
        np.sqrt(rg.std() ** 2 + yb.std() ** 2) + 0.3 * np.sqrt(rg.mean() ** 2 + yb.mean() ** 2)
    )

    # 흠집/반점의 대략적인 대리 지표: 그레이스케일 gradient 크기 평균
    gray = np.asarray(image.convert("L"), dtype=np.float32)
    gy, gx = np.gradient(gray)
    edge_density = float(np.sqrt(gx ** 2 + gy ** 2).mean() / 255.0)

    return [
        float(mean_r), float(mean_g), float(mean_b),
        float(std_r), float(std_g), float(std_b),
        float(mean_hue), float(mean_sat), float(mean_val),
        brightness, colorfulness, edge_density,
    ]
