package com.lionapple.prediction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "AI 가격 예측 차트 포인트")
public record PricePredictionChartPoint(
        @Schema(description = "예측 기준 날짜", example = "2026-07-17")
        LocalDate date,
        @Schema(description = "AI 예측 가격 (원/kg)", example = "4120")
        Integer predictedPrice,
        @Schema(description = "실제 시장 평균 가격 (원/kg, 미집계 시 null)", example = "3980")
        Integer actualPrice

) {}