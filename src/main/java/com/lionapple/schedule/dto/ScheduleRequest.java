package com.lionapple.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ScheduleRequest(
        @NotBlank String title,
        @NotNull LocalDate scheduleDate
) {
}