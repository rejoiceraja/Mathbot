package com.mathbot.api.repository;

import com.mathbot.api.entity.UserTopicProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserTopicProgressRepository extends JpaRepository<UserTopicProgress, UUID> {

    Optional<UserTopicProgress> findByUserIdAndTopicId(UUID userId, UUID topicId);

    List<UserTopicProgress> findByUserId(UUID userId);

    @Query("SELECT COUNT(p) FROM UserTopicProgress p WHERE p.user.id = :userId AND p.status = 'COMPLETED'")
    long countCompletedByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(p) FROM UserTopicProgress p WHERE p.user.id = :userId AND p.status = 'IN_PROGRESS'")
    long countInProgressByUserId(@Param("userId") UUID userId);
}
