package com.lionapple.price.dto;

import java.util.List;

public class PriceDashboardResponse {
    public String status;
    public SearchInfo search_info;
    public CurrentPriceInfo current_price_info;
    public PriceSummary price_summary;
    public List<ChartData> chart_data;
    public AiMarketAnalysis ai_market_analysis;

    public static class SearchInfo {
        public String formatted_title;
        public String date;
        public String market;
        public String item;
        public String variety;
    }

    public static class CurrentPriceInfo {
        public int price_per_kg;
        public String currency;
        public double change_rate;
        public String change_direction;
    }

    public static class PriceSummary {
        public int today_price;
        public String today_basis_date;
        public int weekly_average_price;
        public String weekly_basis_range;
        public int monthly_average_price;
        public String monthly_basis_range;
    }

    public static class ChartData {
        public String date;
        public int price;
    }

    public static class AiMarketAnalysis {
        public String title;
        public String report_text;
    }

}
