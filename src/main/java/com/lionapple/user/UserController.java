package com.lionapple.user;

import com.lionapple.common.ApiResult;
import com.lionapple.common.auth.CurrentUserId;
import com.lionapple.user.dto.GoogleLoginRequest;
import com.lionapple.user.dto.LoginResponse;
import com.lionapple.user.dto.ProfileRequest;
import com.lionapple.user.dto.UserMeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@Tag(name = "User", description = "사용자 API")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/google")
    @Operation(summary = "구글 로그인")
    public LoginResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return userService.googleLogin(request);
    }

    @PostMapping("/profile")
    @Operation(summary = "농가 정보 입력")
    public ApiResult saveProfile(@CurrentUserId Long userId, @Valid @RequestBody ProfileRequest request) {
        userService.saveProfile(userId, request);
        return ApiResult.success();
    }

    @GetMapping("/me")
    @Operation(summary = "사용자 정보 조회")
    public UserMeResponse me(@CurrentUserId Long userId) {
        return userService.me(userId);
    }
}
