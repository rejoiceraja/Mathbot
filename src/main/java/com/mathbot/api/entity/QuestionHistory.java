package com.mathbot.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "question_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuestionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private PracticeSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(nullable = false, length = 20)
    private String level;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "question_hash", nullable = false, length = 64)
    private String questionHash;

    @Column(name = "correct_answer", nullable = false, columnDefinition = "TEXT")
    private String correctAnswer;

    @Column(name = "student_answer", columnDefinition = "TEXT")
    private String studentAnswer;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    @Column(name = "is_remedial", nullable = false)
    @Builder.Default
    private Boolean isRemedial = false;

    @Column(name = "llm_source", nullable = false, length = 20)
    @Builder.Default
    private String llmSource = "CLAUDE";

    @Column(name = "asked_at", nullable = false)
    @Builder.Default
    private OffsetDateTime askedAt = OffsetDateTime.now();
}
