package com.lionapple.price;

import com.lionapple.price.dto.PriceDashboardResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PriceService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String aiServerUrl;

    public PriceService(@Value("${app.ai-server.url}") String aiServerUrl) {
        this.aiServerUrl = aiServerUrl;
    }

    public PriceDashboardResponse getMarketDashboardData(String date, String marketCode, String itemCode, String varietyCode) {
        String targetUrl = UriComponentsBuilder.fromHttpUrl(aiServerUrl + "/api/price/dashboard")
                .queryParam("date", date)
                .queryParam("market_code", marketCode)
                .queryParam("item_code", itemCode)
                .queryParam("variety_code", varietyCode)
                .toUriString();

        try {
            return restTemplate.getForObject(targetUrl, PriceDashboardResponse.class);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "파이썬 AI 분석 서버 통신 실패: " + e.getMessage());
        }
    }
}
