package com.example.ailearning.repository;

import com.example.ailearning.entity.PracticeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeRecordRepository extends JpaRepository<PracticeRecord, Long> {
}
