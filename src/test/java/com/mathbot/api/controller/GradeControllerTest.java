package com.mathbot.api.controller;

import com.mathbot.api.dto.response.GradeDto;
import com.mathbot.api.dto.response.TopicWithProgressDto;
import com.mathbot.api.exception.ErrorCode;
import com.mathbot.api.exception.GlobalExceptionHandler;
import com.mathbot.api.exception.MathBotException;
import com.mathbot.api.service.CurriculumService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link GradeController} using standalone MockMvc.
 *
 * Covers grade listing and topic-with-progress retrieval
 * (TDD §6.3 Grade & Topic Endpoints).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GradeController – grade and topic endpoints")
class GradeControllerTest {

    @Mock private CurriculumService curriculumService;
    @InjectMocks private GradeController gradeController;

    private MockMvc mockMvc;
    private String userId;
    private UsernamePasswordAuthenticationToken auth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(gradeController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        userId = UUID.randomUUID().toString();
        auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    // ── GET /grades ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/grades")
    class GetGradesTests {

        @Test
        @DisplayName("Returns 200 with list of all grades sorted by id")
        void getGrades_returnsGradeList() throws Exception {
            List<GradeDto> grades = List.of(
                    GradeDto.builder().id((short) 1).displayName("Grade 1").topicCount(6).build(),
                    GradeDto.builder().id((short) 2).displayName("Grade 2").topicCount(7).build()
            );
            given(curriculumService.getAllGrades()).willReturn(grades);

            mockMvc.perform(get("/api/v1/grades").with(authentication(auth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].displayName").value("Grade 1"))
                    .andExpect(jsonPath("$.data[0].topicCount").value(6))
                    .andExpect(jsonPath("$.data[1].id").value(2));
        }

        @Test
        @DisplayName("Returns 200 with empty list when no grades are configured")
        void getGrades_noGrades_returnsEmptyList() throws Exception {
            given(curriculumService.getAllGrades()).willReturn(List.of());

            mockMvc.perform(get("/api/v1/grades").with(authentication(auth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }
    }

    // ── GET /grades/{gradeId}/topics ──────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/grades/{gradeId}/topics")
    class GetTopicsTests {

        @Test
        @DisplayName("Returns 200 with topics and user progress for authenticated user")
        void getTopics_returnsTopicList() throws Exception {
            List<TopicWithProgressDto> topics = List.of(
                    TopicWithProgressDto.builder()
                            .id(UUID.randomUUID())
                            .name("Addition").status("COMPLETED")
                            .currentLevel("CHALLENGING").masteryCounter((short) 10).build(),
                    TopicWithProgressDto.builder()
                            .id(UUID.randomUUID())
                            .name("Multiplication").status("IN_PROGRESS")
                            .currentLevel("MEDIUM").masteryCounter((short) 4).build()
            );
            given(curriculumService.getTopicsWithProgress(eq((short) 4), any(UUID.class)))
                    .willReturn(topics);

            mockMvc.perform(get("/api/v1/grades/4/topics").with(authentication(auth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].name").value("Addition"))
                    .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data[1].name").value("Multiplication"))
                    .andExpect(jsonPath("$.data[1].currentLevel").value("MEDIUM"));
        }

        @Test
        @DisplayName("Returns 200 with empty list when grade has no topics")
        void getTopics_noTopics_returnsEmptyList() throws Exception {
            given(curriculumService.getTopicsWithProgress(any(), any()))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/v1/grades/1/topics").with(authentication(auth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }

        @Test
        @DisplayName("Returns 404 when grade does not exist (propagated from service)")
        void getTopics_unknownGrade_returns404() throws Exception {
            given(curriculumService.getTopicsWithProgress(eq((short) 99), any()))
                    .willThrow(MathBotException.notFound(
                            ErrorCode.GRADE_NOT_FOUND, "Grade not found: 99"));

            mockMvc.perform(get("/api/v1/grades/99/topics").with(authentication(auth)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("GRADE_NOT_FOUND"));
        }
    }
}
