package com.lionapple.storage.dto;

import com.lionapple.storage.Storage;

import java.time.LocalDateTime;
import java.util.List;

public record StorageDetailResponse(
        Long storageId,
        String name,
        String type,
        int startDate,
        LocalDateTime storeDate,
        String storageMethod,
        int brix,
        int hardness,
        String condition,
        int amount,
        String preferredDate,
        long storagePeriodDays,
        int temperature,
        int humidity,
        double ethylene,
        String qualityStatus,
        String shipmentRecommendation,
        String analysisReason,
        String priceRecommendationReason,
        List<ShipmentAnalysisResponse> shipmentAnalyses,
        String qualityGrade,
        String qualityRipeness,
        String qualityColorDescription,
        String qualityShipmentComment,
        String qualityConfidence,
        LocalDateTime qualityCheckedAt
) {
    public static StorageDetailResponse of(
            Storage storage,
            int temperature,
            int humidity,
            double ethylene,
            String qualityStatus,
            String shipmentRecommendation,
            String analysisReason,
            String priceRecommendationReason,
            List<ShipmentAnalysisResponse> shipmentAnalyses
    ){
        int startDate = (storage.getAnalysisStartDate() != null)
                ? storage.getAnalysisStartDate().getDayOfMonth()
                : 0;

        return new StorageDetailResponse(
                storage.getStorageId(),
                storage.getName(),
                storage.getAppleType(),
                startDate,
                storage.getStoreDate(),
                storage.getStorageMethod(),
                storage.getBrix(),
                storage.getHardness(),
                storage.getCondition(),
                storage.getAmount(),
                storage.getPreferredDate(),
                storage.getStoragePeriodDays(),
                temperature,
                humidity,
                ethylene,
                qualityStatus,
                shipmentRecommendation,
                analysisReason,
                priceRecommendationReason,
                shipmentAnalyses,
                storage.getQualityGrade(),
                storage.getQualityRipeness(),
                storage.getQualityColorDescription(),
                storage.getQualityShipmentComment(),
                storage.getQualityConfidence(),
                storage.getQualityCheckedAt()
        );
    }

}
