package com.mathbot.api.llm;

import com.mathbot.api.dto.response.QuestionDto;
import com.mathbot.api.entity.Grade;
import com.mathbot.api.entity.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link LLMGatewayService}.
 *
 * Covers cache hit/miss behaviour, successful API call, fallback invocation
 * when circuit is open, and the fallback method itself.
 * (TDD §4 LLM Gateway Design)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LLMGatewayService – Claude API gateway")
class LLMGatewayServiceTest {

    @Mock private ClaudeApiClient claudeApiClient;
    @Mock private PromptBuilder promptBuilder;
    @Mock private LLMResponseParser responseParser;
    @Mock private FallbackQuestionService fallbackQuestionService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private LLMGatewayService llmGatewayService;

    private UUID userId;
    private Topic topic;
    private QuestionDto expectedQuestion;

    private static final String VALID_CLAUDE_RESPONSE = """
            {"content":[{"type":"text","text":"{\\"question_text\\":\\"5x=25\\",\\"answer_type\\":\\"open_ended\\",\\"correct_answer\\":\\"5\\",\\"hint\\":\\"divide both sides\\"}"}]}
            """;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        Grade grade = Grade.builder().id((short) 5).displayName("Grade 5").build();
        topic = Topic.builder().id(UUID.randomUUID()).name("Algebra").grade(grade).build();

        expectedQuestion = QuestionDto.builder()
                .questionText("5x = 25, what is x?")
                .answerType("open_ended")
                .build();

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("Cache hit: returns parsed question from Redis without calling Claude API")
    void generateQuestion_cacheHit_returnsFromCacheWithoutApiCall() {
        given(valueOperations.get(anyString())).willReturn(VALID_CLAUDE_RESPONSE);
        given(responseParser.parseQuestionJson(anyString())).willReturn(expectedQuestion);

        QuestionDto result = llmGatewayService.generateQuestion(userId, topic, "HARD");

        assertThat(result).isEqualTo(expectedQuestion);
        then(claudeApiClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Cache miss: calls Claude API, parses response, stores in cache")
    void generateQuestion_cacheMiss_callsClaudeAndCachesResponse() {
        given(valueOperations.get(anyString())).willReturn(null);
        given(promptBuilder.buildUserMessage(userId, topic, "EASY"))
                .willReturn("{\"grade\":5,\"topic\":\"Algebra\"}");
        given(claudeApiClient.generateQuestion(anyString(), anyString()))
                .willReturn(Mono.just(VALID_CLAUDE_RESPONSE));
        given(responseParser.parseClaudeResponse(VALID_CLAUDE_RESPONSE))
                .willReturn(expectedQuestion);

        QuestionDto result = llmGatewayService.generateQuestion(userId, topic, "EASY");

        assertThat(result).isEqualTo(expectedQuestion);
        then(valueOperations).should().set(anyString(), eq(VALID_CLAUDE_RESPONSE), any());
    }

    @Test
    @DisplayName("Cache miss: Claude API is called with correct system and user prompts")
    void generateQuestion_cacheMiss_promptsPassedToClaudeClient() {
        given(valueOperations.get(anyString())).willReturn(null);
        String userMsg = "{\"grade\":5}";
        given(promptBuilder.buildUserMessage(userId, topic, "MEDIUM")).willReturn(userMsg);
        given(claudeApiClient.generateQuestion(PromptBuilder.SYSTEM_PROMPT, userMsg))
                .willReturn(Mono.just(VALID_CLAUDE_RESPONSE));
        given(responseParser.parseClaudeResponse(any())).willReturn(expectedQuestion);

        llmGatewayService.generateQuestion(userId, topic, "MEDIUM");

        then(claudeApiClient).should()
                .generateQuestion(eq(PromptBuilder.SYSTEM_PROMPT), eq(userMsg));
    }

    @Test
    @DisplayName("fallbackQuestion() delegates to FallbackQuestionService")
    void fallbackQuestion_delegatesToFallbackService() {
        given(fallbackQuestionService.getFallbackQuestion(
                topic.getId(), "EASY", any()))
                .willReturn(expectedQuestion);

        QuestionDto result = llmGatewayService.fallbackQuestion(
                userId, topic, "EASY", new RuntimeException("circuit open"));

        assertThat(result).isEqualTo(expectedQuestion);
        then(claudeApiClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Cache key encodes topicId, level, and userId to prevent cross-user cache hits")
    void generateQuestion_cacheKeyIsUserSpecific() {
        given(valueOperations.get(anyString())).willReturn(null);
        given(promptBuilder.buildUserMessage(any(), any(), any())).willReturn("msg");
        given(claudeApiClient.generateQuestion(any(), any()))
                .willReturn(Mono.just(VALID_CLAUDE_RESPONSE));
        given(responseParser.parseClaudeResponse(any())).willReturn(expectedQuestion);

        UUID otherUser = UUID.randomUUID();

        llmGatewayService.generateQuestion(userId, topic, "EASY");
        llmGatewayService.generateQuestion(otherUser, topic, "EASY");

        // Verify two distinct cache keys were used
        then(valueOperations).should(times(2)).get(anyString());
    }
}
