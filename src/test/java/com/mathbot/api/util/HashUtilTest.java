package com.mathbot.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link HashUtil}.
 *
 * Verifies SHA-256 hashing used for refresh token storage and question deduplication.
 */
@DisplayName("HashUtil – SHA-256 hashing")
class HashUtilTest {

    // Known SHA-256 hash of "hello"
    private static final String HELLO_SHA256 =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @Test
    @DisplayName("sha256() produces correct 64-char hex digest for known input")
    void sha256_knownInput_returnsCorrectDigest() {
        String result = HashUtil.sha256("hello");
        assertThat(result).isEqualTo(HELLO_SHA256);
    }

    @Test
    @DisplayName("sha256() returns 64-character hex string")
    void sha256_returnsFixedLength64Chars() {
        String hash = HashUtil.sha256("any input value");
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("sha256() is deterministic – same input produces same hash")
    void sha256_deterministic_sameinputSameOutput() {
        String input = "refresh-token-uuid-12345";
        assertThat(HashUtil.sha256(input)).isEqualTo(HashUtil.sha256(input));
    }

    @Test
    @DisplayName("sha256() produces different hashes for different inputs")
    void sha256_differentInputs_differentHashes() {
        String hash1 = HashUtil.sha256("token-a");
        String hash2 = HashUtil.sha256("token-b");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("sha256() handles empty string without throwing")
    void sha256_emptyString_returnsHash() {
        String hash = HashUtil.sha256("");
        assertThat(hash).isNotBlank().hasSize(64);
    }

    @Test
    @DisplayName("sha256() handles long string correctly")
    void sha256_longString_returnsCorrectLengthHash() {
        String longInput = "a".repeat(10_000);
        String hash = HashUtil.sha256(longInput);
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("sha256() handles UUID string format used for refresh tokens")
    void sha256_uuidString_returnsValid64CharHash() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        String hash = HashUtil.sha256(uuid);
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("sha256() is case-sensitive – uppercase and lowercase differ")
    void sha256_caseSensitive_differentHashes() {
        assertThat(HashUtil.sha256("Token")).isNotEqualTo(HashUtil.sha256("token"));
    }
}
