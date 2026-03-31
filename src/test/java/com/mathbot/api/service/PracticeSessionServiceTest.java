package com.mathbot.api.service;

import com.mathbot.api.dto.request.CreateSessionRequest;
import com.mathbot.api.dto.response.PracticeSessionDto;
import com.mathbot.api.dto.response.SessionSummaryDto;
import com.mathbot.api.entity.*;
import com.mathbot.api.exception.ErrorCode;
import com.mathbot.api.exception.MathBotException;
import com.mathbot.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link PracticeSessionService}.
 *
 * Covers session creation, retrieval, completion, and accuracy calculation
 * as defined in TDD §6.4 Practice Session Endpoints.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PracticeSessionService – session lifecycle")
class PracticeSessionServiceTest {

    @Mock private PracticeSessionRepository sessionRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurriculumService curriculumService;
    @Mock private QuestionHistoryRepository questionHistoryRepository;
    @Mock private UserTopicProgressRepository progressRepository;

    @InjectMocks
    private PracticeSessionService practiceSessionService;

    private UUID userId;
    private UUID sessionId;
    private UUID topicId;
    private User user;
    private Topic topic;
    private Grade grade;
    private PracticeSession session;
    private UserTopicProgress progress;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        topicId = UUID.randomUUID();

        grade = Grade.builder().id((short) 4).displayName("Grade 4").build();
        user = User.builder().id(userId).email("s@test.com").build();
        topic = Topic.builder().id(topicId).name("Multiplication").grade(grade).build();

        session = PracticeSession.builder()
                .id(sessionId).user(user).topic(topic)
                .isRetake(false).isDiagnostic(false)
                .startedAt(OffsetDateTime.now().minusMinutes(5))
                .build();

