package com.apollosuny.apolledgebe.auth.service;

import com.apollosuny.apolledgebe.auth.dto.LoginRequest;
import com.apollosuny.apolledgebe.auth.dto.RegisterRequest;
import com.apollosuny.apolledgebe.auth.dto.TokenRefreshResponse;
import com.apollosuny.apolledgebe.auth.dto.TokenResponse;
import com.apollosuny.apolledgebe.auth.security.JwtService;
import com.apollosuny.apolledgebe.auth.security.RefreshTokenClaims;
import com.apollosuny.apolledgebe.common.exception.BusinessException;
import com.apollosuny.apolledgebe.user.entity.User;
import com.apollosuny.apolledgebe.user.entity.UserProvider;
import com.apollosuny.apolledgebe.user.mapper.UserMapper;
import com.apollosuny.apolledgebe.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
            throw usernameAlreadyExists();
        }

        User user = User.builder()
                .username(username)
                .email(request.email().trim().toLowerCase())
                .provider(UserProvider.LOCAL)
                .password(passwordEncoder.encode(request.password()))
                .jwtValidFrom(Instant.now())
                .build();

        // The existence check above is racy; the unique (username, provider) constraint is the real guard.
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw usernameAlreadyExists();
        }

        return issueToken(user);
    }

    public TokenRefreshResponse refresh(String refreshToken) {
        RefreshTokenClaims claims = parseRefreshToken(refreshToken);

        User user = userRepository.findById(claims.userId())
                .orElseThrow(AuthService::invalidRefreshToken);

        // JWT iat has second precision, so compare against jwtValidFrom truncated to seconds.
        // Moving jwtValidFrom forward revokes every refresh token issued before it.
        if (claims.issuedAt().isBefore(user.getJwtValidFrom().truncatedTo(ChronoUnit.SECONDS))) {
            throw invalidRefreshToken();
        }

        return new TokenRefreshResponse(jwtService.generateAccessToken(user.getId()));
    }

    private RefreshTokenClaims parseRefreshToken(String refreshToken) {
        try {
            return jwtService.parseRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidRefreshToken();
        }
    }

    private static BusinessException invalidRefreshToken() {
        return new BusinessException(
                "INVALID_REFRESH_TOKEN",
                "Refresh token is invalid or expired",
                HttpStatus.UNAUTHORIZED
        );
    }

    private static BusinessException usernameAlreadyExists() {
        return new BusinessException(
                "USERNAME_ALREADY_EXISTS",
                "Username already exists",
                HttpStatus.CONFLICT
        );
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
