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

    public UserService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public LoginResponse googleLogin(GoogleLoginRequest request) {
        return new LoginResponse("jwt-google-demo-token");
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
