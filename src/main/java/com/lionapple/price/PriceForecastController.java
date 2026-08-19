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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @Operation(
            summary = "선택 가능한 도매시장·품종 목록",
            description = "예측 데이터가 존재하는 도매시장과 품종의 전체 목록을 반환합니다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public PriceOptionsResponse options() {
        return forecastStore.load()
                .map(data -> new PriceOptionsResponse(data.markets(), data.varieties()))
                .orElseGet(() -> new PriceOptionsResponse(List.of(), List.of()));
    }

    @GetMapping("/forecast")
    @Operation(
            summary = "특정 도매시장·품종의 시세 + 7일 예측",
            description = "지정한 도매시장과 품종의 최근 실제 시세와 향후 7일 AI 예측 가격을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "해당 도매시장·품종의 예측 데이터가 없음")
    })
    public ForecastResponse forecast(
            @Parameter(description = "도매시장 이름 (예: 서울 가락동)", required = true) @RequestParam String market,
            @Parameter(description = "품종 이름 (예: 후지)", required = true) @RequestParam String variety) {
        ForecastData data = forecastStore.loadOrThrow();
        ForecastData.Combo combo = find(data, market, variety)
                .orElseThrow(() -> new ForecastNotFoundException("해당 도매시장·품종의 예측 데이터가 없습니다."));
        return ForecastResponse.of(data, combo, null);
    }

    @GetMapping("/me")
    @Operation(
            summary = "맞춤 시세 예측 (로그인 사용자 기반)",
            description = "로그인한 사용자의 농가 위치·품종 정보를 기반으로 가장 적합한 도매시장과 품종을 자동 매핑하여 시세 예측을 반환합니다. 프로필 미등록 시 기본값을 사용합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료"),
            @ApiResponse(responseCode = "404", description = "예측 데이터가 없음")
    })
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
