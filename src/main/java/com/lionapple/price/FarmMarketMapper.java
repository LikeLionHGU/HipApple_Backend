package com.lionapple.price;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FarmMarketMapper {

    public static final String DEFAULT_MARKET = "서울가락";
    public static final String DEFAULT_VARIETY = "후지";

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

    public String mapMarket(String farmLocation) {
        if (farmLocation != null) {
            for (Map.Entry<String, String> entry : REGION_TO_MARKET.entrySet()) {
                if (farmLocation.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return DEFAULT_MARKET;
    }

    public String mapVariety(String raw, List<String> available) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_VARIETY;
        }
        if (raw.contains("부사")) {
            return DEFAULT_VARIETY;
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