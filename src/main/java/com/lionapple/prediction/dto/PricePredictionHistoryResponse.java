package com.lionapple.prediction.dto;
import java.util.List;

public record PricePredictionHistoryResponse(
        List<PricePredictionChartPoint> chartPoints,
        List<PricePredictionTableRow> tableRows
) {}