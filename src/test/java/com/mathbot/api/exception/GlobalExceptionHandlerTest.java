package com.mathbot.api.exception;

import com.mathbot.api.controller.AuthController;
import com.mathbot.api.dto.request.RegisterRequest;
import com.mathbot.api.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * Verifies that exceptions are mapped to correct HTTP statuses and
 * ApiResponse error envelope format (TDD §9.1 Global Exception Handler).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler – exception-to-HTTP mapping")
class GlobalExceptionHandlerTest {

    @Mock private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String VALID_REGISTER_BODY = """
            {"firstName":"Alice","lastName":"Smith","email":"a@test.com","password":"Pass1234!"}
            """;

    // ── MathBotException mappings ─────────────────────────────────────────────

    @Nested
    @DisplayName("MathBotException HTTP status mapping")
    class MathBotExceptionMappingTests {

        @Test
        @DisplayName("404 NOT_FOUND for USER_NOT_FOUND error code")
        void mathBotException_userNotFound_returns404() throws Exception {
            given(authService.register(any()))
                    .willThrow(MathBotException.notFound(
                            ErrorCode.USER_NOT_FOUND, "User not found"));

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REGISTER_BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"))
                    .andExpect(jsonPath("$.error.message").isNotEmpty());
        }

        @Test
        @DisplayName("401 UNAUTHORIZED for INVALID_TOKEN error code")
        void mathBotException_invalidToken_returns401() throws Exception {
            given(authService.register(any()))
                    .willThrow(MathBotException.unauthorized(
                            ErrorCode.INVALID_TOKEN, "Token is invalid"));

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REGISTER_BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        }

        @Test
        @DisplayName("403 FORBIDDEN for ACCOUNT_LOCKED error code")
        void mathBotException_accountLocked_returns403() throws Exception {
            given(authService.register(any()))
                    .willThrow(MathBotException.forbidden(
                            ErrorCode.ACCOUNT_LOCKED, "Account is locked"));

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REGISTER_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
        }

        @Test
        @DisplayName("409 CONFLICT for EMAIL_ALREADY_EXISTS error code")
        void mathBotException_emailExists_returns409() throws Exception {
            given(authService.register(any()))
                    .willThrow(MathBotException.conflict(
                            ErrorCode.EMAIL_ALREADY_EXISTS, "Email already registered"));

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REGISTER_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("503 SERVICE_UNAVAILABLE for LLM_UNAVAILABLE error code")
        void mathBotException_llmUnavailable_returns503() throws Exception {
            given(authService.register(any()))
                    .willThrow(MathBotException.serviceUnavailable(
                            ErrorCode.LLM_UNAVAILABLE, "Claude API unavailable"));

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REGISTER_BODY))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value("LLM_UNAVAILABLE"));
        }
    }

    // ── Validation errors ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation error handling (MethodArgumentNotValidException)")
    class ValidationErrorTests {

        @Test
        @DisplayName("Returns 400 with VALIDATION_ERROR code and field errors map")
        void validationError_returns400WithFieldErrors() throws Exception {
            // Missing all required fields
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.fieldErrors").isMap());
        }

        @Test
        @DisplayName("Field errors map contains entry for each invalid field")
        void validationError_containsFieldErrorEntries() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"not-valid\",\"password\":\"x\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fieldErrors").isMap())
                    .andExpect(jsonPath("$.error.fieldErrors.email").exists());
        }

        @Test
        @DisplayName("Returns 400 when email format is invalid")
        void invalidEmailFormat_returns400WithEmailFieldError() throws Exception {
            String body = """
                    {"firstName":"A","lastName":"B","email":"bademail","password":"Pass1234!"}
                    """;
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fieldErrors.email").isString());
        }
    }

    // ── Generic exception handler ─────────────────────────────────────────────

    @Nested
    @DisplayName("Generic (unhandled) exception handling")
    class GenericExceptionTests {

        @Test
        @DisplayName("Returns 500 Internal Server Error for unexpected exceptions")
        void unexpectedException_returns500() throws Exception {
            given(authService.register(any()))
                    .willThrow(new RuntimeException("Database connection failed"));

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REGISTER_BODY))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
        }

        @Test
        @DisplayName("500 response does not expose internal exception message")
        void unexpectedException_doesNotExposeMessage() throws Exception {
            given(authService.register(any()))
                    .willThrow(new RuntimeException("secret DB password is abc123"));

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REGISTER_BODY))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error.message")
                            .value("An unexpected error occurred"));
        }
    }

    // ── Response envelope structure ───────────────────────────────────────────

    @Nested
    @DisplayName("Response envelope structure")
    class ResponseEnvelopeTests {

        @Test
        @DisplayName("Error response contains success=false, error object, and timestamp")
        void errorResponse_containsRequiredFields() throws Exception {
            given(authService.register(any()))
                    .willThrow(MathBotException.notFound(
                            ErrorCode.USER_NOT_FOUND, "not found"));

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REGISTER_BODY))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error").isMap())
                    .andExpect(jsonPath("$.error.code").isString())
                    .andExpect(jsonPath("$.error.message").isString())
                    .andExpect(jsonPath("$.timestamp").isString());
        }

        @Test
        @DisplayName("Error response does not contain a data field")
        void errorResponse_noDataField() throws Exception {
            given(authService.register(any()))
                    .willThrow(MathBotException.unauthorized(
                            ErrorCode.INVALID_CREDENTIALS, "bad credentials"));

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REGISTER_BODY))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }
}
