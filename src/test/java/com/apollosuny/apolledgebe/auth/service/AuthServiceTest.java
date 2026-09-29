package com.apollosuny.apolledgebe.auth.service;

import com.apollosuny.apolledgebe.auth.dto.RegisterRequest;
import com.apollosuny.apolledgebe.auth.dto.TokenRefreshResponse;
import com.apollosuny.apolledgebe.auth.dto.TokenResponse;
import com.apollosuny.apolledgebe.auth.security.JwtProperties;
import com.apollosuny.apolledgebe.auth.security.JwtService;
import com.apollosuny.apolledgebe.common.exception.BusinessException;
import com.apollosuny.apolledgebe.user.entity.User;
import com.apollosuny.apolledgebe.user.entity.UserProvider;
import com.apollosuny.apolledgebe.user.mapper.UserMapper;
import com.apollosuny.apolledgebe.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserRepository userRepository;

    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(
                "access-secret-access-secret-access-secret",
                "refresh-secret-refresh-secret-refresh-secret",
                Duration.ofMinutes(15),
                Duration.ofDays(30)
        ));
        authService = new AuthService(
                authenticationManager, passwordEncoder, jwtService, userMapper, userRepository);
    }

    // ---------- register ----------

    @Test
    void register_shouldNormalizeInputHashPasswordAndIssueTokens() {
        when(userRepository.existsByUsernameAndProvider("trung", UserProvider.LOCAL)).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        TokenResponse response = authService.register(
                new RegisterRequest("  Trung ", " Trung@Example.com ", "password123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("trung");
        assertThat(captor.getValue().getEmail()).isEqualTo("trung@example.com");
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed");
        assertThat(captor.getValue().getProvider()).isEqualTo(UserProvider.LOCAL);
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    void register_shouldThrowConflict_whenUsernameAlreadyExists() {
        when(userRepository.existsByUsernameAndProvider("trung", UserProvider.LOCAL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("trung", "a@b.com", "password123")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("USERNAME_ALREADY_EXISTS");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void register_shouldThrowConflict_whenConcurrentRegistrationHitsUniqueConstraint() {
        when(userRepository.existsByUsernameAndProvider("trung", UserProvider.LOCAL)).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uq_users_username_provider"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("trung", "a@b.com", "password123")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo("USERNAME_ALREADY_EXISTS"));
    }

    // ---------- refresh ----------

    @Test
    void refresh_shouldIssueNewAccessToken_whenRefreshTokenIsValid() {
        User user = userWithValidFrom(Instant.now().minusSeconds(3600));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        TokenRefreshResponse response = authService.refresh(jwtService.generateRefreshToken(user.getId()));

        assertThat(jwtService.extractUserIdFromAccessToken(response.accessToken())).isEqualTo(user.getId());
    }

    @Test
    void refresh_shouldAcceptToken_whenJwtValidFromIsSetInTheSameSecondAsIssue() {
        User user = userWithValidFrom(Instant.now());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        TokenRefreshResponse response = authService.refresh(jwtService.generateRefreshToken(user.getId()));

        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    void refresh_shouldReject_whenTokenWasIssuedBeforeJwtValidFrom() {
        User user = userWithValidFrom(Instant.now().plusSeconds(60));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        String staleToken = jwtService.generateRefreshToken(user.getId());

        assertInvalidRefreshToken(staleToken);
    }

    @Test
    void refresh_shouldReject_whenUserNoLongerExists() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertInvalidRefreshToken(jwtService.generateRefreshToken(userId));
    }

    @Test
    void refresh_shouldReject_whenAccessTokenIsSentInsteadOfRefreshToken() {
        assertInvalidRefreshToken(jwtService.generateAccessToken(UUID.randomUUID()));
        verifyNoInteractions(userRepository);
    }

    @Test
    void refresh_shouldReject_whenTokenIsGarbage() {
        assertInvalidRefreshToken("not-a-jwt");
        verifyNoInteractions(userRepository);
    }

    private User userWithValidFrom(Instant jwtValidFrom) {
        return User.builder()
                .id(UUID.randomUUID())
                .username("trung")
                .provider(UserProvider.LOCAL)
                .jwtValidFrom(jwtValidFrom)
                .build();
    }

    private void assertInvalidRefreshToken(String token) {
        assertThatThrownBy(() -> authService.refresh(token))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("INVALID_REFRESH_TOKEN");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }
}
