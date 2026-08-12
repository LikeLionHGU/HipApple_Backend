package com.lionapple.prediction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PricePredictionRepository extends JpaRepository<PricePrediction, Long> {

    boolean existsByCropTypeAndPredictionDate(String cropType, LocalDate predictionDate);

    Optional<PricePrediction> findTopByCropTypeAndPredictionDateLessThanOrderByPredictionDateDesc(
            String cropType, LocalDate predictionDate);

    List<PricePrediction> findByPredictionDateAndActualPriceIsNull(LocalDate predictionDate);

    List<PricePrediction> findByCropTypeAndPredictionDateBetweenOrderByPredictionDateAsc(
            String cropType, LocalDate startDate, LocalDate endDate);
}