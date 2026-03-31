package com.mathbot.api.service;

import com.mathbot.api.dto.response.DashboardDto;
import com.mathbot.api.dto.response.TopicWithProgressDto;
import com.mathbot.api.entity.User;
import com.mathbot.api.exception.ErrorCode;
import com.mathbot.api.exception.MathBotException;
import com.mathbot.api.repository.TopicRepository;
import com.mathbot.api.repository.UserRepository;
import com.mathbot.api.repository.UserTopicProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link ProgressService}.
 *
 * Verifies dashboard aggregation, progress percentage calculation, and edge cases
 * as per TDD §6 REST API Design – progress endpoint.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProgressService – dashboard aggregation")
class ProgressServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private UserTopicProgressRepository progressRepository;
    @Mock private CurriculumService curriculumService;

    @InjectMocks
    private ProgressService progressService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("s@test.com")
                .currentGrade((short) 4)
                .build();
    }

    @Test
    @DisplayName("getDashboard() calculates overall progress percentage correctly")
    void getDashboard_calculatesProgressPercentage() {
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(topicRepository.countByGradeId((short) 4)).willReturn(8L);
        given(progressRepository.countCompletedByUserId(userId)).willReturn(4L);
        given(progressRepository.countInProgressByUserId(userId)).willReturn(2L);
        given(curriculumService.getTopicsWithProgress(eq((short) 4), eq(userId)))
                .willReturn(List.of());

        DashboardDto result = progressService.getDashboard(userId);

        assertThat(result.getTotalTopics()).isEqualTo(8);
        assertThat(result.getCompletedTopics()).isEqualTo(4);
        assertThat(result.getInProgressTopics()).isEqualTo(2);
        assertThat(result.getOverallProgressPercent()).isEqualTo(50.0); // 4/8 * 100
    }

    @Test
    @DisplayName("getDashboard() returns 100% when all topics are completed")
    void getDashboard_allCompleted_returns100Percent() {
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(topicRepository.countByGradeId((short) 4)).willReturn(5L);
        given(progressRepository.countCompletedByUserId(userId)).willReturn(5L);
        given(progressRepository.countInProgressByUserId(userId)).willReturn(0L);
        given(curriculumService.getTopicsWithProgress(any(), any())).willReturn(List.of());

        DashboardDto result = progressService.getDashboard(userId);

        assertThat(result.getOverallProgressPercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("getDashboard() returns 0% when no progress exists")
    void getDashboard_noProgress_returnsZeroPercent() {
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(topicRepository.countByGradeId((short) 4)).willReturn(6L);
        given(progressRepository.countCompletedByUserId(userId)).willReturn(0L);
        given(progressRepository.countInProgressByUserId(userId)).willReturn(0L);
        given(curriculumService.getTopicsWithProgress(any(), any())).willReturn(List.of());

        DashboardDto result = progressService.getDashboard(userId);

        assertThat(result.getOverallProgressPercent()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getDashboard() returns 0% and empty topics when user has no grade set")
    void getDashboard_noGradeSet_returnsZeroPercent() {
        user.setCurrentGrade(null);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        DashboardDto result = progressService.getDashboard(userId);

        assertThat(result.getTotalTopics()).isEqualTo(0);
        assertThat(result.getOverallProgressPercent()).isEqualTo(0.0);
        assertThat(result.getTopics()).isEmpty();
        then(topicRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("getDashboard() includes topic list from CurriculumService")
    void getDashboard_includesTopicList() {
        TopicWithProgressDto t1 = TopicWithProgressDto.builder()
                .name("Addition").status("COMPLETED").build();
        TopicWithProgressDto t2 = TopicWithProgressDto.builder()
                .name("Subtraction").status("IN_PROGRESS").build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(topicRepository.countByGradeId((short) 4)).willReturn(2L);
        given(progressRepository.countCompletedByUserId(userId)).willReturn(1L);
        given(progressRepository.countInProgressByUserId(userId)).willReturn(1L);
        given(curriculumService.getTopicsWithProgress((short) 4, userId))
                .willReturn(List.of(t1, t2));

        DashboardDto result = progressService.getDashboard(userId);

        assertThat(result.getTopics()).hasSize(2)
                .extracting(TopicWithProgressDto::getName)
                .containsExactly("Addition", "Subtraction");
    }

    @Test
    @DisplayName("getDashboard() throws USER_NOT_FOUND for unknown userId")
    void getDashboard_unknownUser_throwsNotFound() {
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatExceptionOfType(MathBotException.class)
                .isThrownBy(() -> progressService.getDashboard(userId))
                .satisfies(e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
