package com.lionapple.price;

import com.lionapple.price.dto.PriceDashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/price")
@Tag(name = "Price", description = "시장가격 조회 및 AI 분석 API")
public class PriceController {

    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "시장가격 대시보드 조회")
    public ResponseEntity<PriceDashboardResponse> getDashboard(
            @RequestParam String date,
            @RequestParam String market_code,
            @RequestParam String item_code,
            @RequestParam String variety_code) {

        PriceDashboardResponse response = priceService.getMarketDashboardData(date, market_code, item_code, variety_code);
        return ResponseEntity.ok(response);
    }
}
