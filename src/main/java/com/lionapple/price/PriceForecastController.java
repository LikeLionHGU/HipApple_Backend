package com.lionapple.price;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static final String DEFAULT_MARKET = "서울가락";
    private static final String DEFAULT_VARIETY = "후지";

    /** 농가 소재지 키워드 -> 대표 도매시장 (구체적인 지역명을 먼저 검사) */
    private static final Map<String, String> REGION_TO_MARKET = new LinkedHashMap<>();

    static {
        REGION_TO_MARKET.put("안동", "안동");
        REGION_TO_MARKET.put("구미", "구미");
        REGION_TO_MARKET.put("포항", "포항");
        REGION_TO_MARKET.put("전주", "전주");
        REGION_TO_MARKET.put("익산", "익산");
        REGION_TO_MARKET.put("순천", "순천");
        REGION_TO_MARKET.put("진주", "진주");
        REGION_TO_MARKET.put("창원", "창원팔용");
        REGION_TO_MARKET.put("청주", "청주");
        REGION_TO_MARKET.put("충주", "충주");
        REGION_TO_MARKET.put("천안", "천안");
        REGION_TO_MARKET.put("원주", "원주");
        REGION_TO_MARKET.put("춘천", "춘천");
        REGION_TO_MARKET.put("수원", "수원");
        REGION_TO_MARKET.put("구리", "구리");
        REGION_TO_MARKET.put("안산", "안산");
        REGION_TO_MARKET.put("안양", "안양");
        REGION_TO_MARKET.put("서울", "서울가락");
        REGION_TO_MARKET.put("인천", "인천남촌");
        REGION_TO_MARKET.put("대구", "대구북부");
        REGION_TO_MARKET.put("부산", "부산반여");
        REGION_TO_MARKET.put("울산", "울산");
        REGION_TO_MARKET.put("광주", "광주서부");
        REGION_TO_MARKET.put("대전", "대전오정");
        REGION_TO_MARKET.put("경북", "안동");
        REGION_TO_MARKET.put("경남", "창원팔용");
        REGION_TO_MARKET.put("전북", "전주");
        REGION_TO_MARKET.put("전남", "순천");
        REGION_TO_MARKET.put("충북", "청주");
        REGION_TO_MARKET.put("충남", "천안");
        REGION_TO_MARKET.put("강원", "원주");
        REGION_TO_MARKET.put("경기", "수원");
    }

    private final ForecastStore forecastStore;
    private final UserProfileRepository userProfileRepository;

    public PriceForecastController(ForecastStore forecastStore, UserProfileRepository userProfileRepository) {
        this.forecastStore = forecastStore;
        this.userProfileRepository = userProfileRepository;
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

        String market = profile.map(p -> mapMarket(p.getFarmLocation())).orElse(DEFAULT_MARKET);
        String variety = profile.map(p -> mapVariety(p.getVariety(), data.varieties())).orElse(DEFAULT_VARIETY);
        String matchedBy = profile.isPresent() ? "user_profile" : "default";

        ForecastData.Combo combo = find(data, market, variety)
                .or(() -> find(data, DEFAULT_MARKET, variety))
                .or(() -> find(data, market, DEFAULT_VARIETY))
                .or(() -> find(data, DEFAULT_MARKET, DEFAULT_VARIETY))
                .or(() -> data.combos().stream().findFirst())
                .orElseThrow(() -> new ForecastNotFoundException("해당 도매시장·품종의 예측 데이터가 없습니다."));
        return ForecastResponse.of(data, combo, matchedBy);
    }

    private Optional<ForecastData.Combo> find(ForecastData data, String market, String variety) {
        return data.combos().stream()
                .filter(c -> c.market().equals(market) && c.variety().equals(variety))
                .findFirst();
    }

    private String mapMarket(String farmLocation) {
        if (farmLocation != null) {
            for (Map.Entry<String, String> entry : REGION_TO_MARKET.entrySet()) {
                if (farmLocation.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return DEFAULT_MARKET;
    }

    private String mapVariety(String raw, List<String> available) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_VARIETY;
        }
        if (raw.contains("부사")) {
            return DEFAULT_VARIETY;  // 부사 = 후지의 한국식 이름
        }
        if (available.contains(raw)) {
            return raw;
        }
        return available.stream()
                .filter(v -> raw.contains(v) || v.contains(raw))
                .findFirst()
                .orElse(DEFAULT_VARIETY);
    }
}
