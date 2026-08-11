package com.lionapple.storage.dto;

public record ShipmentAnalysisResponse(
        String date,
        int predictedPrice,
        String qualityStatus,
        String event
) {
}
