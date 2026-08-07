package com.lionapple.schedule.dto;

import com.lionapple.schedule.Schedule;
import java.time.LocalDate;

public record ScheduleResponse(
        Long scheduleId,
        String title,
        LocalDate scheduleDate
) {
    public static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getScheduleId(),
                schedule.getTitle(),
                schedule.getScheduleDate()
        );
    }
}