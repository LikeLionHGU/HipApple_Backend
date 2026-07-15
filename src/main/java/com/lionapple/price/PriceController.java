package com.lionapple.price;

import com.lionapple.price.dto.PriceDashboardResponse;
import com.lionapple.price.PriceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/price")
@CrossOrigin(origins = "*")
public class PriceController {

    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<PriceDashboardResponse> getDashboard(
            @RequestParam String date,
            @RequestParam String market_code,
            @RequestParam String item_code,
            @RequestParam String variety_code) {

        PriceDashboardResponse response = priceService.getMarketDashboardData(date, market_code, item_code, variety_code);
        return ResponseEntity.ok(response);
    }
}