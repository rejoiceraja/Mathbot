package com.mathbot.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_topic_progress")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserTopicProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "current_level", nullable = false, length = 20)
    @Builder.Default
    private String currentLevel = "EASY";

    @Column(name = "mastery_counter", nullable = false)
    @Builder.Default
    private Short masteryCounter = 0;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "NOT_STARTED";

    @Column(name = "diagnostic_done", nullable = false)
    @Builder.Default
    private Boolean diagnosticDone = false;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
