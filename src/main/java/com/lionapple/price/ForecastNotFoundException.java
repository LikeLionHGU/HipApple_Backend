package com.lionapple.price;

public class ForecastNotFoundException extends RuntimeException {

    public ForecastNotFoundException(String message) {
        super(message);
    }
}
