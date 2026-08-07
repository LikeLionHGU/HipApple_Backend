package com.lionapple.schedule;

import com.lionapple.schedule.dto.ScheduleRequest;
import com.lionapple.schedule.dto.ScheduleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;

    @Transactional
    public Long createSchedule(Long userId, ScheduleRequest request) {
        Schedule schedule = new Schedule(
                userId,
                request.title(),
                request.scheduleDate()
        );
        return scheduleRepository.save(schedule).getScheduleId();
    }

    //해당 월 전체 반환
    public List<ScheduleResponse> getMonthlySchedules(Long userId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<Schedule> schedules = scheduleRepository.findByUserIdAndDateBetween(userId, startDate, endDate);
        return schedules.stream()
                .map(ScheduleResponse::from)
                .toList();
    }

    @Transactional
    public void deleteSchedule(Long userId, Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 일정입니다."));

        if (!schedule.getUserId().equals(userId)) {
            throw new IllegalArgumentException("삭제 권한이 없습니다.");
        }
        scheduleRepository.delete(schedule);
    }
}