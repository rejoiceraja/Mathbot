package com.mathbot.api.llm;

import com.mathbot.api.dto.response.QuestionDto;
import com.mathbot.api.entity.StaticQuestion;
import com.mathbot.api.exception.ErrorCode;
import com.mathbot.api.exception.MathBotException;
import com.mathbot.api.repository.StaticQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FallbackQuestionService {

    private final StaticQuestionRepository staticQuestionRepository;

    public QuestionDto getFallbackQuestion(UUID topicId, String level, List<UUID> recentIds) {
        List<StaticQuestion> candidates =
                staticQuestionRepository.findByTopicAndLevelExcluding(topicId, level, recentIds);

        if (candidates.isEmpty()) {
            candidates = staticQuestionRepository.findByTopicIdAndLevel(topicId, level);
        }

        if (candidates.isEmpty()) {
            throw MathBotException.serviceUnavailable(ErrorCode.LLM_UNAVAILABLE,
                    "No questions available for topic/level: " + topicId + "/" + level);
        }

        Collections.shuffle(candidates);
        StaticQuestion q = candidates.get(0);

        return QuestionDto.builder()
                .questionId(q.getId())
                .questionText(q.getQuestionText())
                .answerType("multiple_choice")
                .hint(null)
                .build();
    }
}
