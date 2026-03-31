package com.mathbot.api.llm;

import com.mathbot.api.dto.response.QuestionDto;
import com.mathbot.api.entity.Topic;
import com.mathbot.api.exception.ErrorCode;
import com.mathbot.api.exception.LLMParseException;
import com.mathbot.api.exception.MathBotException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LLMGatewayService {

    private static final Logger log = LoggerFactory.getLogger(LLMGatewayService.class);
    private static final String CACHE_PREFIX = "llm_response:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final ClaudeApiClient claudeApiClient;
    private final PromptBuilder promptBuilder;
    private final LLMResponseParser responseParser;
    private final FallbackQuestionService fallbackQuestionService;
    private final StringRedisTemplate redisTemplate;

    @CircuitBreaker(name = "claude-api", fallbackMethod = "fallbackQuestion")
    @Retry(name = "claude-api")
    public QuestionDto generateQuestion(UUID userId, Topic topic, String level) {
        String cacheKey = buildCacheKey(topic.getId(), level, userId);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("LLM cache hit for key: {}", cacheKey);
            return responseParser.parseQuestionJson(cached);
        }

        String systemPrompt = PromptBuilder.SYSTEM_PROMPT;
        String userMessage = promptBuilder.buildUserMessage(userId, topic, level);

        String rawResponse = claudeApiClient.generateQuestion(systemPrompt, userMessage).block();
        QuestionDto question = responseParser.parseClaudeResponse(rawResponse);

        redisTemplate.opsForValue().set(cacheKey, rawResponse, CACHE_TTL);
        return question;
    }

    public QuestionDto fallbackQuestion(UUID userId, Topic topic, String level, Throwable ex) {
        log.warn("LLM circuit open or retries exhausted, using fallback. Cause: {}", ex.getMessage());
        return fallbackQuestionService.getFallbackQuestion(topic.getId(), level, List.of());
    }

    private String buildCacheKey(UUID topicId, String level, UUID userId) {
        return CACHE_PREFIX + topicId + ":" + level + ":" + userId;
    }
}
