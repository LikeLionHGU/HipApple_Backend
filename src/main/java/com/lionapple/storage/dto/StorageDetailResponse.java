package com.lionapple.storage.dto;

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
        List<Integer> nearbyDates
) {
}
