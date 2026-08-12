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

    public void recordTodayAnalysis(ForecastData.Combo combo) {
        recordTodayAnalysis(DEFAULT_CROP_TYPE, combo);
    }

    public void recordTodayAnalysis(String cropType, ForecastData.Combo combo) {
        LocalDate today = LocalDate.now();

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