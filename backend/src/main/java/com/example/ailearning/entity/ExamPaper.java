package com.example.ailearning.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 定期考核试卷（周测 / 月测）。
 * paperData 以 JSON 形式保存该试卷的全部题目（含答案与解析，仅在提交后返回给前端）。
 * period 字段保存周期 token（如 weekly:2026-10-05 / monthly:2026-10），
 * 同一周期只生成一次，后续直接读取，避免重复消耗外部接口额度。
 */
@Entity
@Table(name = "exam_papers")
public class ExamPaper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period", nullable = false, unique = true)
    private String period;

    @Column(name = "paper_data", columnDefinition = "TEXT")
    private String paperData;

    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    public ExamPaper() {
    }

    public ExamPaper(String period, String paperData, LocalDateTime createdTime) {
        this.period = period;
        this.paperData = paperData;
        this.createdTime = createdTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getPaperData() {
        return paperData;
    }

    public void setPaperData(String paperData) {
        this.paperData = paperData;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }
}
