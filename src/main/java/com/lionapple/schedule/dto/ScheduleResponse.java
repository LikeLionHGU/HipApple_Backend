package com.lionapple.schedule.dto;

import com.lionapple.schedule.Schedule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "일정 응답")
public record ScheduleResponse(
        @Schema(description = "일정 ID") Long scheduleId,
        @Schema(description = "일정 제목") String title,
        @Schema(description = "일정 날짜 (yyyy-MM-dd)") LocalDate scheduleDate
) {
    public static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getScheduleId(),
                schedule.getTitle(),
                schedule.getScheduleDate()
        );
    }
}
