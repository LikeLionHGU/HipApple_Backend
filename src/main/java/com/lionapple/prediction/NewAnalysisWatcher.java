package com.lionapple.prediction;

import com.lionapple.price.ForecastStore;
import com.lionapple.price.dto.ForecastData;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NewAnalysisWatcher {

    private static final String DEFAULT_CROP_TYPE = "APPLE";

    private final ForecastStore forecastStore;
    private final PricePredictionRecordService pricePredictionRecordService;

    private volatile String lastProcessedGeneratedAt;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void checkForNewAnalysis() {
        Optional<ForecastData> dataOpt = forecastStore.load();
        if (dataOpt.isEmpty()) return;

        ForecastData data = dataOpt.get();
        if (data.generatedAt().equals(lastProcessedGeneratedAt)) {
            return;
        }

        data.combos().stream()
                .filter(combo -> DEFAULT_CROP_TYPE.equalsIgnoreCase(combo.variety()))
                .findFirst()
                .ifPresent(combo -> pricePredictionRecordService.recordTodayAnalysis(DEFAULT_CROP_TYPE, combo));

        lastProcessedGeneratedAt = data.generatedAt();
    }
}