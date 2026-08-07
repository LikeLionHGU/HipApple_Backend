package com.lionapple.schedule;

import com.lionapple.common.auth.CurrentUserId;
import com.lionapple.schedule.dto.ScheduleRequest;
import com.lionapple.schedule.dto.ScheduleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    @PostMapping
    public ResponseEntity<Void> createSchedule(
            @CurrentUserId Long userId,
            @Valid @RequestBody ScheduleRequest request) {
        Long scheduleId = scheduleService.createSchedule(userId, request);
        return ResponseEntity.created(URI.create("/api/schedules/" + scheduleId)).build();
    }

    @GetMapping
    public ResponseEntity<List<ScheduleResponse>> getMonthlySchedules(
            @CurrentUserId Long userId,
            @RequestParam int year,
            @RequestParam int month) {
        List<ScheduleResponse> schedules = scheduleService.getMonthlySchedules(userId, year, month);
        return ResponseEntity.ok(schedules);
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(
            @CurrentUserId Long userId,
            @PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(userId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}