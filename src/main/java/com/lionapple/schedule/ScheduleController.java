package com.lionapple.schedule;

import com.lionapple.common.auth.CurrentUserId;
import com.lionapple.schedule.dto.ScheduleRequest;
import com.lionapple.schedule.dto.ScheduleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@Tag(name = "Schedule", description = "농가 일정 관리 API")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping
    @Operation(summary = "일정 등록", description = "새로운 농가 일정을 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "일정 등록 성공"),
            @ApiResponse(responseCode = "400", description = "필수 입력값 누락 또는 형식 오류"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    public ResponseEntity<Void> createSchedule(
            @CurrentUserId Long userId,
            @Valid @RequestBody ScheduleRequest request) {
        Long scheduleId = scheduleService.createSchedule(userId, request);
        return ResponseEntity.created(URI.create("/api/schedules/" + scheduleId)).build();
    }

    @GetMapping
    @Operation(summary = "월별 일정 조회", description = "지정한 연도·월에 해당하는 일정 목록을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    public ResponseEntity<List<ScheduleResponse>> getMonthlySchedules(
            @CurrentUserId Long userId,
            @Parameter(description = "조회 연도 (예: 2025)") @RequestParam int year,
            @Parameter(description = "조회 월 (1~12)") @RequestParam int month) {
        List<ScheduleResponse> schedules = scheduleService.getMonthlySchedules(userId, year, month);
        return ResponseEntity.ok(schedules);
    }

    @DeleteMapping("/{scheduleId}")
    @Operation(summary = "일정 삭제", description = "지정한 일정을 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료"),
            @ApiResponse(responseCode = "404", description = "일정을 찾을 수 없음")
    })
    public ResponseEntity<Void> deleteSchedule(
            @CurrentUserId Long userId,
            @PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(userId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}
