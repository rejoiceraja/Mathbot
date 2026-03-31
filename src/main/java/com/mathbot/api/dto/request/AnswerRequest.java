package com.mathbot.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AnswerRequest {

    @NotBlank(message = "Student answer is required")
    private String studentAnswer;
}
