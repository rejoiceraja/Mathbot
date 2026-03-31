package com.mathbot.api.repository;

import com.mathbot.api.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TopicRepository extends JpaRepository<Topic, UUID> {

    List<Topic> findByGradeIdOrderBySequenceOrderAsc(Short gradeId);

    long countByGradeId(Short gradeId);
}
