package com.lionapple.storage.dto;

import java.util.List;

public record QualityStorageStatusResponse(
        String status,
        String title,
        StorageEnvironment storageEnvironment,
        List<QualityTrendPoint> trendData,
        CurrentMetrics currentMetrics
) {
    // 1. 상단 저장고 현재 저장 현황 (온도/습도/에틸렌/저장기간)
    public record StorageEnvironment(
            String storageName,        // 예: "A동"
            double temperature,        // 2.0 (°C)
            double humidity,           // 90.0 (%)
            double ethylene,           // 0.3 (ppm)
            long storageDays,          // DB storeDate 기반 계산값 (23일 등)
            String lastUpdated         // 마지막 측정 날짜 (YYYY.M.D)
    ) {}

    // 2. 품질 점수 변화 추이 차트 포인트
    public record QualityTrendPoint(
            String date,               // MM.DD
            double score               // 품질 점수
    ) {}

    // 3. 우측 품질 정보 카드
    public record CurrentMetrics(
            String grade,              // 품질 등급 (예: "우수")
            int score,                 // 현재 품질 점수 (91)
            int maxScore,              // 최대 점수 (100)
            int estimatedStorageDays,  // 예상 저장 가능 기간 (18일)
            String degradationSpeed    // 품질 저하 속도 ("보통")
    ) {}
}