package com.lionapple.user.dto;

public record LoginResponse(
        String accessToken,
        boolean isNewUser
) {
}
