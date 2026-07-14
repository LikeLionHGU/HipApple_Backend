package com.lionapple.user;

import com.lionapple.user.dto.GoogleUserInfo;

public interface GoogleTokenVerifier {

    GoogleUserInfo verify(String idToken);
}
