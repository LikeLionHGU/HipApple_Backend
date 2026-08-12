package com.lionapple.prediction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PricePredictionRepository extends JpaRepository<PricePrediction, Long> {

    // 특정 작물의 오늘 날짜 예측 기록이 이미 존재하는지 확인
    boolean existsByCropTypeAndPredictionDate(String cropType, LocalDate predictionDate);

    // 특정 작물의 특정 날짜 이전 가장 최근 예측 기록 1건 조회
    Optional<PricePrediction> findTopByCropTypeAndPredictionDateLessThanOrderByPredictionDateDesc(
            String cropType, LocalDate predictionDate);

    // 실제 가격이 아직 안 채워진(null) 특정 날짜의 예측 기록 목록 조회 (배치용)
    List<PricePrediction> findByPredictionDateAndActualPriceIsNull(LocalDate predictionDate);

    // 특정 작물의 특정 기간 내 예측 데이터 날짜 오름차순 조회 (차트/테이블용)
    List<PricePrediction> findByCropTypeAndPredictionDateBetweenOrderByPredictionDateAsc(
            String cropType, LocalDate startDate, LocalDate endDate);
}