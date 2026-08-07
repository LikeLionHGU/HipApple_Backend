package com.lionapple.prediction;

import com.lionapple.price.dto.ForecastData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class PricePredictionRecordService {

    private final PricePredictionRepository pricePredictionRepository;

    /** AI가 새 분석을 돌렸을 때, 오늘 날짜로 "다음날 예측가"를 기록 */
    public void recordTodayAnalysis(Long userId, String cropType, ForecastData.Combo combo) {
        LocalDate today = LocalDate.now();

        if (pricePredictionRepository.existsByUserIdAndCropTypeAndPredictionDate(userId, cropType, today)) {
            return; // 오늘 이미 기록했으면 스킵
        }

        combo.forecast().stream()
                .filter(point -> point.horizon() == 1) // 다음날에 대한 예측
                .findFirst()
                .ifPresent(point -> pricePredictionRepository.save(
                        PricePrediction.builder()
                                .userId(userId)
                                .cropType(cropType)
                                .predictionDate(today)
                                .predictedPrice(point.price())
                                .build()
                ));
    }
}