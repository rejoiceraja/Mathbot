package com.mathbot.api.service;

import com.mathbot.api.dto.request.*;
import com.mathbot.api.dto.response.*;
import com.mathbot.api.entity.*;
import com.mathbot.api.exception.*;
import com.mathbot.api.repository.*;
import com.mathbot.api.security.JwtUtil;
import com.mathbot.api.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw MathBotException.conflict(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .authProvider("LOCAL")
                .emailVerified(false)
                .build();

        user = userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> MathBotException.unauthorized(
                        ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));

        if (isLocked(user)) {
            throw MathBotException.forbidden(ErrorCode.ACCOUNT_LOCKED,
                    "Account is temporarily locked. Try again later.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordFailedAttempt(user);
            throw MathBotException.unauthorized(ErrorCode.INVALID_CREDENTIALS,
                    "Invalid email or password");
        }

        if (!user.getEmailVerified()) {
            throw MathBotException.forbidden(ErrorCode.ACCOUNT_NOT_VERIFIED,
                    "Please verify your email before logging in");
        }

        user.setFailedLoginCount((short) 0);
        user.setLockedUntil(null);
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String tokenHash = HashUtil.sha256(request.getRefreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> MathBotException.unauthorized(
                        ErrorCode.INVALID_TOKEN, "Refresh token not found"));

        if (stored.getRevoked() || stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw MathBotException.unauthorized(ErrorCode.INVALID_TOKEN,
                    "Refresh token is expired or revoked");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = UUID.randomUUID().toString();

        RefreshToken newToken = RefreshToken.builder()
                .user(user)
                .tokenHash(HashUtil.sha256(newRefreshToken))
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(newToken);

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String tokenHash = HashUtil.sha256(request.getRefreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String rawRefreshToken = UUID.randomUUID().toString();

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(HashUtil.sha256(rawRefreshToken))
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(token);

        UserDto userDto = mapToDto(user);
        return new AuthResponse(accessToken, rawRefreshToken, userDto);
    }

    private boolean isLocked(User user) {
        return user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(OffsetDateTime.now());
    }

    private void recordFailedAttempt(User user) {
        short attempts = (short) (user.getFailedLoginCount() + 1);
        user.setFailedLoginCount(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
        }
        userRepository.save(user);
    }

    public static UserDto mapToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatarUrl(user.getAvatarUrl())
                .currentGrade(user.getCurrentGrade())
                .authProvider(user.getAuthProvider())
                .emailVerified(user.getEmailVerified())
                .build();
    }
}
