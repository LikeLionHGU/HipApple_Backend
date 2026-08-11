package com.lionapple.prediction;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
@Entity
@Table(
        name = "price_predictions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cropType", "predictionDate"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PricePrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String cropType;

    // AI가 분석을 실행한 날짜 (표/차트에 보이는 날짜)
    @Column(nullable = false)
    private LocalDate predictionDate;

    // predictionDate 다음날에 대한 예측가
    @Column(nullable = false)
    private Integer predictedPrice;

    // predictionDate 다음날의 실제가 (다음날 배치가 채움)
    private Integer actualPrice;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public PricePrediction(String cropType, LocalDate predictionDate, Integer predictedPrice) {
        this.cropType = cropType;
        this.predictionDate = predictionDate;
        this.predictedPrice = predictedPrice;
        this.createdAt = LocalDateTime.now();
    }

    public void fillActualPrice(Integer actualPrice) {
        this.actualPrice = actualPrice;
    }
}