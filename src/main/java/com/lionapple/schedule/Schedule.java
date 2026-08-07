package com.lionapple.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long scheduleId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDate scheduleDate;


    public Schedule(Long userId, String title, LocalDate scheduleDate) {
        this.userId = userId;
        this.title = title;
        this.scheduleDate = scheduleDate;
    }

    public void update(String title, LocalDate scheduleDate) {
        this.title = title;
        this.scheduleDate = scheduleDate;
    }
}