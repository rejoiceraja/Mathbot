package com.mathbot.api.repository;

import com.mathbot.api.entity.QuestionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionHistoryRepository extends JpaRepository<QuestionHistory, UUID> {

    @Query("SELECT q.questionText FROM QuestionHistory q WHERE q.user.id = :userId AND q.topic.id = :topicId ORDER BY q.askedAt DESC LIMIT 20")
    List<String> findRecentQuestionStemsByUserAndTopic(@Param("userId") UUID userId,
                                                       @Param("topicId") UUID topicId);

    boolean existsByUserIdAndQuestionHash(UUID userId, String questionHash);

    List<QuestionHistory> findBySessionId(UUID sessionId);

    @Query("SELECT COUNT(q) FROM QuestionHistory q WHERE q.session.id = :sessionId AND q.isCorrect = true")
    long countCorrectBySessionId(@Param("sessionId") UUID sessionId);
}
