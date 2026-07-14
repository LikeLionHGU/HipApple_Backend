package com.lionapple.user;

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
        UserAccount userAccount = userAccountRepository.findByGoogleSubject(googleUserInfo.subject())
                .orElseGet(() -> new UserAccount(googleUserInfo));
        userAccount.updateLoginInfo(googleUserInfo);
        UserAccount savedUserAccount = userAccountRepository.save(userAccount);
        return new LoginResponse(jwtTokenProvider.createAccessToken(savedUserAccount));
    }

    @Transactional
    public void saveProfile(ProfileRequest request) {
        UserProfile profile = userProfileRepository.findById(1L)
                .orElseGet(() -> new UserProfile(request));
        profile.update(request);
        userProfileRepository.save(profile);
    }

    public UserMeResponse me() {
        return userProfileRepository.findById(1L)
                .map(profile -> new UserMeResponse(profile.getId(), profile.getVariety() + " 농가"))
                .orElseGet(() -> new UserMeResponse(1L, "박주아"));
    }
}
