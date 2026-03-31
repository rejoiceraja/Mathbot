package com.mathbot.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link JwtAuthFilter}.
 *
 * Verifies filter chain behaviour for valid tokens, missing headers,
 * malformed tokens, and expired tokens (TDD §3.3 step 9).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthFilter – JWT filter chain")
class JwtAuthFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Valid bearer token populates SecurityContext with userId principal")
    void doFilter_validBearerToken_setsAuthentication() throws ServletException, IOException {
        String token = "valid.jwt.token";
        given(request.getHeader("Authorization")).willReturn("Bearer " + token);
        given(jwtUtil.isTokenValid(token)).willReturn(true);
        given(jwtUtil.extractUserId(token)).willReturn(userId);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(userId.toString());
        then(filterChain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("Missing Authorization header – SecurityContext stays null, chain continues")
    void doFilter_noAuthHeader_skipsAuthentication() throws ServletException, IOException {
        given(request.getHeader("Authorization")).willReturn(null);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(filterChain).should().doFilter(request, response);
        then(jwtUtil).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Authorization header without Bearer prefix – chain continues without auth")
    void doFilter_basicAuthHeader_skipsAuthentication() throws ServletException, IOException {
        given(request.getHeader("Authorization")).willReturn("Basic dXNlcjpwYXNz");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(filterChain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("Invalid JWT – SecurityContext stays null, chain continues")
    void doFilter_invalidToken_skipsAuthentication() throws ServletException, IOException {
        String token = "invalid.token";
        given(request.getHeader("Authorization")).willReturn("Bearer " + token);
        given(jwtUtil.isTokenValid(token)).willReturn(false);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(filterChain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("JwtUtil throws exception – filter swallows it and chain continues")
    void doFilter_jwtUtilThrows_chainContinuesWithoutAuth() throws ServletException, IOException {
        String token = "throws.exception";
        given(request.getHeader("Authorization")).willReturn("Bearer " + token);
        given(jwtUtil.isTokenValid(token)).willThrow(new RuntimeException("unexpected"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(filterChain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("Filter always calls filterChain.doFilter() exactly once")
    void doFilter_alwaysCallsChainOnce() throws ServletException, IOException {
        given(request.getHeader("Authorization")).willReturn(null);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        then(filterChain).should(times(1)).doFilter(request, response);
    }
}
