package com.lionapple.price.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 파이썬 배치가 생성한 forecasts.json 파일 구조. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ForecastData(
        String generatedAt,
        List<String> markets,
        List<String> varieties,
        List<Combo> combos
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Combo(
            String market,
            String variety,
            String asOf,
            List<HistoryPoint> history,
            List<ForecastPoint> forecast
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HistoryPoint(String date, int price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForecastPoint(String date, int price, int low, int high, int horizon) {
    }
}
