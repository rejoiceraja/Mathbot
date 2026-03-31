package com.mathbot.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathbot.api.dto.request.CreateSessionRequest;
import com.mathbot.api.dto.response.PracticeSessionDto;
import com.mathbot.api.dto.response.QuestionDto;
import com.mathbot.api.dto.response.SessionSummaryDto;
import com.mathbot.api.entity.*;
import com.mathbot.api.exception.ErrorCode;
import com.mathbot.api.exception.GlobalExceptionHandler;
import com.mathbot.api.exception.MathBotException;
import com.mathbot.api.llm.LLMGatewayService;
import com.mathbot.api.repository.QuestionHistoryRepository;
import com.mathbot.api.service.AnswerService;
import com.mathbot.api.service.CurriculumService;
import com.mathbot.api.service.PracticeSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link PracticeController} using standalone MockMvc.
 *
 * Covers session creation, question retrieval, session completion, and error cases
 * (TDD §6.4 Practice Session Endpoints).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PracticeController – practice session endpoints")
class PracticeControllerTest {

    @Mock private PracticeSessionService sessionService;
    @Mock private LLMGatewayService llmGatewayService;
    @Mock private AnswerService answerService;
    @Mock private CurriculumService curriculumService;
    @Mock private QuestionHistoryRepository questionHistoryRepository;

    @InjectMocks private PracticeController practiceController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private String userId;
    private UUID sessionId;
    private UUID topicId;
    private UsernamePasswordAuthenticationToken auth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(practiceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        userId = UUID.randomUUID().toString();
        sessionId = UUID.randomUUID();
        topicId = UUID.randomUUID();
        auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    // ── POST /sessions ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/practice/sessions")
    class CreateSessionTests {

        @Test
        @DisplayName("Valid request returns 201 with session DTO")
        void createSession_validRequest_returns201() throws Exception {
            CreateSessionRequest req = new CreateSessionRequest(topicId, false);
            PracticeSessionDto dto = PracticeSessionDto.builder()
                    .id(sessionId)
                    .topicId(topicId)
                    .topicName("Multiplication")
                    .currentLevel("EASY")
                    .masteryCounter((short) 0)
                    .isDiagnostic(false)
                    .isRetake(false)
                    .startedAt(OffsetDateTime.now())
                    .build();

            given(sessionService.createSession(any(UUID.class), any(CreateSessionRequest.class)))
                    .willReturn(dto);

            mockMvc.perform(post("/api/v1/practice/sessions")
                            .with(authentication(auth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.topicName").value("Multiplication"))
                    .andExpect(jsonPath("$.data.currentLevel").value("EASY"));
        }

        @Test
        @DisplayName("Missing topicId returns 400 Bad Request")
        void createSession_missingTopicId_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/practice/sessions")
                            .with(authentication(auth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"isRetake\":false}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Unknown topic returns 404")
        void createSession_unknownTopic_returns404() throws Exception {
            CreateSessionRequest req = new CreateSessionRequest(UUID.randomUUID(), false);
            given(sessionService.createSession(any(), any()))
                    .willThrow(MathBotException.notFound(
                            ErrorCode.TOPIC_NOT_FOUND, "Topic not found"));

            mockMvc.perform(post("/api/v1/practice/sessions")
                            .with(authentication(auth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("TOPIC_NOT_FOUND"));
        }
    }

    // ── GET /sessions/{id}/question ───────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/practice/sessions/{id}/question")
    class GetQuestionTests {

        @Test
        @DisplayName("Returns 200 with question DTO (no correct_answer field)")
        void getQuestion_validSession_returns200WithQuestion() throws Exception {
            Grade grade = Grade.builder().id((short) 4).displayName("Grade 4").build();
            Topic topic = Topic.builder().id(topicId).name("Multiplication")
                    .grade(grade).build();
            PracticeSession session = PracticeSession.builder()
                    .id(sessionId).topic(topic)
                    .user(User.builder().id(UUID.fromString(userId)).build())
                    .build();

            QuestionDto question = QuestionDto.builder()
                    .questionText("What is 8 × 9?")
                    .answerType("multiple_choice")
                    .options(List.of("A: 63", "B: 72", "C: 81", "D: 56"))
                    .hint("Think of the 9 times table")
                    .level("EASY")
                    .build();

            given(sessionService.getSessionOrThrow(sessionId, UUID.fromString(userId)))
                    .willReturn(session);
            given(llmGatewayService.generateQuestion(any(UUID.class), eq(topic), anyString()))
                    .willReturn(question);

            mockMvc.perform(get("/api/v1/practice/sessions/{id}/question", sessionId)
                            .with(authentication(auth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.questionText").value("What is 8 × 9?"))
                    .andExpect(jsonPath("$.data.answerType").value("multiple_choice"))
                    .andExpect(jsonPath("$.data.options").isArray());
        }

        @Test
        @DisplayName("Returns 404 when session does not belong to user")
        void getQuestion_wrongUser_returns404() throws Exception {
            given(sessionService.getSessionOrThrow(any(), any()))
                    .willThrow(MathBotException.notFound(
                            ErrorCode.SESSION_NOT_FOUND, "Session not found"));

            mockMvc.perform(get("/api/v1/practice/sessions/{id}/question", sessionId)
                            .with(authentication(auth)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        }
    }

    // ── POST /sessions/{id}/complete ──────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/practice/sessions/{id}/complete")
    class CompleteSessionTests {

        @Test
        @DisplayName("Returns 200 with session summary including accuracy")
        void completeSession_validSession_returns200WithSummary() throws Exception {
            SessionSummaryDto summary = SessionSummaryDto.builder()
                    .sessionId(sessionId)
                    .totalQuestions(10)
                    .correctAnswers(8)
                    .accuracyPercent(80.0)
                    .durationSeconds(300L)
                    .finalLevelReached("HARD")
                    .topicCompleted(false)
                    .build();

            given(sessionService.completeSession(sessionId, UUID.fromString(userId)))
                    .willReturn(summary);

            mockMvc.perform(post("/api/v1/practice/sessions/{id}/complete", sessionId)
                            .with(authentication(auth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalQuestions").value(10))
                    .andExpect(jsonPath("$.data.correctAnswers").value(8))
                    .andExpect(jsonPath("$.data.accuracyPercent").value(80.0))
                    .andExpect(jsonPath("$.data.finalLevelReached").value("HARD"));
        }

        @Test
        @DisplayName("Returns 400 when session is already completed")
        void completeSession_alreadyEnded_returns400() throws Exception {
            given(sessionService.completeSession(any(), any()))
                    .willThrow(MathBotException.badRequest(
                            ErrorCode.SESSION_ALREADY_ENDED, "Session already completed"));

            mockMvc.perform(post("/api/v1/practice/sessions/{id}/complete", sessionId)
                            .with(authentication(auth)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("SESSION_ALREADY_ENDED"));
        }
    }
}
