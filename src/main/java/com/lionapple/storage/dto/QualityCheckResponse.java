package com.lionapple.storage.dto;

import java.time.LocalDateTime;

public record QualityCheckResponse(
        Long storageId,
        LocalDateTime checkedAt,
        String grade,
        String ripeness,
        String colorDescription,
        String shipmentComment,
        String confidence,
        String disclaimer
) {

    private static final String DISCLAIMER =
            "AI 육안 판정 참고용 결과이며, 실측 당도·경도 값을 대체하지 않습니다.";

    public static QualityCheckResponse of(Long storageId, LocalDateTime checkedAt, QualityAnalysisResult result) {
        return new QualityCheckResponse(
                storageId,
                checkedAt,
                result.grade(),
                result.ripeness(),
                result.colorDescription(),
                result.shipmentComment(),
                result.confidence(),
                DISCLAIMER
        );
    }
}
