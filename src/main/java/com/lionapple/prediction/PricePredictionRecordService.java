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

    private static final String DEFAULT_CROP_TYPE = "APPLE";

    private final PricePredictionRepository pricePredictionRepository;

    /**
     * AI가 새 분석을 돌렸을 때, 오늘 날짜로 "다음날 예측가"를 기록합니다.
     */
    public void recordTodayAnalysis(ForecastData.Combo combo) {
        recordTodayAnalysis(DEFAULT_CROP_TYPE, combo);
    }

    /**
     * 특정 작물 타입에 대해 오늘 날짜의 "다음날 예측가"를 기록합니다.
     */
    public void recordTodayAnalysis(String cropType, ForecastData.Combo combo) {
        LocalDate today = LocalDate.now();

        // 오늘 이미 해당 작물의 기록이 생성되었으면 중복 저장 방지
        if (pricePredictionRepository.existsByCropTypeAndPredictionDate(cropType, today)) {
            return;
        }

        combo.forecast().stream()
                .filter(point -> point.horizon() == 1) // 다음날(horizon = 1)에 대한 예측값 추출
                .findFirst()
                .ifPresent(point -> pricePredictionRepository.save(
                        PricePrediction.builder()
                                .cropType(cropType)
                                .predictionDate(today)
                                .predictedPrice(point.price())
                                .build()
                ));
    }
}