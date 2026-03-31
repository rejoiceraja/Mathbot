package com.mathbot.api.dto.response;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GradeDto {
    private Short id;
    private String displayName;
    private String description;
    private Integer topicCount;
}
