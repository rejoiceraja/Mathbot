package com.mathbot.api.service;

import com.mathbot.api.dto.response.AnswerResultDto;
import com.mathbot.api.entity.*;
import com.mathbot.api.repository.QuestionHistoryRepository;
import com.mathbot.api.repository.UserTopicProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link AnswerService}.
 *
 * Covers mastery counter increment/reset, level progression (EASY→MEDIUM→HARD→CHALLENGING),
 * topic completion, and answer case-insensitive matching (TDD §6.4 Practice Session).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerService – answer validation and mastery logic")
class AnswerServiceTest {

    @Mock private UserTopicProgressRepository progressRepository;
    @Mock private QuestionHistoryRepository questionHistoryRepository;
    @Mock private PracticeSessionService sessionService;

    @InjectMocks
    private AnswerService answerService;

    private UUID userId;
    private UUID sessionId;
    private User user;
    private Topic topic;
    private PracticeSession session;
    private UserTopicProgress progress;
    private QuestionHistory questionRecord;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        user = User.builder().id(userId).email("s@test.com").build();
        topic = Topic.builder().id(UUID.randomUUID()).name("Multiplication")
                .grade(Grade.builder().id((short) 4).build()).build();

        session = PracticeSession.builder()
                .id(sessionId).user(user).topic(topic).build();

        progress = UserTopicProgress.builder()
                .user(user).topic(topic)
                .currentLevel("EASY").masteryCounter((short) 0)
                .status("IN_PROGRESS").build();

        questionRecord = QuestionHistory.builder()
                .session(session).user(user).topic(topic)
                .level("EASY")
                .questionText("What is 4 × 5?")
                .questionHash("abc123")
                .correctAnswer("20")
                .isCorrect(false)
                .build();

        given(sessionService.getSessionOrThrow(sessionId, userId)).willReturn(session);
        given(progressRepository.findByUserIdAndTopicId(userId, topic.getId()))
                .willReturn(Optional.of(progress));
        given(questionHistoryRepository.save(any())).willAnswer(i -> i.getArgument(0));
        given(progressRepository.save(any())).willAnswer(i -> i.getArgument(0));
    }

    // ── Correct answer ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Correct answer handling")
    class CorrectAnswerTests {

        @Test
        @DisplayName("isCorrect=true for exact match")
        void correctAnswer_exactMatch_isCorrectTrue() {
            AnswerResultDto result = answerService.submitAnswer(
                    sessionId, userId, "20", questionRecord);
            assertThat(result.getIsCorrect()).isTrue();
        }

        @Test
        @DisplayName("isCorrect=true for case-insensitive match (e.g. 'b: 56' vs 'B: 56')")
        void correctAnswer_caseInsensitive_isCorrectTrue() {
            questionRecord.setCorrectAnswer("B: 56");
            AnswerResultDto result = answerService.submitAnswer(
                    sessionId, userId, "b: 56", questionRecord);
            assertThat(result.getIsCorrect()).isTrue();
        }

        @Test
        @DisplayName("isCorrect=true for trimmed whitespace match")
        void correctAnswer_withWhitespace_isCorrectTrue() {
            AnswerResultDto result = answerService.submitAnswer(
                    sessionId, userId, "  20  ", questionRecord);
            assertThat(result.getIsCorrect()).isTrue();
        }

        @Test
        @DisplayName("Correct answer increments masteryCounter by 1")
        void correctAnswer_incrementsMasteryCounter() {
            progress.setMasteryCounter((short) 4);
            AnswerResultDto result = answerService.submitAnswer(
                    sessionId, userId, "20", questionRecord);
            assertThat(result.getMasteryCounter()).isEqualTo((short) 5);
        }

        @Test
        @DisplayName("Correct answer saves question record with isCorrect=true")
        void correctAnswer_savesQuestionRecordAsCorrect() {
            answerService.submitAnswer(sessionId, userId, "20", questionRecord);
            ArgumentCaptor<QuestionHistory> captor =
                    ArgumentCaptor.forClass(QuestionHistory.class);
            then(questionHistoryRepository).should().save(captor.capture());
            assertThat(captor.getValue().getIsCorrect()).isTrue();
            assertThat(captor.getValue().getStudentAnswer()).isEqualTo("20");
        }
    }

    // ── Wrong answer ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Wrong answer handling")
    class WrongAnswerTests {

        @Test
        @DisplayName("isCorrect=false for wrong answer")
        void wrongAnswer_isCorrectFalse() {
            AnswerResultDto result = answerService.submitAnswer(
                    sessionId, userId, "25", questionRecord);
            assertThat(result.getIsCorrect()).isFalse();
        }

        @Test
        @DisplayName("Wrong answer resets masteryCounter to 0")
        void wrongAnswer_resetsMasteryCounter() {
            progress.setMasteryCounter((short) 7);
            AnswerResultDto result = answerService.submitAnswer(
                    sessionId, userId, "99", questionRecord);
            assertThat(result.getMasteryCounter()).isEqualTo((short) 0);
        }

        @Test
        @DisplayName("Wrong answer does not change current level")
        void wrongAnswer_levelUnchanged() {
            progress.setCurrentLevel("MEDIUM");
            AnswerResultDto result = answerService.submitAnswer(
                    sessionId, userId, "99", questionRecord);
            assertThat(result.getNewLevel()).isNull();
        }
    }

    // ── Level progression ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Level progression (mastery target = 10)")
    class LevelProgressionTests {

        @ParameterizedTest
        @CsvSource({
                "EASY,    MEDIUM",
                "MEDIUM,  HARD",
                "HARD,    CHALLENGING"
        })
        @DisplayName("10th correct answer at a level advances to next level")
        void tenCorrectAnswers_advancesToNextLevel(String fromLevel, String expectedNewLevel) {
            progress.setCurrentLevel(fromLevel);
            progress.setMasteryCounter((short) 9); // 9 already correct

            AnswerResultDto result = answerService.submitAnswer(
                    sessionId, userId, "20", questionRecord);

            assertThat(result.getNewLevel()).isEqualTo(expectedNewLevel);
            assertThat(result.getMasteryCounter()).isEqualTo((short) 0); // reset
        }

        @Test
        @DisplayName("10th correct answer at CHALLENGING level marks topic COMPLETED")
        void tenCorrectAtChallenging_completesToopic() {
            progress.setCurrentLevel("CHALLENGING");
            progress.setMasteryCounter((short) 9);

            AnswerResultDto result = answerService.submitAnswer(
                    sessionId, userId, "20", questionRecord);

            assertThat(result.getTopicCompleted()).isTrue();
            assertThat(result.getNewLevel()).isNull(); // no next level

            ArgumentCaptor<UserTopicProgress> captor =
                    ArgumentCaptor.forClass(UserTopicProgress.class);
            then(progressRepository).should().save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
            assertThat(captor.getValue().getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("9 correct answers (counter=9) does NOT advance level yet")
        void nineCorrectAnswers_doesNotAdvanceLevel() {
            progress.setMasteryCounter((short) 8); // 8 so far + this one = 9

            AnswerResultDto result = answerService.submitAnswer(
                    sessionId, userId, "20", questionRecord);

            assertThat(result.getNewLevel()).isNull();
            assertThat(result.getMasteryCounter()).isEqualTo((short) 9);
            assertThat(result.getTopicCompleted()).isFalse();
        }
    }
}
