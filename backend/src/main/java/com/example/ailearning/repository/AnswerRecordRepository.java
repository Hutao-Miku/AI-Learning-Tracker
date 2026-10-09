package com.example.ailearning.repository;

import com.example.ailearning.entity.AnswerRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AnswerRecordRepository extends JpaRepository<AnswerRecord, Long> {

    List<AnswerRecord> findByQuestionIdOrderByAnsweredTimeDesc(Long questionId);

    List<AnswerRecord> findByAnsweredTimeBetween(LocalDateTime start, LocalDateTime end);
}
