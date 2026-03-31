package com.mathbot.api.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathbot.api.dto.response.QuestionDto;
import com.mathbot.api.exception.LLMParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LLMResponseParser {

    private final ObjectMapper objectMapper;

    /**
     * Parses the raw Claude API response JSON envelope and extracts a QuestionDto.
     * Throws LLMParseException (triggering retry) if the response is invalid or missing fields.
     */
    public QuestionDto parseClaudeResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);

            // Claude API wraps content in content[0].text
            JsonNode contentNode = root.path("content").get(0).path("text");
            String questionJson = contentNode.asText();

            return parseQuestionJson(questionJson);
        } catch (LLMParseException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMParseException("Failed to parse Claude API response: " + e.getMessage(), e);
        }
    }

    public QuestionDto parseQuestionJson(String questionJson) {
        try {
            JsonNode node = objectMapper.readTree(questionJson);

            String questionText = requireText(node, "question_text");
            String answerType = requireText(node, "answer_type");
            String correctAnswer = requireText(node, "correct_answer");
            String hint = node.path("hint").asText(null);

            List<String> options = new ArrayList<>();
            JsonNode optionsNode = node.path("options");
            if (optionsNode.isArray()) {
                optionsNode.forEach(o -> options.add(o.asText()));
            }

            return QuestionDto.builder()
                    .questionText(questionText)
                    .answerType(answerType)
                    .options(options.isEmpty() ? null : options)
                    .hint(hint)
                    .build();
        } catch (LLMParseException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMParseException("Invalid question JSON: " + e.getMessage(), e);
        }
    }

    private String requireText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new LLMParseException("Missing required field: " + field);
        }
        return value.asText();
    }
}
