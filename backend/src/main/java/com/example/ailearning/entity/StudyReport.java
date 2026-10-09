package com.example.ailearning.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * AI 学情分析报告存档。
 * period 字段存储「周期 token」（如 weekly:2026-10-05 / monthly:2026-10），
 * 同一周期（同一 token）只生成一次，后续直接读取，避免重复消耗 AI 额度。
 */
@Entity
@Table(name = "study_reports")
public class StudyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period", nullable = false, unique = true)
    private String period;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    public StudyReport() {
    }

    public StudyReport(String period, String content, LocalDateTime createdTime) {
        this.period = period;
        this.content = content;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }
}
