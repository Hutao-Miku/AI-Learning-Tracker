package com.example.ailearning.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 组卷接口的返回（学生视图）：题目不含 answer / explanation，避免提前泄露答案。 */
public class ExamPaperView {

    private Long paperId;
    private String period;
    private LocalDateTime createdTime;
    /** 是否为已存在的同周期试卷（true 表示本次未重新消耗外部接口） */
    private boolean cached;
    private List<ExamQuestion> questions;

    public Long getPaperId() {
        return paperId;
    }

    public void setPaperId(Long paperId) {
        this.paperId = paperId;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public boolean isCached() {
        return cached;
    }

    public void setCached(boolean cached) {
        this.cached = cached;
    }

    public List<ExamQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<ExamQuestion> questions) {
        this.questions = questions;
    }
}
