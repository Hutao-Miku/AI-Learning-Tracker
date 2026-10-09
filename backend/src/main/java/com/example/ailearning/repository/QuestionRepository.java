package com.example.ailearning.repository;

import com.example.ailearning.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByVideoRecordId(Long videoRecordId);
}
