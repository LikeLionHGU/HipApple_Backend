package com.lionapple.prediction.dto;

import java.time.LocalDate;

public record PricePredictionChartPoint(
        LocalDate date,
        Integer predictedPrice,
        Integer actualPrice
) {}