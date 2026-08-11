package com.lionapple.prediction;

import com.lionapple.prediction.dto.PricePredictionHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@Tag(name = "Price Prediction", description = "AI 가격 예측 관련 API")
@RestController
@RequestMapping("/api/price-predictions")
@RequiredArgsConstructor
public class PricePredictionController {

    private final PricePredictionService pricePredictionService;
    @Operation(summary = "AI 가격 예측 이력 및 차트 데이터 조회", description = "조회 기간(기본 6개월) 동안의 AI 가격 예측값과 실제 시장가를 조회합니다.")
    @GetMapping
    public PricePredictionHistoryResponse getHistory(
            @Parameter(description = "조회 기간 (기본값: SIX_MONTHS)")
            @RequestParam(defaultValue = "SIX_MONTHS") PredictionPeriod period
    ) {
        return pricePredictionService.getHistory(period);
    }
}