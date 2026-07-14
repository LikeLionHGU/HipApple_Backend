package com.lionapple.user.dto;

public record GoogleUserInfo(
        String subject,
        String email,
        String name,
        String pictureUrl
) {
}
