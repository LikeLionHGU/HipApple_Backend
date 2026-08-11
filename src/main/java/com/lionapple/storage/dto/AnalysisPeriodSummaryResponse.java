package com.lionapple.storage.dto;

public record AnalysisPeriodSummaryResponse(
        AiAnalysisSummary aiAnalysisSummary,
        StorageEnvironmentSummary storageEnvironmentSummary
) {
    public record AiAnalysisSummary(
            int analysisCount,
            int shipmentRecommendationCount,
            int maxPredictedPrice,
            int minPredictedPrice,
            int avgPredictedPrice,
            int priceIncreaseDays,
            int priceDecreaseDays
    ) {}

    public record StorageEnvironmentSummary(
            double avgTemperature,
            int avgHumidity,
            int avgCo2,
            int tempDeviationCount,
            int humidityDeviationCount,
            int co2AnomalyCount,
            int environmentStabilityScore
    ) {}
}
