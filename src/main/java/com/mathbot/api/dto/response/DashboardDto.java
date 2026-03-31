package com.mathbot.api.dto.response;

import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DashboardDto {
    private int totalTopics;
    private int completedTopics;
    private int inProgressTopics;
    private double overallProgressPercent;
    private List<TopicWithProgressDto> topics;
}
