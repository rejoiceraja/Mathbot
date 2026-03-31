package com.mathbot.api.dto.response;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuestionDto {
    private UUID questionId;
    private String questionText;
    private String answerType;
    private List<String> options;
    private String hint;
    private String level;
}