        progress = UserTopicProgress.builder()
                .user(user).topic(topic)
                .currentLevel("EASY").masteryCounter((short) 3)
                .status("IN_PROGRESS")
                .build();
    }

    // ── createSession() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("createSession()")
    class CreateSessionTests {

        @Test
        @DisplayName("Happy path: returns PracticeSessionDto with correct fields")
        void createSession_happyPath_returnsDto() {
            CreateSessionRequest req = new CreateSessionRequest(topicId, false);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(curriculumService.getTopicOrThrow(topicId)).willReturn(topic);
            given(progressRepository.findByUserIdAndTopicId(userId, topicId))
                    .willReturn(Optional.of(progress));
            given(progressRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(sessionRepository.save(any(PracticeSession.class))).willReturn(session);

            PracticeSessionDto result = practiceSessionService.createSession(userId, req);

            assertThat(result.getTopicId()).isEqualTo(topicId);
            assertThat(result.getTopicName()).isEqualTo("Multiplication");
            assertThat(result.getCurrentLevel()).isEqualTo("EASY");
            assertThat(result.getMasteryCounter()).isEqualTo((short) 3);
        }

        @Test
        @DisplayName("Creates new UserTopicProgress when none exists for topic")
        void createSession_noExistingProgress_createsProgress() {
            CreateSessionRequest req = new CreateSessionRequest(topicId, false);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(curriculumService.getTopicOrThrow(topicId)).willReturn(topic);
            given(progressRepository.findByUserIdAndTopicId(userId, topicId))
                    .willReturn(Optional.empty());
            given(progressRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(sessionRepository.save(any())).willReturn(session);

            practiceSessionService.createSession(userId, req);

            ArgumentCaptor<UserTopicProgress> captor =
                    ArgumentCaptor.forClass(UserTopicProgress.class);
            then(progressRepository).should().save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("IN_PROGRESS");
        }

        @Test
        @DisplayName("Sets startedAt on progress when status was NOT_STARTED")
        void createSession_notStartedProgress_setsStartedAt() {
            progress.setStatus("NOT_STARTED");
            CreateSessionRequest req = new CreateSessionRequest(topicId, false);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(curriculumService.getTopicOrThrow(topicId)).willReturn(topic);
            given(progressRepository.findByUserIdAndTopicId(userId, topicId))
                    .willReturn(Optional.of(progress));
            given(progressRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(sessionRepository.save(any())).willReturn(session);

            practiceSessionService.createSession(userId, req);

            assertThat(progress.getStartedAt()).isNotNull();
            assertThat(progress.getStatus()).isEqualTo("IN_PROGRESS");
        }

        @Test
        @DisplayName("Throws USER_NOT_FOUND when user does not exist")
        void createSession_unknownUser_throwsNotFound() {
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatExceptionOfType(MathBotException.class)
                    .isThrownBy(() -> practiceSessionService.createSession(
                            userId, new CreateSessionRequest(topicId, false)))
                    .satisfies(e -> assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }
    }

    // ── getSessionOrThrow() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getSessionOrThrow()")
    class GetSessionTests {

        @Test
        @DisplayName("Returns session when it belongs to the user")
        void getSessionOrThrow_ownSession_returnsSession() {
            given(sessionRepository.findByIdAndUserId(sessionId, userId))
                    .willReturn(Optional.of(session));

            PracticeSession result = practiceSessionService.getSessionOrThrow(sessionId, userId);

            assertThat(result.getId()).isEqualTo(sessionId);
        }

        @Test
        @DisplayName("Throws SESSION_NOT_FOUND when session does not belong to user")
        void getSessionOrThrow_wrongUser_throwsNotFound() {
            given(sessionRepository.findByIdAndUserId(sessionId, userId))
                    .willReturn(Optional.empty());

            assertThatExceptionOfType(MathBotException.class)
                    .isThrownBy(() -> practiceSessionService.getSessionOrThrow(sessionId, userId))
                    .satisfies(e -> assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.SESSION_NOT_FOUND));
        }
    }

    // ── completeSession() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("completeSession()")
    class CompleteSessionTests {

        @Test
        @DisplayName("Returns SessionSummaryDto with correct accuracy calculation")
        void completeSession_calculatesAccuracyCorrectly() {
            given(sessionRepository.findByIdAndUserId(sessionId, userId))
                    .willReturn(Optional.of(session));
            given(progressRepository.findByUserIdAndTopicId(userId, topicId))
                    .willReturn(Optional.of(progress));
            given(sessionRepository.save(any())).willReturn(session);

            List<QuestionHistory> history = List.of(
                    QuestionHistory.builder().isCorrect(true).build(),
                    QuestionHistory.builder().isCorrect(true).build(),
                    QuestionHistory.builder().isCorrect(false).build(),
                    QuestionHistory.builder().isCorrect(true).build()
            );
            given(questionHistoryRepository.findBySessionId(sessionId)).willReturn(history);
            given(questionHistoryRepository.countCorrectBySessionId(sessionId)).willReturn(3L);

            SessionSummaryDto result =
                    practiceSessionService.completeSession(sessionId, userId);

            assertThat(result.getTotalQuestions()).isEqualTo(4);
            assertThat(result.getCorrectAnswers()).isEqualTo(3);
            assertThat(result.getAccuracyPercent()).isEqualTo(75.0);
        }

        @Test
        @DisplayName("Sets endedAt on the session")
        void completeSession_setsEndedAt() {
            given(sessionRepository.findByIdAndUserId(sessionId, userId))
                    .willReturn(Optional.of(session));
            given(progressRepository.findByUserIdAndTopicId(userId, topicId))
                    .willReturn(Optional.of(progress));
            given(sessionRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(questionHistoryRepository.findBySessionId(sessionId)).willReturn(List.of());
            given(questionHistoryRepository.countCorrectBySessionId(sessionId)).willReturn(0L);

            practiceSessionService.completeSession(sessionId, userId);

            ArgumentCaptor<PracticeSession> captor =
                    ArgumentCaptor.forClass(PracticeSession.class);
            then(sessionRepository).should().save(captor.capture());
            assertThat(captor.getValue().getEndedAt()).isNotNull();
        }

        @Test
        @DisplayName("Throws SESSION_ALREADY_ENDED when session already has endedAt")
        void completeSession_alreadyEnded_throwsBadRequest() {
            session.setEndedAt(OffsetDateTime.now().minusMinutes(1));
            given(sessionRepository.findByIdAndUserId(sessionId, userId))
                    .willReturn(Optional.of(session));

            assertThatExceptionOfType(MathBotException.class)
                    .isThrownBy(() -> practiceSessionService.completeSession(sessionId, userId))
                    .satisfies(e -> assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.SESSION_ALREADY_ENDED));
        }

        @Test
        @DisplayName("Accuracy is 0 when no questions were answered")
        void completeSession_noQuestions_zeroAccuracy() {
            given(sessionRepository.findByIdAndUserId(sessionId, userId))
                    .willReturn(Optional.of(session));
            given(progressRepository.findByUserIdAndTopicId(userId, topicId))
                    .willReturn(Optional.of(progress));
            given(sessionRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(questionHistoryRepository.findBySessionId(sessionId)).willReturn(List.of());
            given(questionHistoryRepository.countCorrectBySessionId(sessionId)).willReturn(0L);

            SessionSummaryDto result = practiceSessionService.completeSession(sessionId, userId);

            assertThat(result.getAccuracyPercent()).isEqualTo(0.0);
            assertThat(result.getTotalQuestions()).isEqualTo(0);
        }
    }
}
