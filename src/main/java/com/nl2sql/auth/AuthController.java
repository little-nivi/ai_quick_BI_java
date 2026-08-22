package com.nl2sql.auth;

import com.nl2sql.common.ApiResponse;
import com.nl2sql.common.TraceIdHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录接口（REQ-402）。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthService.LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String traceId = TraceIdHolder.getOrCreate();
        return ApiResponse.ok(authService.login(request.username(), request.password()), traceId);
    }

    public record LoginRequest(
            @NotBlank(message = "username 不能为空") String username,
            @NotBlank(message = "password 不能为空") String password) {
    }
}
