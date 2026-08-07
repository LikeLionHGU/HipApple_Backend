package com.lionapple.prediction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PricePredictionRepository extends JpaRepository<PricePrediction, Long> {

    boolean existsByUserIdAndCropTypeAndPredictionDate(Long userId, String cropType, LocalDate predictionDate);

    Optional<PricePrediction> findTopByUserIdAndCropTypeAndPredictionDateLessThanOrderByPredictionDateDesc(
            Long userId, String cropType, LocalDate predictionDate);

    List<PricePrediction> findByPredictionDateAndActualPriceIsNull(LocalDate predictionDate);

    List<PricePrediction> findByUserIdAndCropTypeAndPredictionDateBetweenOrderByPredictionDateAsc(
            Long userId, String cropType, LocalDate startDate, LocalDate endDate);
}