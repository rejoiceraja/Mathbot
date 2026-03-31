package com.mathbot.api.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PracticeSessionDto {
    private UUID id;
    private UUID topicId;
    private String topicName;
    private String currentLevel;
    private Short masteryCounter;
    private Boolean isDiagnostic;
    private Boolean isRetake;
    private OffsetDateTime startedAt;
}
