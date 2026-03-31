package com.mathbot.api.dto.response;

import lombok.*;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TopicWithProgressDto {
    private UUID id;
    private String name;
    private String description;
    private Short sequenceOrder;
    private String iconKey;
    private String status;
    private String currentLevel;
    private Short masteryCounter;
    private Boolean diagnosticDone;
}
