package com.apollosuny.apolledgebe.auth.controller;

import com.apollosuny.apolledgebe.auth.dto.LoginRequest;
import com.apollosuny.apolledgebe.auth.dto.RegisterRequest;
import com.apollosuny.apolledgebe.auth.dto.TokenResponse;
import com.apollosuny.apolledgebe.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix}/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/local")
    public TokenResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @PostMapping("/local/register")
    public TokenResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return authService.register(request);
    }
}
