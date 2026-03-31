package com.mathbot.api.dto.response;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnswerResultDto {
    private Boolean isCorrect;
    private String explanation;
    private QuestionDto followUpQuestion;
    private Short masteryCounter;
    private String newLevel;
    private Boolean topicCompleted;
}
