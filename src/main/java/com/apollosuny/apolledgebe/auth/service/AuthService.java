package com.apollosuny.apolledgebe.auth.service;

import com.apollosuny.apolledgebe.auth.dto.LoginRequest;
import com.apollosuny.apolledgebe.auth.dto.RegisterRequest;
import com.apollosuny.apolledgebe.auth.dto.TokenRefreshResponse;
import com.apollosuny.apolledgebe.auth.dto.TokenResponse;
import com.apollosuny.apolledgebe.auth.security.JwtService;
import com.apollosuny.apolledgebe.user.entity.User;
import com.apollosuny.apolledgebe.user.entity.UserProvider;
import com.apollosuny.apolledgebe.user.mapper.UserMapper;
import com.apollosuny.apolledgebe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final UserRepository userRepository;

    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username().trim().toLowerCase(),
                        request.password()
                        )
        );
        UserDetails principal = (UserDetails) authentication.getPrincipal();

        User user = userRepository.findByUsernameAndProvider(principal.getUsername(), UserProvider.LOCAL)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return issueToken(user);
    }

    public TokenResponse register(RegisterRequest request) {
        String username = request.username()
                .trim()
                .toLowerCase();
        boolean exists = userRepository
                .existsByUsernameAndProvider(
                        username,
                        UserProvider.LOCAL
                );

        if (exists) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = User.builder()
                .username(username)
                .email(request.email().trim().toLowerCase())
                .provider(UserProvider.LOCAL)
                .password(passwordEncoder.encode(request.password()))
                .jwtValidFrom(Instant.now())
                .build();

        userRepository.save(user);

        return issueToken(user);
    }

    public TokenRefreshResponse refresh(String refreshToken) {
        if (!jwtService.isRefreshTokenValid(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        UUID userId = jwtService.extractUserIdFromRefreshToken(refreshToken);

        String newAccessToken = jwtService.generateAccessToken(userId);

        return new TokenRefreshResponse(newAccessToken);
    }

    private TokenResponse issueToken(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId());

        String refreshToken = jwtService.generateRefreshToken(user.getId());

        return new TokenResponse(
                accessToken,
                refreshToken,
                userMapper.toResponse(user)
        );
    }
}
