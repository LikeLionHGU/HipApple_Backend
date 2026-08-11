package com.lionapple.storage.dto;

import java.util.List;
import java.util.Map;

public record QualityClassifyResponse(
        Long storageId,
        String label,
        Map<String, Double> probabilities,
        List<FeatureContribution> topFeatures,
        String disclaimer
) {

    private static final String DISCLAIMER =
            "이미지 특징 + 저장고 데이터 기반 실험적 분류 결과이며, 정식 AI 품질 판정(quality-check)을 대체하지 않습니다.";

    public record FeatureContribution(String name, double importance) {
    }

    public static QualityClassifyResponse of(Long storageId, QualityClassifyResult result) {
        List<FeatureContribution> features = result.topFeatures().stream()
                .map(f -> new FeatureContribution(f.name(), f.importance()))
                .toList();
        return new QualityClassifyResponse(storageId, result.label(), result.probabilities(), features, DISCLAIMER);
    }
}
