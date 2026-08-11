package com.lionapple.storage.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

/** 파이썬 AI 서버의 이미지+Storage 필드 기반 상/중/하 분류 응답 (POST /api/quality/classify). */
public record QualityClassifyResult(
        String label,
        Map<String, Double> probabilities,
        List<FeatureContribution> topFeatures
) {

    /** 파이썬이 [이름, 중요도] 2요소 배열로 보내는 걸 그대로 매핑. */
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public record FeatureContribution(String name, double importance) {
    }
}
