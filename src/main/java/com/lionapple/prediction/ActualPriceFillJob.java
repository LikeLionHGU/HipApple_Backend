package com.lionapple.prediction;

import com.lionapple.price.ForecastStore;
import com.lionapple.price.dto.ForecastData;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ActualPriceFillJob {

    private final ForecastStore forecastStore;
    private final PricePredictionRepository pricePredictionRepository;

    // 매일 새벽 1시 실행
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void fillActualPrices() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // 어제 저장된, 아직 실제값이 안 채워진 row들 (그 예측은 "오늘"에 대한 예측이었음)
        List<PricePrediction> targets = pricePredictionRepository
                .findByPredictionDateAndActualPriceIsNull(yesterday);
        if (targets.isEmpty()) return;

        ForecastData data = forecastStore.loadOrThrow();
        String todayStr = today.format(DateTimeFormatter.ISO_DATE);

        for (PricePrediction prediction : targets) {
            data.combos().stream()
                    .filter(c -> matchesCropType(c, prediction.getCropType()))
                    .findFirst()
                    .flatMap(combo -> combo.history().stream()
                            .filter(h -> h.date().equals(todayStr))
                            .findFirst())
                    .ifPresent(historyPoint -> prediction.fillActualPrice(historyPoint.price()));
        }
    }

    private boolean matchesCropType(ForecastData.Combo combo, String cropType) {
        return combo.variety().equals(cropType);
    }
}