package com.lionapple.prediction;

import com.lionapple.common.auth.CurrentUserId;
import com.lionapple.prediction.dto.PricePredictionHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PricePredictionController {

    private final PricePredictionService pricePredictionService;

    @GetMapping("/api/price-predictions")
    public PricePredictionHistoryResponse getHistory(
            @CurrentUserId Long userId,
            @RequestParam String cropType,
            @RequestParam(defaultValue = "SIX_MONTHS") PredictionPeriod period
    ) {
        return pricePredictionService.getHistory(userId, cropType, period);
    }
}