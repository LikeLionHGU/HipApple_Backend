package com.lionapple.storage.dto;

import java.time.LocalDate;

import com.lionapple.storage.Storage;

public record StorageSummaryResponse(
        Long storageId,
        String name,
        int startDate,
        String type,
        String storageMethod,
        int brix,
        LocalDate analysisStartDate, // 분석 시작일 (UI의 2026.7.1~ 표시용)
        long storagePeriodDays       // 경과일
) {

    public static StorageSummaryResponse from(Storage storage) {
        LocalDate date = storage.getStoreDate().toLocalDate();
        int startDate = date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
        return new StorageSummaryResponse(
                storage.getStorageId(),
                storage.getName(),
                startDate,
                storage.getAppleType(),
                storage.getStorageMethod(),
                storage.getBrix(),
                storage.getAnalysisStartDate(),
                storage.getStoragePeriodDays()
        );
    }
}
