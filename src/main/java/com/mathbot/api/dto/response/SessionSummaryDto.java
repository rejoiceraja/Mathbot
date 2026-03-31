package com.mathbot.api.dto.response;

import lombok.*;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SessionSummaryDto {
    private UUID sessionId;
    private int totalQuestions;
    private int correctAnswers;
    private double accuracyPercent;
    private long durationSeconds;
    private String finalLevelReached;
    private Boolean topicCompleted;
}
