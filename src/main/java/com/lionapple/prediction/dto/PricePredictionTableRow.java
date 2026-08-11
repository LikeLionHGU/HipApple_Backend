package com.lionapple.prediction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "AI 가격 예측 이력 테이블 행")
public record PricePredictionTableRow(
        @Schema(description = "예측 기준 날짜", example = "2026-07-17")
        LocalDate date,
        @Schema(description = "AI 예측 가격 (원/kg)", example = "4120")
        Integer predictedPrice,
        @Schema(description = "실제 시장 평균 가격 (원/kg, 미집계 시 null)", example = "3980")
        Integer actualPrice,
        @Schema(description = "전일 대비 변동률 (%, 변동 없거나 최초 데이터 시 null)", example = "7.0")
        Double changeRate // null이면 프론트에서 "-" 표시
) {}