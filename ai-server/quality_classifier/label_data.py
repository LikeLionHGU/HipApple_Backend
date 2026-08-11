"""manifest CSV(사진 + Storage 필드)를 GPT-4o-mini로 자동 라벨링해 dataset.csv를 만든다.

manifest.csv 형식 (헤더 필수, Storage 엔티티 필드와 동일한 이름 사용):
  image,brix,hardness,storageMethod,storageDays,amount
  photos/apple_001.jpg,15,10,CA,30,5
  photos/apple_002.jpg,12,8,MA,60,3
  ...

ai-server/app.py의 QUALITY_ANALYSIS_PROMPT와 동일한 프롬프트로 gpt-4o-mini(선생 모델)를 호출해
특/상/중/하를 받은 뒤, 상/중/하 3단계로 매핑해서 저장한다 (지식 증류).

사용:
  export OPENAI_API_KEY=sk-...
  python3 label_data.py --manifest manifest.csv --out dataset.csv
"""
import argparse
import base64
import csv
import json
import os
import sys
import time
from pathlib import Path

import openai

# ai-server/app.py의 QUALITY_ANALYSIS_PROMPT와 동일 (프로덕션과 같은 기준으로 라벨링하기 위해 재사용)
TEACHER_PROMPT = """당신은 사과 품질을 사진만으로 육안 판정하는 AI 검수 보조입니다.
제공된 사과 사진 한 장을 보고, 다른 정보 없이 오직 사진에서 보이는 것만으로 판단하세요.
반드시 아래 JSON 형식으로만 답하세요. 다른 설명, 인사말, 코드블록 표시는 절대 포함하지 마세요.

{
  "grade": "특|상|중|하 중 하나",
  "ripeness": "사진에서 보이는 숙성 정도에 대한 짧은 한 문장",
  "color_description": "색상/착색 상태에 대한 짧은 한 문장",
  "shipment_comment": "사진 속 사과 상태를 근거로 한 출하 시점 조언 1~2문장",
  "confidence": "high|medium|low"
}

판정 기준:
- grade는 색상 균일도, 표면 흠집·멍·반점 여부, 전체적인 신선도로 종합 판단하세요.
- 품종 정보가 주어지지 않았으므로 특정 품종 기준을 가정하지 말고, 사진에 보이는 사과 자체의 상태만으로 판단하세요.
- 사진이 흐리거나 조명이 나쁘거나 사과가 프레임에 온전히 담기지 않아 판정이 어려우면 confidence를 "low"로 낮추고 grade는 보수적으로 답하세요.
- 사과가 아닌 사진이거나 식별할 수 없으면 grade를 "판정불가"로 하세요.
"""

# 특/상/중/하(4단계) -> 상/중/하(3단계) 매핑. "판정불가"는 학습에 못 쓰므로 제외한다.
GRADE_TO_LABEL = {
    "특": "상",
    "상": "상",
    "중": "중",
    "하": "하",
}

REQUIRED_COLUMNS = ["image", "brix", "hardness", "storageMethod", "storageDays", "amount"]


class DailyLimitReached(Exception):
    """하루 요청 한도(RPD)에 걸린 경우 — 재시도해봐야 소용없고 남은 한도만 깎아먹으므로
    즉시 포기하고 상위 루프에서 전체를 중단시키기 위한 전용 예외."""


def label_image(image_path: Path, max_retries: int = 5) -> dict:
    with open(image_path, "rb") as f:
        b64 = base64.b64encode(f.read()).decode("utf-8")
    ext = image_path.suffix.lower().lstrip(".")
    mime = "jpeg" if ext == "jpg" else ext
    data_url = f"data:image/{mime};base64,{b64}"

    response = None
    for attempt in range(max_retries):
        try:
            response = openai.ChatCompletion.create(
                model="gpt-4o-mini",
                messages=[{
                    "role": "user",
                    "content": [
                        {"type": "text", "text": TEACHER_PROMPT},
                        {"type": "image_url", "image_url": {"url": data_url}},
                    ],
                }],
                max_tokens=400,
                temperature=0.2,
            )
            break
        except openai.error.RateLimitError as e:
            message = str(e)
            if "per day" in message or "RPD" in message:
                # 분당(TPM) 한도와 달리 하루 단위라 몇 초/몇 분 기다린다고 안 풀림 — 재시도는 요청 수만 더 쓸 뿐
                raise DailyLimitReached(message) from e
            if attempt == max_retries - 1:
                raise
            wait = min(15 * (attempt + 1), 60)
            print(f"    ! 분당 토큰 한도(TPM) 도달, {wait}초 대기 후 재시도 ({attempt + 1}/{max_retries})", file=sys.stderr)
            time.sleep(wait)

    content = response.choices[0].message["content"].strip()
    if content.startswith("```json"):
        content = content[7:]
    elif content.startswith("```"):
        content = content[3:]
    if content.endswith("```"):
        content = content[:-3]
    return json.loads(content.strip())


