package com.lionapple.storage.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record StorageRequest(
        @NotBlank String name,
        @NotBlank String appleType,
        @NotNull LocalDateTime storeDate,
        @NotBlank String storageMethod,
        @Positive int brix,
        @Positive int hardness,
        @NotBlank String condition,
        @PositiveOrZero int amount,
        @NotBlank String preferredDate
) {
}
