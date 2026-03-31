package com.mathbot.api.llm;

import com.mathbot.api.dto.response.QuestionDto;
import com.mathbot.api.entity.StaticQuestion;
import com.mathbot.api.entity.Topic;
import com.mathbot.api.exception.MathBotException;
import com.mathbot.api.repository.StaticQuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link FallbackQuestionService}.
 *
 * Verifies that static fallback questions are returned correctly when the
 * Claude API circuit breaker is open (TDD §4.1).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FallbackQuestionService – static question fallback")
class FallbackQuestionServiceTest {

    @Mock private StaticQuestionRepository staticQuestionRepository;

    @InjectMocks
    private FallbackQuestionService fallbackQuestionService;

    private UUID topicId;
    private StaticQuestion staticQuestion;

    @BeforeEach
    void setUp() {
        topicId = UUID.randomUUID();
        staticQuestion = StaticQuestion.builder()
                .id(UUID.randomUUID())
                .topic(Topic.builder().id(topicId).name("Multiplication").build())
                .level("EASY")
                .questionText("What is 6 × 7?")
                .correctAnswer("42")
                .options("[\"A: 36\",\"B: 42\",\"C: 48\",\"D: 40\"]")
                .build();
    }

    @Test
    @DisplayName("Returns QuestionDto when candidates are available (excluding recent)")
    void getFallbackQuestion_withCandidates_returnsQuestion() {
        List<UUID> recentIds = List.of(UUID.randomUUID());
        given(staticQuestionRepository.findByTopicAndLevelExcluding(topicId, "EASY", recentIds))
                .willReturn(List.of(staticQuestion));

        QuestionDto result = fallbackQuestionService.getFallbackQuestion(
                topicId, "EASY", recentIds);

        assertThat(result.getQuestionText()).isEqualTo("What is 6 × 7?");
        assertThat(result.getAnswerType()).isEqualTo("multiple_choice");
    }

    @Test
    @DisplayName("Falls back to all questions for level when exclusion yields no results")
    void getFallbackQuestion_noNonRecentQuestions_usesAllQuestionsForLevel() {
        List<UUID> recentIds = List.of(staticQuestion.getId());
        given(staticQuestionRepository.findByTopicAndLevelExcluding(topicId, "EASY", recentIds))
                .willReturn(Collections.emptyList());
        given(staticQuestionRepository.findByTopicIdAndLevel(topicId, "EASY"))
                .willReturn(List.of(staticQuestion));

        QuestionDto result = fallbackQuestionService.getFallbackQuestion(
                topicId, "EASY", recentIds);

        assertThat(result).isNotNull();
        assertThat(result.getQuestionText()).isEqualTo("What is 6 × 7?");
    }

    @Test
    @DisplayName("Throws MathBotException SERVICE_UNAVAILABLE when zero static questions exist")
    void getFallbackQuestion_noQuestionsAtAll_throwsServiceUnavailable() {
        given(staticQuestionRepository.findByTopicAndLevelExcluding(any(), any(), any()))
                .willReturn(Collections.emptyList());
        given(staticQuestionRepository.findByTopicIdAndLevel(any(), any()))
                .willReturn(Collections.emptyList());

        assertThatExceptionOfType(MathBotException.class)
                .isThrownBy(() -> fallbackQuestionService.getFallbackQuestion(
                        topicId, "HARD", List.of()))
                .satisfies(e -> assertThat(e.getErrorCode())
                        .isEqualTo(com.mathbot.api.exception.ErrorCode.LLM_UNAVAILABLE));
    }

    @Test
    @DisplayName("Returns QuestionDto with empty recent ids list")
    void getFallbackQuestion_emptyRecentIds_returnsQuestion() {
        given(staticQuestionRepository.findByTopicAndLevelExcluding(topicId, "MEDIUM", List.of()))
                .willReturn(List.of(staticQuestion));

        QuestionDto result = fallbackQuestionService.getFallbackQuestion(
                topicId, "MEDIUM", List.of());

        assertThat(result.getQuestionId()).isEqualTo(staticQuestion.getId());
    }

    @Test
    @DisplayName("QuestionDto id matches StaticQuestion id for cache/dedup purposes")
    void getFallbackQuestion_questionIdMatchesStaticQuestionId() {
        given(staticQuestionRepository.findByTopicAndLevelExcluding(any(), any(), any()))
                .willReturn(List.of(staticQuestion));

        QuestionDto result = fallbackQuestionService.getFallbackQuestion(
                topicId, "EASY", List.of());

        assertThat(result.getQuestionId()).isEqualTo(staticQuestion.getId());
    }
}
