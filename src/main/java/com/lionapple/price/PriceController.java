package com.lionapple.price;

import com.lionapple.price.dto.PriceDashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @Operation(
            summary = "시장가격 대시보드 조회",
            description = "특정 날짜·도매시장·품목·품종의 시장가격 데이터를 조회합니다. AI 서버에서 데이터를 가져옵니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "파라미터 누락 또는 형식 오류"),
            @ApiResponse(responseCode = "503", description = "AI 서버 응답 없음")
    })
    public ResponseEntity<PriceDashboardResponse> getDashboard(
            @Parameter(description = "조회 날짜 (예: 2025-01-15)", required = true) @RequestParam String date,
            @Parameter(description = "도매시장 코드 (예: 110001)", required = true) @RequestParam String market_code,
            @Parameter(description = "품목 코드 (예: 111)", required = true) @RequestParam String item_code,
            @Parameter(description = "품종 코드 (예: 00)", required = true) @RequestParam String variety_code) {

        PriceDashboardResponse response = priceService.getMarketDashboardData(date, market_code, item_code, variety_code);
        return ResponseEntity.ok(response);
    }
}
