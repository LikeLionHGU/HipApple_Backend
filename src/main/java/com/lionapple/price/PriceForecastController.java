package com.lionapple.price;

import java.util.List;
import java.util.Optional;

import com.lionapple.common.auth.CurrentUserId;
import com.lionapple.price.dto.ForecastData;
import com.lionapple.price.dto.ForecastResponse;
import com.lionapple.price.dto.PriceOptionsResponse;
import com.lionapple.user.UserProfile;
import com.lionapple.user.UserProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/price")
@Tag(name = "Price Forecast", description = "시세 조회 및 7일 가격 예측 API")
public class PriceForecastController {

    private final ForecastStore forecastStore;
    private final UserProfileRepository userProfileRepository;
    private final FarmMarketMapper farmMarketMapper;

    public PriceForecastController(ForecastStore forecastStore,
                                   UserProfileRepository userProfileRepository,
                                   FarmMarketMapper farmMarketMapper) {
        this.forecastStore = forecastStore;
        this.userProfileRepository = userProfileRepository;
        this.farmMarketMapper = farmMarketMapper;
    }

    @GetMapping("/options")
    @Operation(summary = "선택 가능한 도매시장·품종 목록")
    public PriceOptionsResponse options() {
        return forecastStore.load()
                .map(data -> new PriceOptionsResponse(data.markets(), data.varieties()))
                .orElseGet(() -> new PriceOptionsResponse(List.of(), List.of()));
    }

    @GetMapping("/forecast")
    @Operation(summary = "특정 도매시장·품종의 최근 시세 + 향후 7일 예측")
    public ForecastResponse forecast(@RequestParam String market, @RequestParam String variety) {
        ForecastData data = forecastStore.loadOrThrow();
        ForecastData.Combo combo = find(data, market, variety)
                .orElseThrow(() -> new ForecastNotFoundException("해당 도매시장·품종의 예측 데이터가 없습니다."));
        return ForecastResponse.of(data, combo, null);
    }

    @GetMapping("/me")
    @Operation(summary = "로그인 농가 정보 기반 맞춤 시세 예측")
    public ForecastResponse myForecast(@CurrentUserId Long userId) {
        ForecastData data = forecastStore.loadOrThrow();
        Optional<UserProfile> profile = userProfileRepository.findByUserId(userId);

        String market = profile.map(p -> farmMarketMapper.mapMarket(p.getFarmLocation()))
                .orElse(FarmMarketMapper.DEFAULT_MARKET);
        String variety = profile.map(p -> farmMarketMapper.mapVariety(p.getVariety(), data.varieties()))
                .orElse(FarmMarketMapper.DEFAULT_VARIETY);
        String matchedBy = profile.isPresent() ? "user_profile" : "default";

        ForecastData.Combo combo = find(data, market, variety)
                .or(() -> find(data, FarmMarketMapper.DEFAULT_MARKET, variety))
                .or(() -> find(data, market, FarmMarketMapper.DEFAULT_VARIETY))
                .or(() -> find(data, FarmMarketMapper.DEFAULT_MARKET, FarmMarketMapper.DEFAULT_VARIETY))
                .or(() -> data.combos().stream().findFirst())
                .orElseThrow(() -> new ForecastNotFoundException("해당 도매시장·품종의 예측 데이터가 없습니다."));
        return ForecastResponse.of(data, combo, matchedBy);
    }

    private Optional<ForecastData.Combo> find(ForecastData data, String market, String variety) {
        return data.combos().stream()
                .filter(c -> c.market().equals(market) && c.variety().equals(variety))
                .findFirst();
    }
}