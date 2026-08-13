package com.lionapple.price.dto;

import java.util.List;

public class PriceFutureCommentsResponse {
    public List<FutureReport> future_reports;

    public static class FutureReport {
        public String date;
        public String content;
    }
}
