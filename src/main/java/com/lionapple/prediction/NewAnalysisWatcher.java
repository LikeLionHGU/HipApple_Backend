package com.lionapple.prediction;

import com.lionapple.price.FarmMarketMapper;
import com.lionapple.price.ForecastStore;
import com.lionapple.price.dto.ForecastData;
import com.lionapple.user.UserProfile;
import com.lionapple.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NewAnalysisWatcher {

    private final ForecastStore forecastStore;
    private final UserProfileRepository userProfileRepository;
    private final PricePredictionRecordService pricePredictionRecordService;
    private final FarmMarketMapper farmMarketMapper;

    private volatile String lastProcessedGeneratedAt;

    // 5분마다 forecasts.json이 새로 갱신됐는지 확인
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void checkForNewAnalysis() {
        Optional<ForecastData> dataOpt = forecastStore.load();
        if (dataOpt.isEmpty()) return;

        ForecastData data = dataOpt.get();
        if (data.generatedAt().equals(lastProcessedGeneratedAt)) {
            return; // 새 분석 아님
        }

        for (UserProfile profile : userProfileRepository.findAll()) {
            String market = farmMarketMapper.mapMarket(profile.getFarmLocation());
            String variety = farmMarketMapper.mapVariety(profile.getVariety(), data.varieties());

            data.combos().stream()
                    .filter(c -> c.market().equals(market) && c.variety().equals(variety))
                    .findFirst()
                    .ifPresent(combo -> pricePredictionRecordService.recordTodayAnalysis(
                            profile.getUserId(), variety, combo));
        }

        lastProcessedGeneratedAt = data.generatedAt();
    }
}