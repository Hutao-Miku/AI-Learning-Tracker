package com.example.ailearning.dto;

import java.util.List;

/** 整张试卷的批改结果（统一打分 + 错题解析）。 */
public class ExamResult {

    private Long paperId;
    private String period;
    /** 客观题得分（0-100），无客观题时为 -1 */
    private int score;
    private int correctCount;
    private int totalGradable;
    /** 实战挑战题数量（CF 真题 / 题库链接，不计分） */
    private int challengeCount;
    /** 是否为本次新生成的试卷（false 表示读取了已存在的同周期试卷） */
    private boolean cached;
    private List<ExamQuestionResult> items;

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

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public int getTotalGradable() {
        return totalGradable;
    }

    public void setTotalGradable(int totalGradable) {
        this.totalGradable = totalGradable;
    }

    public int getChallengeCount() {
        return challengeCount;
    }

    public void setChallengeCount(int challengeCount) {
        this.challengeCount = challengeCount;
    }

    public boolean isCached() {
        return cached;
    }

    public void setCached(boolean cached) {
        this.cached = cached;
    }

    public List<ExamQuestionResult> getItems() {
        return items;
    }

    public void setItems(List<ExamQuestionResult> items) {
        this.items = items;
    }
}
