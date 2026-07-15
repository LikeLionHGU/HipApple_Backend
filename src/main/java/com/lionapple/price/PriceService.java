package com.lionapple.price;
import com.lionapple.price.dto.PriceDashboardResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
@Service
public class PriceService {
    private final String PYTHON_SERVER_URL = "http://localhost:8000/api/price/dashboard";

    public PriceDashboardResponse getMarketDashboardData(String date, String marketCode, String itemCode, String varietyCode) {
        RestTemplate restTemplate = new RestTemplate();
        String targetUrl = UriComponentsBuilder.fromHttpUrl(PYTHON_SERVER_URL)
                .queryParam("date", date)
                .queryParam("market_code", marketCode)
                .queryParam("item_code", itemCode)
                .queryParam("variety_code", varietyCode)
                .toUriString();

        try {
            return restTemplate.getForObject(targetUrl, PriceDashboardResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("파이썬 AI 분석 서버 통신 실패: " + e.getMessage());
        }
    }
}
