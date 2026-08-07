package com.lionapple.storage.dto;

import java.time.LocalDate;

public record MajorScheduleResponse(
        String title,
        LocalDate date,
        String eventType
) {
}