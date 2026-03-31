package com.mathbot.api.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathbot.api.entity.Grade;
import com.mathbot.api.entity.Topic;
import com.mathbot.api.repository.QuestionHistoryRepository;
import com.mathbot.api.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link PromptBuilder}.
 *
 * Verifies that the LLM request payload is constructed correctly
 * as specified in TDD §4.2 Claude API Request Construction.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PromptBuilder – LLM prompt construction")
class PromptBuilderTest {

    @Mock private QuestionHistoryRepository questionHistoryRepository;
    @Mock private TopicRepository topicRepository;

    private PromptBuilder promptBuilder;
    private ObjectMapper objectMapper;
    private UUID userId;
    private Topic multiplicationTopic;
    private Grade grade4;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        promptBuilder = new PromptBuilder(questionHistoryRepository, topicRepository, objectMapper);

        userId = UUID.randomUUID();
        grade4 = Grade.builder().id((short) 4).displayName("Grade 4").build();
        multiplicationTopic = Topic.builder()
                .id(UUID.randomUUID())
                .grade(grade4)
                .name("Multiplication")
                .description("Times tables")
                .sequenceOrder((short) 3)
                .build();
    }

    @Test
    @DisplayName("buildUserMessage() returns valid JSON string")
    void buildUserMessage_returnsValidJson() throws Exception {
        given(questionHistoryRepository.findRecentQuestionStemsByUserAndTopic(
                userId, multiplicationTopic.getId())).willReturn(List.of());
        given(topicRepository.findByGradeIdOrderBySequenceOrderAsc((short) 4))
                .willReturn(List.of(multiplicationTopic));

        String json = promptBuilder.buildUserMessage(userId, multiplicationTopic, "HARD");

        assertThatCode(() -> objectMapper.readTree(json)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Payload contains grade id matching topic's grade")
    void buildUserMessage_containsCorrectGradeId() throws Exception {
        given(questionHistoryRepository.findRecentQuestionStemsByUserAndTopic(any(), any()))
                .willReturn(List.of());
        given(topicRepository.findByGradeIdOrderBySequenceOrderAsc(any()))
                .willReturn(List.of(multiplicationTopic));

        String json = promptBuilder.buildUserMessage(userId, multiplicationTopic, "EASY");
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("grade").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("Payload contains topic name")
    void buildUserMessage_containsTopicName() throws Exception {
        given(questionHistoryRepository.findRecentQuestionStemsByUserAndTopic(any(), any()))
                .willReturn(List.of());
        given(topicRepository.findByGradeIdOrderBySequenceOrderAsc(any()))
                .willReturn(List.of(multiplicationTopic));

        String json = promptBuilder.buildUserMessage(userId, multiplicationTopic, "MEDIUM");
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("topic").asText()).isEqualTo("Multiplication");
    }

    @Test
    @DisplayName("Payload contains difficulty_level")
    void buildUserMessage_containsDifficultyLevel() throws Exception {
        given(questionHistoryRepository.findRecentQuestionStemsByUserAndTopic(any(), any()))
                .willReturn(List.of());
        given(topicRepository.findByGradeIdOrderBySequenceOrderAsc(any()))
                .willReturn(List.of(multiplicationTopic));

        String json = promptBuilder.buildUserMessage(userId, multiplicationTopic, "CHALLENGING");
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("difficulty_level").asText()).isEqualTo("CHALLENGING");
    }

    @Test
    @DisplayName("Payload includes previously_asked_question_stems from repository")
    void buildUserMessage_includesRecentQuestionStems() throws Exception {
        List<String> recentStems = List.of(
                "A farmer has 15 rows...",
                "If a school orders 36 boxes..."
        );
        given(questionHistoryRepository.findRecentQuestionStemsByUserAndTopic(
                userId, multiplicationTopic.getId())).willReturn(recentStems);
        given(topicRepository.findByGradeIdOrderBySequenceOrderAsc(any()))
                .willReturn(List.of(multiplicationTopic));

        String json = promptBuilder.buildUserMessage(userId, multiplicationTopic, "EASY");
        JsonNode node = objectMapper.readTree(json);

        JsonNode stems = node.get("previously_asked_question_stems");
        assertThat(stems.isArray()).isTrue();
        assertThat(stems.size()).isEqualTo(2);
        assertThat(stems.get(0).asText()).isEqualTo("A farmer has 15 rows...");
    }

    @Test
    @DisplayName("Payload includes response_format schema with all required keys")
    void buildUserMessage_includesResponseFormatSchema() throws Exception {
        given(questionHistoryRepository.findRecentQuestionStemsByUserAndTopic(any(), any()))
                .willReturn(List.of());
        given(topicRepository.findByGradeIdOrderBySequenceOrderAsc(any()))
                .willReturn(List.of(multiplicationTopic));

        String json = promptBuilder.buildUserMessage(userId, multiplicationTopic, "EASY");
        JsonNode node = objectMapper.readTree(json);
        JsonNode format = node.get("response_format");

        assertThat(format).isNotNull();
        assertThat(format.has("question_text")).isTrue();
        assertThat(format.has("answer_type")).isTrue();
        assertThat(format.has("correct_answer")).isTrue();
        assertThat(format.has("hint")).isTrue();
    }

    @Test
    @DisplayName("Payload includes all grade curriculum topics")
    void buildUserMessage_includesGradeCurriculumTopics() throws Exception {
        Topic additionTopic = Topic.builder()
                .id(UUID.randomUUID()).grade(grade4)
                .name("Addition").sequenceOrder((short) 1).build();

        given(questionHistoryRepository.findRecentQuestionStemsByUserAndTopic(any(), any()))
                .willReturn(List.of());
        given(topicRepository.findByGradeIdOrderBySequenceOrderAsc((short) 4))
                .willReturn(List.of(additionTopic, multiplicationTopic));

        String json = promptBuilder.buildUserMessage(userId, multiplicationTopic, "EASY");
        JsonNode node = objectMapper.readTree(json);
        JsonNode topics = node.get("grade_curriculum_topics");

        assertThat(topics.isArray()).isTrue();
        List<String> topicNames = List.of(
                topics.get(0).asText(), topics.get(1).asText());
        assertThat(topicNames).contains("Addition", "Multiplication");
    }

    @Test
    @DisplayName("SYSTEM_PROMPT constant is non-blank and mentions JSON")
    void systemPrompt_isNonBlankAndMentionsJson() {
        assertThat(PromptBuilder.SYSTEM_PROMPT)
                .isNotBlank()
                .containsIgnoringCase("JSON");
    }
}