def build_dataset(manifest_path: str, out_path: str, sleep_seconds: float = 0.5) -> int:
    with open(manifest_path, encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))

    missing = [c for c in REQUIRED_COLUMNS if c not in (rows[0].keys() if rows else REQUIRED_COLUMNS)]
    if missing:
        print(f"manifest에 필수 컬럼이 없습니다: {missing}", file=sys.stderr)
        sys.exit(1)

    print(f"manifest {len(rows)}행 발견 ({manifest_path})")

    # 이전 실행이 중간에 멈췄어도(속도 제한 등) 이미 라벨링된 사진은 다시 API를 호출하지 않고 이어서 진행한다.
    out_rows = []
    already_done = set()
    if os.path.exists(out_path):
        with open(out_path, encoding="utf-8-sig") as f:
            out_rows = list(csv.DictReader(f))
        already_done = {r["image"] for r in out_rows}
        if already_done:
            print(f"기존 {out_path}에 이미 라벨링된 {len(already_done)}개는 건너뜁니다.")

    for row in rows:
        image_path = Path(row["image"])
        if str(image_path) in already_done:
            continue
        try:
            parsed = label_image(image_path)
        except DailyLimitReached as e:
            done_so_far = {r["image"] for r in out_rows}
            remaining = sum(1 for r in rows if str(Path(r["image"])) not in done_so_far)
            print(f"\n⚠️  OpenAI 하루 요청 한도에 도달했습니다: {e}", file=sys.stderr)
            print(f"나머지 {remaining}장은 한도가 풀린 뒤 같은 명령어로 다시 실행하면 이어서 진행됩니다"
                  f" (이미 라벨링된 {len(done_so_far)}장은 다시 안 부릅니다).", file=sys.stderr)
            break
        except Exception as e:
            print(f"[skip] {image_path.name}: {e}", file=sys.stderr)
            continue

        grade = parsed.get("grade", "판정불가")
        label = GRADE_TO_LABEL.get(grade)
        if label is None:
            print(f"[skip] {image_path.name}: grade='{grade}' (판정불가/알 수 없음, 학습 데이터에서 제외)")
            continue

        out_rows.append({
            "image": str(image_path),
            "brix": row["brix"],
            "hardness": row["hardness"],
            "storageMethod": row["storageMethod"],
            "storageDays": row["storageDays"],
            "amount": row["amount"],
            "label": label,
        })
        print(f"[ok] {image_path.name}: grade={grade} -> label={label}")
        time.sleep(sleep_seconds)  # rate limit 여유

    with open(out_path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["image", "brix", "hardness", "storageMethod", "storageDays", "amount", "label"])
        writer.writeheader()
        writer.writerows(out_rows)

    print(f"\n총 {len(out_rows)}개 라벨링 완료 -> {out_path}")
    counts = {}
    for r in out_rows:
        counts[r["label"]] = counts.get(r["label"], 0) + 1
    print("라벨 분포:", counts)
    return len(out_rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--manifest", default="manifest.csv", help="image,brix,hardness,storageMethod,storageDays,amount 컬럼을 가진 CSV")
    parser.add_argument("--out", default="dataset.csv", help="저장할 dataset.csv 경로")
    parser.add_argument("--sleep", type=float, default=0.5, help="이미지마다 API 호출 사이 대기 시간(초)")
    args = parser.parse_args()

    if not os.getenv("OPENAI_API_KEY"):
        print("OPENAI_API_KEY 환경변수가 필요합니다.", file=sys.stderr)
        sys.exit(1)
    openai.api_key = os.getenv("OPENAI_API_KEY")

    build_dataset(args.manifest, args.out, sleep_seconds=args.sleep)


if __name__ == "__main__":
    main()
