package com.mathbot.api.service;

import com.mathbot.api.dto.response.GradeDto;
import com.mathbot.api.dto.response.TopicWithProgressDto;
import com.mathbot.api.entity.Grade;
import com.mathbot.api.entity.Topic;
import com.mathbot.api.entity.UserTopicProgress;
import com.mathbot.api.exception.MathBotException;
import com.mathbot.api.repository.TopicRepository;
import com.mathbot.api.repository.UserTopicProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link CurriculumService}.
 *
 * Covers grade listing, topic-with-progress retrieval, and topic lookup
 * as per TDD §6.3 Grade & Topic Endpoints.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CurriculumService – grade and topic retrieval")
class CurriculumServiceTest {

    @Mock private JpaRepository<Grade, Short> gradeRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private UserTopicProgressRepository progressRepository;

    private CurriculumService curriculumService;

    private Grade grade4;
    private Topic multiplicationTopic;
    private UUID userId;

    @BeforeEach
    void setUp() {
        curriculumService = new CurriculumService(gradeRepository, topicRepository, progressRepository);
        userId = UUID.randomUUID();

        grade4 = Grade.builder()
                .id((short) 4)
                .displayName("Grade 4")
                .description("Year 4 mathematics")
                .build();

        multiplicationTopic = Topic.builder()
                .id(UUID.randomUUID())
                .grade(grade4)
                .name("Multiplication")
                .description("Times tables and multiplication")
                .sequenceOrder((short) 3)
                .iconKey("multiply")
                .isActive(true)
                .build();
    }

    // ── getAllGrades() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllGrades()")
    class GetAllGradesTests {

        @Test
        @DisplayName("Returns GradeDtos sorted by grade id")
        void getAllGrades_returnsSortedList() {
            Grade grade1 = Grade.builder().id((short) 1).displayName("Grade 1").build();
            Grade grade2 = Grade.builder().id((short) 2).displayName("Grade 2").build();
            given(gradeRepository.findAll()).willReturn(List.of(grade2, grade1)); // unsorted
            given(topicRepository.countByGradeId(anyShort())).willReturn(5L);

            List<GradeDto> result = curriculumService.getAllGrades();

            assertThat(result).extracting(GradeDto::getId)
                    .containsExactly((short) 1, (short) 2);
        }

        @Test
        @DisplayName("Each GradeDto includes topic count from repository")
        void getAllGrades_includesTopicCount() {
            given(gradeRepository.findAll()).willReturn(List.of(grade4));
            given(topicRepository.countByGradeId((short) 4)).willReturn(8L);

            List<GradeDto> result = curriculumService.getAllGrades();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTopicCount()).isEqualTo(8);
        }

        @Test
        @DisplayName("Returns empty list when no grades exist")
        void getAllGrades_noGrades_returnsEmptyList() {
            given(gradeRepository.findAll()).willReturn(Collections.emptyList());

            assertThat(curriculumService.getAllGrades()).isEmpty();
        }
    }

    // ── getTopicsWithProgress() ───────────────────────────────────────────────

    @Nested
    @DisplayName("getTopicsWithProgress()")
    class GetTopicsWithProgressTests {

        @Test
        @DisplayName("Returns topics with NOT_STARTED status when no progress exists for user")
        void getTopicsWithProgress_noProgress_allNotStarted() {
            given(topicRepository.findByGradeIdOrderBySequenceOrderAsc((short) 4))
                    .willReturn(List.of(multiplicationTopic));
            given(progressRepository.findByUserId(userId))
                    .willReturn(Collections.emptyList());

            List<TopicWithProgressDto> result =
                    curriculumService.getTopicsWithProgress((short) 4, userId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo("NOT_STARTED");
            assertThat(result.get(0).getCurrentLevel()).isEqualTo("EASY");
            assertThat(result.get(0).getMasteryCounter()).isEqualTo((short) 0);
        }

        @Test
        @DisplayName("Returns correct status and level when progress record exists")
        void getTopicsWithProgress_withProgress_mapsCorrectly() {
            UserTopicProgress progress = UserTopicProgress.builder()
                    .user(null)
                    .topic(multiplicationTopic)
                    .status("IN_PROGRESS")
                    .currentLevel("MEDIUM")
                    .masteryCounter((short) 5)
                    .diagnosticDone(true)
                    .build();

            given(topicRepository.findByGradeIdOrderBySequenceOrderAsc((short) 4))
                    .willReturn(List.of(multiplicationTopic));
            given(progressRepository.findByUserId(userId))
                    .willReturn(List.of(progress));

            List<TopicWithProgressDto> result =
                    curriculumService.getTopicsWithProgress((short) 4, userId);

            TopicWithProgressDto dto = result.get(0);
            assertThat(dto.getStatus()).isEqualTo("IN_PROGRESS");
            assertThat(dto.getCurrentLevel()).isEqualTo("MEDIUM");
            assertThat(dto.getMasteryCounter()).isEqualTo((short) 5);
            assertThat(dto.getDiagnosticDone()).isTrue();
        }

        @Test
        @DisplayName("Topics are returned in sequence_order from repository")
        void getTopicsWithProgress_maintainsSequenceOrder() {
            Topic topic1 = Topic.builder().id(UUID.randomUUID()).grade(grade4)
                    .name("Addition").sequenceOrder((short) 1).isActive(true).build();
            Topic topic2 = Topic.builder().id(UUID.randomUUID()).grade(grade4)
                    .name("Subtraction").sequenceOrder((short) 2).isActive(true).build();

            given(topicRepository.findByGradeIdOrderBySequenceOrderAsc((short) 4))
                    .willReturn(List.of(topic1, topic2));
            given(progressRepository.findByUserId(userId)).willReturn(List.of());

            List<TopicWithProgressDto> result =
                    curriculumService.getTopicsWithProgress((short) 4, userId);

            assertThat(result).extracting(TopicWithProgressDto::getName)
                    .containsExactly("Addition", "Subtraction");
        }

        @Test
        @DisplayName("Returns empty list when grade has no topics")
        void getTopicsWithProgress_noTopics_returnsEmptyList() {
            given(topicRepository.findByGradeIdOrderBySequenceOrderAsc((short) 4))
                    .willReturn(Collections.emptyList());
            given(progressRepository.findByUserId(userId)).willReturn(List.of());

            assertThat(curriculumService.getTopicsWithProgress((short) 4, userId)).isEmpty();
        }
    }

    // ── getTopicOrThrow() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTopicOrThrow()")
    class GetTopicOrThrowTests {

        @Test
        @DisplayName("Returns topic when it exists")
        void getTopicOrThrow_existingTopic_returnsTopic() {
            UUID topicId = multiplicationTopic.getId();
            given(topicRepository.findById(topicId)).willReturn(Optional.of(multiplicationTopic));

            Topic result = curriculumService.getTopicOrThrow(topicId);

            assertThat(result.getName()).isEqualTo("Multiplication");
        }

        @Test
        @DisplayName("Throws MathBotException with TOPIC_NOT_FOUND for unknown id")
        void getTopicOrThrow_unknownTopic_throwsNotFound() {
            UUID unknownId = UUID.randomUUID();
            given(topicRepository.findById(unknownId)).willReturn(Optional.empty());

            assertThatExceptionOfType(MathBotException.class)
                    .isThrownBy(() -> curriculumService.getTopicOrThrow(unknownId))
                    .satisfies(e -> assertThat(e.getErrorCode())
                            .isEqualTo(com.mathbot.api.exception.ErrorCode.TOPIC_NOT_FOUND));
        }
    }
}
