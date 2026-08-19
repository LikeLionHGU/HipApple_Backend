package com.lionapple.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "일정 등록 요청")
public record ScheduleRequest(
        @Schema(description = "일정 제목", example = "사과 수확")
        @NotBlank String title,

        @Schema(description = "일정 날짜 (yyyy-MM-dd)", example = "2025-10-15")
        @NotNull LocalDate scheduleDate
) {
}
