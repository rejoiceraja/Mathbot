package com.mathbot.api.service;

import com.mathbot.api.dto.response.AnswerResultDto;
import com.mathbot.api.entity.*;
import com.mathbot.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnswerService {

    private static final short MASTERY_TARGET = 10;
    private static final String[] LEVEL_PROGRESSION = {"EASY", "MEDIUM", "HARD", "CHALLENGING"};

    private final UserTopicProgressRepository progressRepository;
    private final QuestionHistoryRepository questionHistoryRepository;
    private final PracticeSessionService sessionService;

    @Transactional
    public AnswerResultDto submitAnswer(UUID sessionId, UUID userId,
                                       String studentAnswer, QuestionHistory questionRecord) {
        PracticeSession session = sessionService.getSessionOrThrow(sessionId, userId);
        UserTopicProgress progress = progressRepository
                .findByUserIdAndTopicId(userId, session.getTopic().getId())
                .orElseThrow();

        boolean isCorrect = questionRecord.getCorrectAnswer()
                .trim().equalsIgnoreCase(studentAnswer.trim());

        questionRecord.setStudentAnswer(studentAnswer);
        questionRecord.setIsCorrect(isCorrect);
        questionHistoryRepository.save(questionRecord);

        String newLevel = null;
        boolean topicCompleted = false;

        if (isCorrect) {
            short counter = (short) (progress.getMasteryCounter() + 1);
            progress.setMasteryCounter(counter);

            if (counter >= MASTERY_TARGET) {
                String next = nextLevel(progress.getCurrentLevel());
                if (next == null) {
                    progress.setStatus("COMPLETED");
                    progress.setCompletedAt(OffsetDateTime.now());
                    topicCompleted = true;
                } else {
                    progress.setCurrentLevel(next);
                    progress.setMasteryCounter((short) 0);
                    newLevel = next;
                }
            }
        } else {
            progress.setMasteryCounter((short) 0);
        }
        progress.setUpdatedAt(OffsetDateTime.now());
        progressRepository.save(progress);

        return AnswerResultDto.builder()
                .isCorrect(isCorrect)
                .masteryCounter(progress.getMasteryCounter())
                .newLevel(newLevel)
                .topicCompleted(topicCompleted)
                .build();
    }

    private String nextLevel(String current) {
        for (int i = 0; i < LEVEL_PROGRESSION.length - 1; i++) {
            if (LEVEL_PROGRESSION[i].equals(current)) {
                return LEVEL_PROGRESSION[i + 1];
            }
        }
        return null;
    }
}
