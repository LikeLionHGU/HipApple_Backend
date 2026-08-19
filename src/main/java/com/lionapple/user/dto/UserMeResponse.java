package com.lionapple.user.dto;

import com.lionapple.user.UserAccount;
import com.lionapple.user.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 정보 응답")
public record UserMeResponse(
        @Schema(description = "사용자 ID") Long id,
        @Schema(description = "표시 이름 (농장명이 있으면 농장명, 없으면 구글 계정 이름)") String name,
        @Schema(description = "농장명 (프로필 미등록 시 null)") String farmName
) {
    public static UserMeResponse of(UserAccount account, UserProfile profile) {
        String farmName = (profile != null) ? profile.getFarmName() : null;
        // 농장명이 있으면 농장명을 표시 이름으로, 없으면 구글 계정 이름 사용
        String displayName = (farmName != null && !farmName.isBlank())
                ? farmName
                : account.getName();
        return new UserMeResponse(account.getId(), displayName, farmName);
    }
}
