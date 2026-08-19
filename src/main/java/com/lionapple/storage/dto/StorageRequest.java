package com.lionapple.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;

@Schema(description = "저장고 등록/수정 요청")
public record StorageRequest(
        @Schema(description = "저장고 이름", example = "1번 창고")
        @NotBlank String name,

        @Schema(description = "사과 품종", example = "부사")
        @NotBlank String appleType,

        @Schema(description = "입고 날짜·시각 (ISO 8601)", example = "2025-10-01T09:00:00")
        @NotNull LocalDateTime storeDate,

        @Schema(description = "보관 방식 (예: 일반냉장, CA저장)", example = "CA저장")
        @NotBlank String storageMethod,

        @Schema(description = "당도 (Brix, 양의 정수)", example = "14")
        @Positive int brix,

        @Schema(description = "경도 (양의 정수)", example = "18")
        @Positive int hardness,

        @Schema(description = "사과 상태 메모", example = "외관 양호")
        @NotBlank String condition,

        @Schema(description = "보관 수량 (0 이상)", example = "500")
        @PositiveOrZero int amount,

        @Schema(description = "희망 출하 시기 (자유 형식 텍스트)", example = "2026-01")
        @NotBlank String preferredDate
) {
}
