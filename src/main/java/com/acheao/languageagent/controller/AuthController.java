package com.acheao.languageagent.controller;

import com.acheao.languageagent.dto.req.LoginReq;
import com.acheao.languageagent.dto.req.RegisterReq;
import com.acheao.languageagent.dto.res.AuthRes;
import com.acheao.languageagent.exception.Result;
import com.acheao.languageagent.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User authentication APIs")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public Result<AuthRes> register(@Valid @RequestBody RegisterReq request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login an existing user")
    public Result<AuthRes> login(@Valid @RequestBody LoginReq request) {
        return Result.success(authService.login(request));
    }
}
