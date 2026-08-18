package com.lionapple.user;

import java.util.NoSuchElementException;

import com.lionapple.user.dto.GoogleLoginRequest;
import com.lionapple.user.dto.LoginResponse;
import com.lionapple.user.dto.ProfileRequest;
import com.lionapple.user.dto.UserMeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final UserAccountRepository userAccountRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtTokenProvider jwtTokenProvider;

    public UserService(
            UserProfileRepository userProfileRepository,
            UserAccountRepository userAccountRepository,
            GoogleTokenVerifier googleTokenVerifier,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userProfileRepository = userProfileRepository;
        this.userAccountRepository = userAccountRepository;
        this.googleTokenVerifier = googleTokenVerifier;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public LoginResponse googleLogin(GoogleLoginRequest request) {
        var googleUserInfo = googleTokenVerifier.verify(request.idToken());
        var existingAccount = userAccountRepository.findByGoogleSubject(googleUserInfo.subject());
        boolean isNewUser = existingAccount.isEmpty();
        UserAccount userAccount = existingAccount.orElseGet(() -> new UserAccount(googleUserInfo));
        userAccount.updateLoginInfo(googleUserInfo);
        UserAccount savedUserAccount = userAccountRepository.save(userAccount);
        return new LoginResponse(jwtTokenProvider.createAccessToken(savedUserAccount), isNewUser);
    }

    @Transactional
    public LoginResponse testLogin() {
        var existingAccount = userAccountRepository.findByGoogleSubject("test-google-id");
        boolean isNewUser = existingAccount.isEmpty();
        UserAccount userAccount = existingAccount.orElseGet(() -> {
            UserAccount newUser = new UserAccount(new com.lionapple.user.dto.GoogleUserInfo(
                    "test-google-id", "test@example.com", "테스트유저", "https://example.com/avatar.png"));
            return userAccountRepository.save(newUser);
        });
        return new LoginResponse(jwtTokenProvider.createAccessToken(userAccount), isNewUser);
    }

    @Transactional
    public void saveProfile(Long userId, ProfileRequest request) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> new UserProfile(userId, request));
        profile.update(request); // UserProfile.java 내부의 update() 메서드에서도 farmName을 변경하도록 처리 필요
        userProfileRepository.save(profile);
    }

    public UserMeResponse me(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .map(profile -> {
                    // 1. DB에 farmName이 존재하고 비어있지 않으면 사용자가 입력한 값 그대로 사용
                    // 2. 만약 입력된 farmName이 없으면 품종 이름(variety)만 사용 (또는 account.getName())
                    String displayName = (profile.getFarmName() != null && !profile.getFarmName().isBlank())
                            ? profile.getFarmName()
                            : profile.getVariety();

                    return new UserMeResponse(userId, displayName, profile.getFarmName());
                })
                .orElseGet(() -> userAccountRepository.findById(userId)
                        .map(account -> new UserMeResponse(userId, account.getName(), null))
                        .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다.")));
    }
}