package com.lionapple.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ProfileRequest(
        @NotBlank String farmName,
        @NotBlank String variety,
        @Positive int farmSize,
        @NotBlank String farmSizeUnit,
        @NotBlank String shipmentType,
        String farmLocation
) {
}
