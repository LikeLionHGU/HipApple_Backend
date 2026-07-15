package com.lionapple.price.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ForecastResponse(
        String market,
        String variety,
        @JsonInclude(JsonInclude.Include.NON_NULL) String matchedBy,
        String unit,
        String asOf,
        String generatedAt,
        List<ForecastData.HistoryPoint> history,
        List<ForecastData.ForecastPoint> forecast
) {

    public static ForecastResponse of(ForecastData data, ForecastData.Combo combo, String matchedBy) {
        return new ForecastResponse(
                combo.market(),
                combo.variety(),
                matchedBy,
                "원/kg",
                combo.asOf(),
                data.generatedAt(),
                combo.history(),
                combo.forecast()
        );
    }
}
