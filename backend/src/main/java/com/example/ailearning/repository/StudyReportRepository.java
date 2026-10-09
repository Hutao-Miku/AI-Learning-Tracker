package com.example.ailearning.repository;

import com.example.ailearning.entity.StudyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudyReportRepository extends JpaRepository<StudyReport, Long> {

    /** 按周期 token 查询已生成的报告（用于去重命中缓存） */
    Optional<StudyReport> findByPeriod(String period);
}
