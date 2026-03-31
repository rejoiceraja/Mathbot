package com.mathbot.api.service;

import com.mathbot.api.dto.request.CreateSessionRequest;
import com.mathbot.api.dto.response.PracticeSessionDto;
import com.mathbot.api.dto.response.SessionSummaryDto;
import com.mathbot.api.entity.*;
import com.mathbot.api.exception.ErrorCode;
import com.mathbot.api.exception.MathBotException;
import com.mathbot.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PracticeSessionService {

    private final PracticeSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final CurriculumService curriculumService;
    private final QuestionHistoryRepository questionHistoryRepository;
    private final UserTopicProgressRepository progressRepository;

    @Transactional
    public PracticeSessionDto createSession(UUID userId, CreateSessionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> MathBotException.notFound(ErrorCode.USER_NOT_FOUND, "User not found"));

        Topic topic = curriculumService.getTopicOrThrow(request.getTopicId());

        UserTopicProgress progress = progressRepository.findByUserIdAndTopicId(userId, topic.getId())
                .orElseGet(() -> UserTopicProgress.builder()
                        .user(user)
                        .topic(topic)
                        .build());

        if ("NOT_STARTED".equals(progress.getStatus())) {
            progress.setStatus("IN_PROGRESS");
            progress.setStartedAt(OffsetDateTime.now());
        }
        progressRepository.save(progress);

        PracticeSession session = PracticeSession.builder()
                .user(user)
                .topic(topic)
                .isRetake(request.isRetake())
                .build();
        session = sessionRepository.save(session);

        return mapToDto(session, progress);
    }

    public PracticeSession getSessionOrThrow(UUID sessionId, UUID userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> MathBotException.notFound(ErrorCode.SESSION_NOT_FOUND,
                        "Session not found: " + sessionId));
    }

    @Transactional
    public SessionSummaryDto completeSession(UUID sessionId, UUID userId) {
        PracticeSession session = getSessionOrThrow(sessionId, userId);

        if (session.getEndedAt() != null) {
            throw MathBotException.badRequest(ErrorCode.SESSION_ALREADY_ENDED,
                    "Session already completed");
        }

        session.setEndedAt(OffsetDateTime.now());

        UserTopicProgress progress = progressRepository
                .findByUserIdAndTopicId(userId, session.getTopic().getId())
                .orElseThrow();

        session.setFinalLevelReached(progress.getCurrentLevel());
        sessionRepository.save(session);

        List<QuestionHistory> history = questionHistoryRepository.findBySessionId(sessionId);
        int total = history.size();
        long correct = questionHistoryRepository.countCorrectBySessionId(sessionId);
        double accuracy = total > 0 ? (correct * 100.0 / total) : 0;
        long durationSecs = Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds();

        return SessionSummaryDto.builder()
                .sessionId(sessionId)
                .totalQuestions(total)
                .correctAnswers((int) correct)
                .accuracyPercent(accuracy)
                .durationSeconds(durationSecs)
                .finalLevelReached(progress.getCurrentLevel())
                .topicCompleted("COMPLETED".equals(progress.getStatus()))
                .build();
    }

    private PracticeSessionDto mapToDto(PracticeSession session, UserTopicProgress progress) {
        return PracticeSessionDto.builder()
                .id(session.getId())
                .topicId(session.getTopic().getId())
                .topicName(session.getTopic().getName())
                .currentLevel(progress.getCurrentLevel())
                .masteryCounter(progress.getMasteryCounter())
                .isDiagnostic(session.getIsDiagnostic())
                .isRetake(session.getIsRetake())
                .startedAt(session.getStartedAt())
                .build();
    }
}
