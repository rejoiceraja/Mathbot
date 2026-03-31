package com.mathbot.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CreateSessionRequest {

    @NotNull(message = "Topic ID is required")
    private UUID topicId;

    private boolean isRetake = false;
}
