package com.mathbot.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link JwtUtil}.
 *
 * Verifies JWT generation, parsing, validation, and expiry handling
 * as required by TDD §3.3 JWT Authentication Flow.
 */
@DisplayName("JwtUtil – JWT generation and validation")
class JwtUtilTest {

    private static final String SECRET =
            "test-secret-key-that-is-at-least-32-chars-long!!";
    private static final long ACCESS_TOKEN_EXPIRY_MS = 900_000L; // 15 min
    private static final long ALREADY_EXPIRED_MS = -1L;          // immediately expired

    private JwtUtil jwtUtil;
    private UUID testUserId;
    private String testEmail;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, ACCESS_TOKEN_EXPIRY_MS);
        testUserId = UUID.randomUUID();
        testEmail = "student@mathbot.com";
    }

    // ── Token Generation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generateAccessToken() returns non-blank JWT string")
    void generateAccessToken_returnsNonBlankString() {
        String token = jwtUtil.generateAccessToken(testUserId, testEmail);
        assertThat(token).isNotBlank().contains(".");
    }

    @Test
    @DisplayName("generateAccessToken() embeds userId as subject claim")
    void generateAccessToken_subjectIsUserId() {
        String token = jwtUtil.generateAccessToken(testUserId, testEmail);
        UUID extracted = jwtUtil.extractUserId(token);
        assertThat(extracted).isEqualTo(testUserId);
    }

    @Test
    @DisplayName("generateAccessToken() embeds email as custom claim")
    void generateAccessToken_emailClaimPresent() {
        String token = jwtUtil.generateAccessToken(testUserId, testEmail);
        String email = jwtUtil.extractEmail(token);
        assertThat(email).isEqualTo(testEmail);
    }

    @Test
    @DisplayName("generateAccessToken() produces different tokens for different users")
    void generateAccessToken_differentUsers_differentTokens() {
        String tokenA = jwtUtil.generateAccessToken(UUID.randomUUID(), "a@test.com");
        String tokenB = jwtUtil.generateAccessToken(UUID.randomUUID(), "b@test.com");
        assertThat(tokenA).isNotEqualTo(tokenB);
    }

    // ── Token Validation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("isTokenValid() returns true for a freshly issued token")
    void isTokenValid_freshToken_returnsTrue() {
        String token = jwtUtil.generateAccessToken(testUserId, testEmail);
        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid() returns false for a malformed token string")
    void isTokenValid_malformedToken_returnsFalse() {
        assertThat(jwtUtil.isTokenValid("not.a.valid.jwt")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid() returns false for an empty string")
    void isTokenValid_emptyString_returnsFalse() {
        assertThat(jwtUtil.isTokenValid("")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid() returns false for a token signed with wrong secret")
    void isTokenValid_wrongSecret_returnsFalse() {
        JwtUtil otherJwt = new JwtUtil("completely-different-secret-that-is-32-chars!", ACCESS_TOKEN_EXPIRY_MS);
        String foreignToken = otherJwt.generateAccessToken(testUserId, testEmail);
        assertThat(jwtUtil.isTokenValid(foreignToken)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid() returns false for an already-expired token")
    void isTokenValid_expiredToken_returnsFalse() {
        JwtUtil shortLivedJwt = new JwtUtil(SECRET, ALREADY_EXPIRED_MS);
        String expiredToken = shortLivedJwt.generateAccessToken(testUserId, testEmail);
        assertThat(jwtUtil.isTokenValid(expiredToken)).isFalse();
    }

    // ── Claim Extraction ──────────────────────────────────────────────────────

    @Test
    @DisplayName("extractAllClaims() returns Claims with correct subject")
    void extractAllClaims_correctSubject() {
        String token = jwtUtil.generateAccessToken(testUserId, testEmail);
        Claims claims = jwtUtil.extractAllClaims(token);
        assertThat(claims.getSubject()).isEqualTo(testUserId.toString());
    }

    @Test
    @DisplayName("extractUserId() returns UUID matching the original userId")
    void extractUserId_returnsCorrectUUID() {
        String token = jwtUtil.generateAccessToken(testUserId, testEmail);
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(testUserId);
    }

    @Test
    @DisplayName("extractEmail() returns the email passed during token creation")
    void extractEmail_returnsCorrectEmail() {
        String token = jwtUtil.generateAccessToken(testUserId, testEmail);
        assertThat(jwtUtil.extractEmail(token)).isEqualTo(testEmail);
    }

    // ── Expiry ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isTokenExpired() returns false for a fresh token")
    void isTokenExpired_freshToken_returnsFalse() {
        String token = jwtUtil.generateAccessToken(testUserId, testEmail);
        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("isTokenExpired() returns true for an expired token")
    void isTokenExpired_expiredToken_returnsTrue() {
        JwtUtil shortLivedJwt = new JwtUtil(SECRET, ALREADY_EXPIRED_MS);
        String expiredToken = shortLivedJwt.generateAccessToken(testUserId, testEmail);
        assertThat(jwtUtil.isTokenExpired(expiredToken)).isTrue();
    }

    @Test
    @DisplayName("extractAllClaims() throws exception for expired token (fail-fast contract)")
    void extractAllClaims_expiredToken_throwsJwtException() {
        JwtUtil shortLivedJwt = new JwtUtil(SECRET, ALREADY_EXPIRED_MS);
        String expiredToken = shortLivedJwt.generateAccessToken(testUserId, testEmail);
        assertThatExceptionOfType(ExpiredJwtException.class)
                .isThrownBy(() -> jwtUtil.extractAllClaims(expiredToken));
    }
}
