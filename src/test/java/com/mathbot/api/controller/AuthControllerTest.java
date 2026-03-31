package com.mathbot.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathbot.api.dto.request.LoginRequest;
import com.mathbot.api.dto.request.RefreshRequest;
import com.mathbot.api.dto.request.RegisterRequest;
import com.mathbot.api.dto.response.AuthResponse;
import com.mathbot.api.dto.response.TokenResponse;
import com.mathbot.api.dto.response.UserDto;
import com.mathbot.api.exception.ErrorCode;
import com.mathbot.api.exception.GlobalExceptionHandler;
import com.mathbot.api.exception.MathBotException;
import com.mathbot.api.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link AuthController} using MockMvc (no Spring context).
 *
 * Tests HTTP status codes, response envelope structure, and validation
 * for all authentication endpoints (TDD §6.2 Authentication Endpoints).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController – REST endpoint tests")
class AuthControllerTest {

    @Mock private AuthService authService;
    @InjectMocks private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UserDto sampleUser;
    private AuthResponse sampleAuthResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();

        sampleUser = UserDto.builder()
                .id(UUID.randomUUID())
                .email("student@mathbot.com")
                .firstName("Alice")
                .lastName("Smith")
                .emailVerified(true)
                .build();

        sampleAuthResponse = AuthResponse.builder()
                .accessToken("access.jwt.token")
                .refreshToken("refresh-uuid-token")
                .user(sampleUser)
                .build();
    }

    // ── POST /register ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("Valid registration returns 201 Created with user data")
        void register_validRequest_returns201() throws Exception {
            RegisterRequest req = new RegisterRequest(
                    "Alice", "Smith", "student@mathbot.com", "SecurePass1!");
            given(authService.register(any())).willReturn(sampleAuthResponse);

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.email").value("student@mathbot.com"))
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("Missing email returns 400 Bad Request with validation error")
        void register_missingEmail_returns400() throws Exception {
            RegisterRequest req = new RegisterRequest("Alice", "Smith", null, "Pass1234!");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Invalid email format returns 400 with field error for 'email'")
        void register_invalidEmailFormat_returns400WithFieldError() throws Exception {
            RegisterRequest req = new RegisterRequest(
                    "Alice", "Smith", "not-an-email", "Pass1234!");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fieldErrors.email").exists());
        }

        @Test
        @DisplayName("Password shorter than 8 chars returns 400")
        void register_shortPassword_returns400() throws Exception {
            RegisterRequest req = new RegisterRequest(
                    "Alice", "Smith", "alice@test.com", "short");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Duplicate email returns 409 Conflict")
        void register_duplicateEmail_returns409() throws Exception {
            RegisterRequest req = new RegisterRequest(
                    "Alice", "Smith", "existing@test.com", "Pass1234!");
            given(authService.register(any()))
                    .willThrow(MathBotException.conflict(
                            ErrorCode.EMAIL_ALREADY_EXISTS, "Email already registered"));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
        }
    }

    // ── POST /login ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("Valid credentials return 200 with accessToken and refreshToken")
        void login_validCredentials_returns200() throws Exception {
            LoginRequest req = new LoginRequest("student@mathbot.com", "password123");
            given(authService.login(any())).willReturn(sampleAuthResponse);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("access.jwt.token"))
                    .andExpect(jsonPath("$.data.refreshToken").value("refresh-uuid-token"))
                    .andExpect(jsonPath("$.data.user.email").value("student@mathbot.com"));
        }

        @Test
        @DisplayName("Invalid credentials return 401 Unauthorized")
        void login_invalidCredentials_returns401() throws Exception {
            LoginRequest req = new LoginRequest("bad@test.com", "wrongpass");
            given(authService.login(any()))
                    .willThrow(MathBotException.unauthorized(
                            ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("Account locked returns 403 Forbidden")
        void login_lockedAccount_returns403() throws Exception {
            LoginRequest req = new LoginRequest("locked@test.com", "pass");
            given(authService.login(any()))
                    .willThrow(MathBotException.forbidden(
                            ErrorCode.ACCOUNT_LOCKED, "Account is temporarily locked"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
        }

        @Test
        @DisplayName("Unverified email returns 403 with ACCOUNT_NOT_VERIFIED code")
        void login_unverifiedEmail_returns403() throws Exception {
            LoginRequest req = new LoginRequest("unverified@test.com", "Pass1234");
            given(authService.login(any()))
                    .willThrow(MathBotException.forbidden(
                            ErrorCode.ACCOUNT_NOT_VERIFIED, "Please verify your email"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_VERIFIED"));
        }

        @Test
        @DisplayName("Missing email field returns 400 Bad Request")
        void login_missingEmail_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"pass123\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Blank password returns 400 Bad Request")
        void login_blankPassword_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"s@test.com\",\"password\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── POST /refresh ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class RefreshTests {

        @Test
        @DisplayName("Valid refresh token returns 200 with new token pair")
        void refresh_validToken_returns200() throws Exception {
            RefreshRequest req = new RefreshRequest("valid-refresh-token");
            TokenResponse tokens = new TokenResponse("new.access.tok", "new-refresh-uuid");
            given(authService.refresh(any())).willReturn(tokens);

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("new.access.tok"))
                    .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-uuid"));
        }

        @Test
        @DisplayName("Invalid refresh token returns 401")
        void refresh_invalidToken_returns401() throws Exception {
            RefreshRequest req = new RefreshRequest("bad-token");
            given(authService.refresh(any()))
                    .willThrow(MathBotException.unauthorized(
                            ErrorCode.INVALID_TOKEN, "Refresh token is expired or revoked"));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        }
    }

    // ── POST /logout ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("Valid logout returns 200 with success=true")
        void logout_validToken_returns200() throws Exception {
            RefreshRequest req = new RefreshRequest("some-refresh-token");
            willDoNothing().given(authService).logout(any());

            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Missing refresh token body returns 400")
        void logout_missingBody_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
