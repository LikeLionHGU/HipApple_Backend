package com.lionapple.user.dto;
import com.lionapple.user.UserAccount;
import com.lionapple.user.UserProfile;

public record UserMeResponse(
        Long id,
        String name,
        String farmName
) {
    public static UserMeResponse of(UserAccount userAccount, UserProfile userProfile) {
        return new UserMeResponse(
                userAccount.getId(),
                // farmName이 존재하면 farmName을 띄우고, 없으면 기존 구글 name을 기본값으로 사용
                userProfile != null && userProfile.getFarmName() != null
                        ? userProfile.getFarmName()
                        : userAccount.getName(),
                userProfile != null ? userProfile.getFarmName() : null
        );
    }
}
