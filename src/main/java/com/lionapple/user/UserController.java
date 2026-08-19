package com.lionapple.user;

import com.lionapple.common.ApiResult;
import com.lionapple.common.auth.CurrentUserId;
import com.lionapple.user.dto.GoogleLoginRequest;
import com.lionapple.user.dto.LoginResponse;
import com.lionapple.user.dto.ProfileRequest;
import com.lionapple.user.dto.UserMeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@Tag(name = "User", description = "사용자 인증 및 프로필 API")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/google")
    @Operation(summary = "구글 로그인", description = "구글 ID 토큰을 검증하고 JWT 액세스 토큰을 발급합니다. 신규 사용자 여부(isNewUser)를 함께 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 구글 토큰")
    })
    public LoginResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return userService.googleLogin(request);
    }

    @PostMapping("/test-login")
    @Operation(summary = "테스트용 로그인 (스웨거 전용)", description = "스웨거에서 JWT 없이 테스트할 수 있는 임시 로그인입니다. 고정된 테스트 계정으로 토큰을 발급합니다.")
    @ApiResponse(responseCode = "200", description = "테스트 토큰 발급 성공")
    public LoginResponse testLogin() {
        return userService.testLogin();
    }

    @PostMapping("/profile")
    @Operation(summary = "농가 정보 입력/수정", description = "로그인한 사용자의 농가 정보를 저장합니다. 최초 입력 및 수정 모두 이 API를 사용합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "필수 입력값 누락 또는 형식 오류"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    public ApiResult saveProfile(@CurrentUserId Long userId, @Valid @RequestBody ProfileRequest request) {
        userService.saveProfile(userId, request);
        return ApiResult.success();
    }

    @GetMapping("/me")
    @Operation(summary = "사용자 정보 조회", description = "로그인한 사용자의 ID, 표시 이름, 농장명을 반환합니다. 프로필 미등록 시 farmName은 null입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    public UserMeResponse me(@CurrentUserId Long userId) {
        return userService.me(userId);
    }
}
