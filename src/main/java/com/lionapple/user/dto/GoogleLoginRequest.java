package com.lionapple.user.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank String idToken
) {
}
