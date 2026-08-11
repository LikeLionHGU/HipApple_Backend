package com.lionapple.prediction;

import com.lionapple.prediction.dto.PricePredictionChartPoint;
import com.lionapple.prediction.dto.PricePredictionHistoryResponse;
import com.lionapple.prediction.dto.PricePredictionTableRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PricePredictionService {

    private static final int TABLE_ROW_LIMIT = 5;
    private static final String DEFAULT_CROP_TYPE = "APPLE";

    private final PricePredictionRepository pricePredictionRepository;

    public PricePredictionHistoryResponse getHistory(PredictionPeriod period) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(period.getMonths());

        List<PricePrediction> predictions = pricePredictionRepository
                .findByCropTypeAndPredictionDateBetweenOrderByPredictionDateAsc(
                        DEFAULT_CROP_TYPE, startDate, endDate);

        List<PricePredictionChartPoint> chartPoints = predictions.stream()
                .map(p -> new PricePredictionChartPoint(
                        p.getPredictionDate(), p.getPredictedPrice(), p.getActualPrice()))
                .toList();

        List<PricePredictionTableRow> tableRows = buildTableRows(predictions);

        return new PricePredictionHistoryResponse(chartPoints, tableRows);
    }

    private List<PricePredictionTableRow> buildTableRows(List<PricePrediction> predictions) {
        List<PricePredictionTableRow> rows = new ArrayList<>();

        for (int i = 0; i < predictions.size(); i++) {
            PricePrediction current = predictions.get(i);
            Double changeRate = null;

            if (i > 0) {
                Integer prevPrice = predictions.get(i - 1).getPredictedPrice();
                Integer currPrice = current.getPredictedPrice();
                changeRate = calculateChangeRate(prevPrice, currPrice);
            }

            rows.add(new PricePredictionTableRow(
                    current.getPredictionDate(),
                    current.getPredictedPrice(),
                    current.getActualPrice(),
                    changeRate
            ));
        }

        int fromIndex = Math.max(0, rows.size() - TABLE_ROW_LIMIT);
        return rows.subList(fromIndex, rows.size());
    }

    private Double calculateChangeRate(Integer prevPrice, Integer currPrice) {
        if (prevPrice == null || prevPrice == 0) {
            return null;
        }
        double rate = ((double) (currPrice - prevPrice) / prevPrice) * 100;
        return Math.round(rate * 10) / 10.0;
    }
}