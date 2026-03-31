package com.mathbot.api.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathbot.api.entity.Topic;
import com.mathbot.api.repository.QuestionHistoryRepository;
import com.mathbot.api.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private final QuestionHistoryRepository questionHistoryRepository;
    private final TopicRepository topicRepository;
    private final ObjectMapper objectMapper;

    public static final String SYSTEM_PROMPT = """
            You are MathBot, an encouraging and patient mathematics tutor for K-12 students.
            Generate exactly one math question. Respond ONLY with valid JSON in the exact format specified.
            No additional text outside the JSON object.""";

    public String buildUserMessage(UUID userId, Topic topic, String level) {
        List<String> recentStems = questionHistoryRepository
                .findRecentQuestionStemsByUserAndTopic(userId, topic.getId());

        List<Topic> gradeTopics = topicRepository
                .findByGradeIdOrderBySequenceOrderAsc(topic.getGrade().getId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("grade", topic.getGrade().getId());
        payload.put("topic", topic.getName());
        payload.put("difficulty_level", level);
        payload.put("grade_curriculum_topics", gradeTopics.stream()
                .map(Topic::getName).toList());
        payload.put("previously_asked_question_stems", recentStems);
        payload.put("response_format", Map.of(
                "question_text", "string",
                "answer_type", "multiple_choice | open_ended | fill_in_blank",
                "options", List.of("A: ...", "B: ...", "C: ...", "D: ..."),
                "correct_answer", "string",
                "hint", "string"
        ));

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize prompt payload", e);
        }
    }
}
