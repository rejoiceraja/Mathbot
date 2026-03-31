package com.mathbot.api.repository;

import com.mathbot.api.entity.StaticQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StaticQuestionRepository extends JpaRepository<StaticQuestion, UUID> {

    @Query("SELECT q FROM StaticQuestion q WHERE q.topic.id = :topicId AND q.level = :level AND q.id NOT IN :excludeIds ORDER BY FUNCTION('RANDOM')")
    List<StaticQuestion> findByTopicAndLevelExcluding(
            @Param("topicId") UUID topicId,
            @Param("level") String level,
            @Param("excludeIds") List<UUID> excludeIds);

    List<StaticQuestion> findByTopicIdAndLevel(UUID topicId, String level);
}
