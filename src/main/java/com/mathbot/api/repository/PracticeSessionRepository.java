package com.mathbot.api.repository;

import com.mathbot.api.entity.PracticeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PracticeSessionRepository extends JpaRepository<PracticeSession, UUID> {

    Optional<PracticeSession> findByIdAndUserId(UUID id, UUID userId);

    List<PracticeSession> findByUserIdOrderByStartedAtDesc(UUID userId);
}
