package com.mathbot.api.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathbot.api.dto.response.QuestionDto;
import com.mathbot.api.exception.LLMParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link LLMResponseParser}.
 *
 * Verifies JSON parsing, field validation, and error signalling as required
 * by TDD §4 LLM Gateway Design. LLMParseException triggers Resilience4j retry.
 */
@DisplayName("LLMResponseParser – Claude API response parsing")
class LLMResponseParserTest {

    private LLMResponseParser parser;

    // Realistic Claude API response envelope
    private static final String VALID_CLAUDE_RESPONSE = """
            {
              "id": "msg_01XFDUDYJgAACzvnptvVoYEL",
              "type": "message",
              "role": "assistant",
              "content": [
                {
                  "type": "text",
                  "text": "{\\"question_text\\":\\"What is 7 × 8?\\",\\"answer_type\\":\\"multiple_choice\\",\\"options\\":[\\"A: 48\\",\\"B: 56\\",\\"C: 54\\",\\"D: 64\\"],\\"correct_answer\\":\\"B: 56\\",\\"hint\\":\\"Think of the 7 times table.\\"}"
                }
              ],
              "model": "claude-3-5-sonnet-20241022",
              "stop_reason": "end_turn"
            }
            """;

    private static final String VALID_QUESTION_JSON = """
            {
              "question_text": "What is 7 × 8?",
              "answer_type": "multiple_choice",
              "options": ["A: 48", "B: 56", "C: 54", "D: 64"],
              "correct_answer": "B: 56",
              "hint": "Think of the 7 times table."
            }
            """;

    @BeforeEach
    void setUp() {
        parser = new LLMResponseParser(new ObjectMapper());
    }

    // ── parseClaudeResponse (envelope) ────────────────────────────────────────

    @Test
    @DisplayName("parseClaudeResponse() extracts question from valid Claude API envelope")
    void parseClaudeResponse_validEnvelope_returnsQuestionDto() {
        QuestionDto dto = parser.parseClaudeResponse(VALID_CLAUDE_RESPONSE);

        assertThat(dto.getQuestionText()).isEqualTo("What is 7 × 8?");
        assertThat(dto.getAnswerType()).isEqualTo("multiple_choice");
        assertThat(dto.getOptions()).containsExactly("A: 48", "B: 56", "C: 54", "D: 64");
        assertThat(dto.getHint()).isEqualTo("Think of the 7 times table.");
    }

    @Test
    @DisplayName("parseClaudeResponse() throws LLMParseException for invalid JSON envelope")
    void parseClaudeResponse_invalidJson_throwsLLMParseException() {
        assertThatExceptionOfType(LLMParseException.class)
                .isThrownBy(() -> parser.parseClaudeResponse("not-json"));
    }

    @Test
    @DisplayName("parseClaudeResponse() throws LLMParseException when content array is empty")
    void parseClaudeResponse_emptyContentArray_throwsLLMParseException() {
        String emptyContent = """
                {"content": [], "type": "message"}
                """;
        assertThatExceptionOfType(LLMParseException.class)
                .isThrownBy(() -> parser.parseClaudeResponse(emptyContent));
    }

    // ── parseQuestionJson ─────────────────────────────────────────────────────

    @Test
    @DisplayName("parseQuestionJson() parses all fields from valid JSON")
    void parseQuestionJson_validJson_allFieldsMapped() {
        QuestionDto dto = parser.parseQuestionJson(VALID_QUESTION_JSON);

        assertThat(dto.getQuestionText()).isEqualTo("What is 7 × 8?");
        assertThat(dto.getAnswerType()).isEqualTo("multiple_choice");
        assertThat(dto.getOptions()).hasSize(4);
        assertThat(dto.getHint()).isNotBlank();
    }

    @Test
    @DisplayName("parseQuestionJson() returns null options list for open_ended question")
    void parseQuestionJson_openEnded_noOptions() {
        String json = """
                {
                  "question_text": "What is the sum of angles in a triangle?",
                  "answer_type": "open_ended",
                  "correct_answer": "180 degrees",
                  "hint": "Think about straight lines."
                }
                """;
        QuestionDto dto = parser.parseQuestionJson(json);
        assertThat(dto.getOptions()).isNullOrEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"question_text", "answer_type", "correct_answer"})
    @DisplayName("parseQuestionJson() throws LLMParseException when required field is missing")
    void parseQuestionJson_missingRequiredField_throwsLLMParseException(String missingField) {
        // Build JSON with the given field set to null
        String json = String.format("""
                {
                  "question_text": "%s",
                  "answer_type": "%s",
                  "correct_answer": "%s"
                }
                """,
                missingField.equals("question_text") ? "" : "What is 5+5?",
                missingField.equals("answer_type") ? "" : "open_ended",
                missingField.equals("correct_answer") ? "" : "10"
        );
        assertThatExceptionOfType(LLMParseException.class)
                .isThrownBy(() -> parser.parseQuestionJson(json));
    }

    @Test
    @DisplayName("parseQuestionJson() throws LLMParseException for completely empty JSON object")
    void parseQuestionJson_emptyObject_throwsLLMParseException() {
        assertThatExceptionOfType(LLMParseException.class)
                .isThrownBy(() -> parser.parseQuestionJson("{}"));
    }

    @Test
    @DisplayName("parseQuestionJson() hint field is optional and can be absent")
    void parseQuestionJson_noHintField_noException() {
        String json = """
                {
                  "question_text": "Solve: 3x = 12",
                  "answer_type": "fill_in_blank",
                  "correct_answer": "4"
                }
                """;
        QuestionDto dto = parser.parseQuestionJson(json);
        assertThat(dto.getQuestionText()).isEqualTo("Solve: 3x = 12");
        assertThat(dto.getHint()).isNull();
    }

    @Test
    @DisplayName("parseQuestionJson() handles unicode math characters in question text")
    void parseQuestionJson_unicodeMath_parsedCorrectly() {
        String json = """
                {
                  "question_text": "Calculate: ∑ from n=1 to 5 of n²",
                  "answer_type": "open_ended",
                  "correct_answer": "55"
                }
                """;
        QuestionDto dto = parser.parseQuestionJson(json);
        assertThat(dto.getQuestionText()).contains("∑");
    }

    @Test
    @DisplayName("LLMParseException message includes the missing field name")
    void llmParseException_messageContainsMissingFieldName() {
        String json = """
                {
                  "answer_type": "multiple_choice",
                  "correct_answer": "B"
                }
                """;
        assertThatExceptionOfType(LLMParseException.class)
                .isThrownBy(() -> parser.parseQuestionJson(json))
                .withMessageContaining("question_text");
    }
}
