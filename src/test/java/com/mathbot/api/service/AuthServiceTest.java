package com.mathbot.api.service;

import com.mathbot.api.dto.request.LoginRequest;
import com.mathbot.api.dto.request.RefreshRequest;
import com.mathbot.api.dto.request.RegisterRequest;
import com.mathbot.api.dto.response.AuthResponse;
import com.mathbot.api.dto.response.TokenResponse;
import com.mathbot.api.entity.RefreshToken;
import com.mathbot.api.entity.User;
import com.mathbot.api.exception.ErrorCode;
import com.mathbot.api.exception.MathBotException;
import com.mathbot.api.repository.RefreshTokenRepository;
import com.mathbot.api.repository.UserRepository;
import com.mathbot.api.security.JwtUtil;
import com.mathbot.api.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link AuthService}.
 *
 * Covers registration, login (happy path + failure modes), token refresh,
 * and logout as specified in TDD §3.3 JWT Authentication Flow.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService – authentication logic")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User validUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        validUser = User.builder()
                .id(userId)
                .email("student@mathbot.com")
                .passwordHash("$2a$10$hashedPassword")
                .firstName("Alice")
                .lastName("Smith")
                .authProvider("LOCAL")
                .emailVerified(true)
                .failedLoginCount((short) 0)
                .build();
    }

    // ── register() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("Happy path: returns AuthResponse with user details")
        void register_newEmail_returnsAuthResponse() {
            RegisterRequest req = new RegisterRequest("Alice", "Smith",
                    "student@mathbot.com", "SecurePass1!");
            given(userRepository.existsByEmail(req.getEmail())).willReturn(false);
            given(passwordEncoder.encode(req.getPassword())).willReturn("$2a$10$hash");
            given(userRepository.save(any(User.class))).willReturn(validUser);
            given(jwtUtil.generateAccessToken(any(), anyString())).willReturn("access.token.jwt");
            given(refreshTokenRepository.save(any(RefreshToken.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            AuthResponse result = authService.register(req);

            assertThat(result.getAccessToken()).isEqualTo("access.token.jwt");
            assertThat(result.getRefreshToken()).isNotBlank();
            assertThat(result.getUser().getEmail()).isEqualTo("student@mathbot.com");
        }

        @Test
        @DisplayName("Duplicate email throws MathBotException with EMAIL_ALREADY_EXISTS code")
        void register_duplicateEmail_throwsConflict() {
            RegisterRequest req = new RegisterRequest("Bob", "Jones",
                    "student@mathbot.com", "Pass1234!");
            given(userRepository.existsByEmail(req.getEmail())).willReturn(true);

            assertThatExceptionOfType(MathBotException.class)
                    .isThrownBy(() -> authService.register(req))
                    .satisfies(e -> assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
        }

        @Test
        @DisplayName("Password is BCrypt-encoded before saving")
        void register_passwordIsEncoded() {
            RegisterRequest req = new RegisterRequest("Alice", "S",
                    "alice@test.com", "plainPassword");
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(passwordEncoder.encode("plainPassword")).willReturn("encoded");
            given(userRepository.save(any(User.class))).willReturn(validUser);
            given(jwtUtil.generateAccessToken(any(), anyString())).willReturn("tok");
            given(refreshTokenRepository.save(any())).willAnswer(i -> i.getArgument(0));

            authService.register(req);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            then(userRepository).should().save(captor.capture());
            assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded");
        }

        @Test
        @DisplayName("New user's emailVerified defaults to false")
        void register_newUser_emailVerifiedFalse() {
            RegisterRequest req = new RegisterRequest("Alice", "S",
                    "alice@test.com", "plainPassword");
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            given(userRepository.save(any(User.class))).willReturn(validUser);
            given(jwtUtil.generateAccessToken(any(), anyString())).willReturn("tok");
            given(refreshTokenRepository.save(any())).willAnswer(i -> i.getArgument(0));

            authService.register(req);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            then(userRepository).should().save(captor.capture());
            assertThat(captor.getValue().getEmailVerified()).isFalse();
        }
    }

    // ── login() ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("Happy path: verified user with correct password returns AuthResponse")
        void login_validCredentials_returnsAuthResponse() {
            LoginRequest req = new LoginRequest("student@mathbot.com", "password123");
            given(userRepository.findByEmail(req.getEmail())).willReturn(Optional.of(validUser));
            given(passwordEncoder.matches(req.getPassword(), validUser.getPasswordHash()))
                    .willReturn(true);
            given(jwtUtil.generateAccessToken(userId, validUser.getEmail()))
                    .willReturn("access.tok");
            given(refreshTokenRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(userRepository.save(any())).willReturn(validUser);

            AuthResponse result = authService.login(req);

            assertThat(result.getAccessToken()).isEqualTo("access.tok");
            assertThat(result.getUser().getId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("Unknown email throws INVALID_CREDENTIALS (prevents email enumeration)")
        void login_unknownEmail_throwsUnauthorized() {
            given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

            assertThatExceptionOfType(MathBotException.class)
                    .isThrownBy(() -> authService.login(
                            new LoginRequest("unknown@test.com", "pass")))
                    .satisfies(e -> assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        }

        @Test
        @DisplayName("Wrong password throws INVALID_CREDENTIALS and increments failedLoginCount")
        void login_wrongPassword_throwsAndIncrementsCounter() {
            LoginRequest req = new LoginRequest("student@mathbot.com", "wrongPass");
            given(userRepository.findByEmail(req.getEmail())).willReturn(Optional.of(validUser));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);
            given(userRepository.save(any())).willReturn(validUser);

            assertThatExceptionOfType(MathBotException.class)
                    .isThrownBy(() -> authService.login(req))
                    .satisfies(e -> assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_CREDENTIALS));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            then(userRepository).should().save(captor.capture());
            assertThat(captor.getValue().getFailedLoginCount()).isEqualTo((short) 1);
        }

        @Test
        @DisplayName("Unverified email throws ACCOUNT_NOT_VERIFIED")
        void login_unverifiedEmail_throwsForbidden() {
            validUser.setEmailVerified(false);
            LoginRequest req = new LoginRequest("student@mathbot.com", "password123");
            given(userRepository.findByEmail(req.getEmail())).willReturn(Optional.of(validUser));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
            given(userRepository.save(any())).willReturn(validUser);

            assertThatExceptionOfType(MathBotException.class)
                    .isThrownBy(() -> authService.login(req))
                    .satisfies(e -> assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.ACCOUNT_NOT_VERIFIED));
        }

        @Test
        @DisplayName("Locked account throws ACCOUNT_LOCKED before checking password")
        void login_lockedAccount_throwsForbiddenWithoutCheckingPassword() {
            validUser.setLockedUntil(OffsetDateTime.now().plusMinutes(10));
            LoginRequest req = new LoginRequest("student@mathbot.com", "anyPass");
            given(userRepository.findByEmail(req.getEmail())).willReturn(Optional.of(validUser));

            assertThatExceptionOfType(MathBotException.class)
                    .isThrownBy(() -> authService.login(req))
                    .satisfies(e -> assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.ACCOUNT_LOCKED));

            then(passwordEncoder).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("5 wrong-password attempts locks the account")
        void login_fiveFailedAttempts_locksAccount() {
            validUser.setFailedLoginCount((short) 4);
            LoginRequest req = new LoginRequest("student@mathbot.com", "wrong");
            given(userRepository.findByEmail(req.getEmail())).willReturn(Optional.of(validUser));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);
            given(userRepository.save(any())).willReturn(validUser);

            assertThatExceptionOfType(MathBotException.class)
                    .isThrownBy(() -> authService.login(req));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            then(userRepository).should().save(captor.capture());
            assertThat(captor.getValue().getLockedUntil()).isNotNull();
            assertThat(captor.getValue().getLockedUntil()).isAfter(OffsetDateTime.now());
        }

        @Test
        @DisplayName("Successful login resets failedLoginCount and clears lockedUntil")
        void login_success_resetsLoginCounter() {
            validUser.setFailedLoginCount((short) 3);
            LoginRequest req = new LoginRequest("student@mathbot.com", "pass");
            given(userRepository.findByEmail(req.getEmail())).willReturn(Optional.of(validUser));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
            given(jwtUtil.generateAccessToken(any(), any())).willReturn("tok");
            given(refreshTokenRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(userRepository.save(any())).willReturn(validUser);

            authService.login(req);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            then(userRepository).should().save(captor.capture());
            assertThat(captor.getValue().getFailedLoginCount()).isEqualTo((short) 0);
            assertThat(captor.getValue().getLockedUntil()).isNull();
        }
    }

    // ── refresh() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        @DisplayName("Valid refresh token: issues new access and refresh tokens")
        void refresh_validToken_returnsNewTokenPair() {
            String rawToken = "valid-refresh-uuid";
            String hash = HashUtil.sha256(rawToken);

            RefreshToken stored = RefreshToken.builder()
                    .tokenHash(hash)
                    .revoked(false)
                    .expiresAt(OffsetDateTime.now().plusDays(7))
                    .user(validUser)
                    .build();

            given(refreshTokenRepository.findByTokenHash(hash)).willReturn(Optional.of(stored));
            given(refreshTokenRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(jwtUtil.generateAccessToken(userId, validUser.getEmail()))
                    .willReturn("new.access.token");

            TokenResponse result = authService.refresh(new RefreshRequest(rawToken));

            assertThat(result.getAccessToken()).isEqualTo("new.access.token");
            assertThat(result.getRefreshToken()).isNotBlank()
                    .isNotEqualTo(rawToken); // rotated
        }

        @Test
        @DisplayName("Old refresh token is marked revoked after rotation")
        void refresh_oldTokenRevoked() {
            String rawToken = "old-refresh-token";
            String hash = HashUtil.sha256(rawToken);

            RefreshToken stored = RefreshToken.builder()
                    .tokenHash(hash)
                    .revoked(false)
                    .expiresAt(OffsetDateTime.now().plusDays(7))
                    .user(validUser)
                    .build();

            given(refreshTokenRepository.findByTokenHash(hash)).willReturn(Optional.of(stored));
            given(refreshTokenRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(jwtUtil.generateAccessToken(any(), any())).willReturn("tok");

            authService.refresh(new RefreshRequest(rawToken));

            assertThat(stored.getRevoked()).isTrue();
        }

        @Test
        @DisplayName("Unknown refresh token throws INVALID_TOKEN")
        void refresh_unknownToken_throwsUnauthorized() {
            given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

            assertThatExceptionOfType(MathBotException.class)
                    .isThrownBy(() -> authService.refresh(new RefreshRequest("unknown-token")))
                    .satisfies(e -> assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TOKEN));
        }

        @Test
        @DisplayName("Revoked refresh token throws INVALID_TOKEN")
        void refresh_revokedToken_throwsUnauthorized() {
            String rawToken = "revoked-token";
            RefreshToken stored = RefreshToken.builder()
                    .tokenHash(HashUtil.sha256(rawToken))
                    .revoked(true)
                    .expiresAt(OffsetDateTime.now().plusDays(7))
                    .user(validUser)
                    .build();

            given(refreshTokenRepository.findByTokenHash(anyString()))
                    .willReturn(Optional.of(stored));

            assertThatExceptionOfType(MathBotException.class)
                    .isThrownBy(() -> authService.refresh(new RefreshRequest(rawToken)))
                    .satisfies(e -> assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TOKEN));
        }

        @Test
        @DisplayName("Expired refresh token throws INVALID_TOKEN")
        void refresh_expiredToken_throwsUnauthorized() {
            String rawToken = "expired-token";
            RefreshToken stored = RefreshToken.builder()
                    .tokenHash(HashUtil.sha256(rawToken))
                    .revoked(false)
                    .expiresAt(OffsetDateTime.now().minusDays(1)) // expired yesterday
                    .user(validUser)
                    .build();

            given(refreshTokenRepository.findByTokenHash(anyString()))
                    .willReturn(Optional.of(stored));

            assertThatExceptionOfType(MathBotException.class)
                    .isThrownBy(() -> authService.refresh(new RefreshRequest(rawToken)))
                    .satisfies(e -> assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TOKEN));
        }
    }

    // ── logout() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("Valid token: marks refresh token as revoked")
        void logout_validToken_revokesToken() {
            String rawToken = "logout-token";
            RefreshToken stored = RefreshToken.builder()
                    .tokenHash(HashUtil.sha256(rawToken))
                    .revoked(false)
                    .user(validUser)
                    .build();

            given(refreshTokenRepository.findByTokenHash(anyString()))
                    .willReturn(Optional.of(stored));
            given(refreshTokenRepository.save(any())).willAnswer(i -> i.getArgument(0));

            authService.logout(new RefreshRequest(rawToken));

            assertThat(stored.getRevoked()).isTrue();
        }

        @Test
        @DisplayName("Unknown token: no exception thrown (idempotent logout)")
        void logout_unknownToken_noException() {
            given(refreshTokenRepository.findByTokenHash(anyString()))
                    .willReturn(Optional.empty());

            assertThatNoException()
                    .isThrownBy(() -> authService.logout(new RefreshRequest("unknown")));
        }
    }
}
