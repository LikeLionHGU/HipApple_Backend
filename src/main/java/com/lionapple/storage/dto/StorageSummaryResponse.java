package com.lionapple.storage.dto;

import java.time.LocalDate;

import com.lionapple.storage.Storage;

public record StorageSummaryResponse(
        Long storageId,
        int startDate,
        String type,
        String storageMethod,
        int brix
) {

    public static StorageSummaryResponse from(Storage storage) {
        LocalDate date = storage.getStoreDate().toLocalDate();
        int startDate = date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
        return new StorageSummaryResponse(
                storage.getStorageId(),
                startDate,
                storage.getAppleType(),
                storage.getStorageMethod(),
                storage.getBrix()
        );
    }
}
