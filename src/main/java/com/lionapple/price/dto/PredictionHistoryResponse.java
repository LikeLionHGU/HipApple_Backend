package com.lionapple.price.dto;

public record PredictionHistoryResponse(
        String date,
        int predictedPrice,
        int actualPrice,
        double changeRate
) {
}
