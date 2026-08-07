package com.lionapple.prediction;

public enum PredictionPeriod {
    ONE_MONTH(1),
    SIX_MONTHS(6),
    ONE_YEAR(12);

    private final int months;

    PredictionPeriod(int months) {
        this.months = months;
    }

    public int getMonths() {
        return months;
    }
}