package com.mathbot.api.dto.response;

import lombok.*;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DiagnosticResultDto {
    private UUID topicId;
    private String assignedLevel;
    private int questionsAnswered;
    private int correctAnswers;
}
