package com.lionapple.storage.dto;

/** 파이썬 AI 서버의 사진 기반 품질 판정 응답 (POST /api/quality/analyze). */
public record QualityAnalysisResult(
        String grade,
        String ripeness,
        String colorDescription,
        String shipmentComment,
        String confidence
) {
}
