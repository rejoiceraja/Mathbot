package com.mathbot.api.service;

import com.mathbot.api.dto.response.GradeDto;
import com.mathbot.api.dto.response.TopicWithProgressDto;
import com.mathbot.api.entity.Grade;
import com.mathbot.api.entity.Topic;
import com.mathbot.api.entity.UserTopicProgress;
import com.mathbot.api.exception.ErrorCode;
import com.mathbot.api.exception.MathBotException;
import com.mathbot.api.repository.TopicRepository;
import com.mathbot.api.repository.UserTopicProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CurriculumService {

    private final JpaRepository<Grade, Short> gradeRepository;
    private final TopicRepository topicRepository;
    private final UserTopicProgressRepository progressRepository;

    public List<GradeDto> getAllGrades() {
        return gradeRepository.findAll().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(g -> GradeDto.builder()
                        .id(g.getId())
                        .displayName(g.getDisplayName())
                        .description(g.getDescription())
                        .topicCount((int) topicRepository.countByGradeId(g.getId()))
                        .build())
                .collect(Collectors.toList());
    }

    public List<TopicWithProgressDto> getTopicsWithProgress(Short gradeId, UUID userId) {
        List<Topic> topics = topicRepository.findByGradeIdOrderBySequenceOrderAsc(gradeId);

        Map<UUID, UserTopicProgress> progressMap = progressRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(p -> p.getTopic().getId(), p -> p));

        return topics.stream().map(t -> {
            UserTopicProgress progress = progressMap.get(t.getId());
            return TopicWithProgressDto.builder()
                    .id(t.getId())
                    .name(t.getName())
                    .description(t.getDescription())
                    .sequenceOrder(t.getSequenceOrder())
                    .iconKey(t.getIconKey())
                    .status(progress != null ? progress.getStatus() : "NOT_STARTED")
                    .currentLevel(progress != null ? progress.getCurrentLevel() : "EASY")
                    .masteryCounter(progress != null ? progress.getMasteryCounter() : 0)
                    .diagnosticDone(progress != null && progress.getDiagnosticDone())
                    .build();
        }).collect(Collectors.toList());
    }

    public Topic getTopicOrThrow(UUID topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> MathBotException.notFound(ErrorCode.TOPIC_NOT_FOUND,
                        "Topic not found: " + topicId));
    }
}
