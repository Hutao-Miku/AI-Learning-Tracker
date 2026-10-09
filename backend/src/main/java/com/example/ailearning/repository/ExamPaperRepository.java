package com.example.ailearning.repository;

import com.example.ailearning.entity.ExamPaper;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamPaperRepository extends JpaRepository<ExamPaper, Long> {

    ExamPaper findByPeriod(String period);
}
