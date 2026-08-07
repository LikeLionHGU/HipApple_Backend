package com.lionapple.prediction.dto;

import java.time.LocalDate;

public record PricePredictionTableRow(
        LocalDate date,
        Integer predictedPrice,
        Integer actualPrice,
        Double changeRate // null이면 프론트에서 "-" 표시
) {}