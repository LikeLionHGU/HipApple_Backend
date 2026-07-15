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
    public void saveProfile(Long userId, ProfileRequest request) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> new UserProfile(userId, request));
        profile.update(request);
        userProfileRepository.save(profile);
    }

    public UserMeResponse me(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .map(profile -> new UserMeResponse(userId, profile.getVariety() + " 농가"))
                .orElseGet(() -> userAccountRepository.findById(userId)
                        .map(account -> new UserMeResponse(userId, account.getName()))
                        .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다.")));
    }
}
