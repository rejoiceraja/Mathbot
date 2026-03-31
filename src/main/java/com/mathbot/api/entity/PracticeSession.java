package com.mathbot.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "practice_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PracticeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "is_retake", nullable = false)
    @Builder.Default
    private Boolean isRetake = false;

    @Column(name = "is_diagnostic", nullable = false)
    @Builder.Default
    private Boolean isDiagnostic = false;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "final_level_reached", length = 20)
    private String finalLevelReached;
}
