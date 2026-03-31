package com.mathbot.api.service;

import com.mathbot.api.dto.response.DashboardDto;
import com.mathbot.api.dto.response.TopicWithProgressDto;
import com.mathbot.api.entity.User;
import com.mathbot.api.exception.ErrorCode;
import com.mathbot.api.exception.MathBotException;
import com.mathbot.api.repository.TopicRepository;
import com.mathbot.api.repository.UserRepository;
import com.mathbot.api.repository.UserTopicProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final UserTopicProgressRepository progressRepository;
    private final CurriculumService curriculumService;

    public DashboardDto getDashboard(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> MathBotException.notFound(ErrorCode.USER_NOT_FOUND,
                        "User not found: " + userId));

        Short gradeId = user.getCurrentGrade();
        int totalTopics = gradeId != null
                ? (int) topicRepository.countByGradeId(gradeId)
                : 0;

        long completed = progressRepository.countCompletedByUserId(userId);
        long inProgress = progressRepository.countInProgressByUserId(userId);

        double overallProgress = totalTopics > 0 ? (completed * 100.0 / totalTopics) : 0;

        List<TopicWithProgressDto> topics = gradeId != null
                ? curriculumService.getTopicsWithProgress(gradeId, userId)
                : List.of();

        return DashboardDto.builder()
                .totalTopics(totalTopics)
                .completedTopics((int) completed)
                .inProgressTopics((int) inProgress)
                .overallProgressPercent(overallProgress)
                .topics(topics)
                .build();
    }
}
