package com.example.ailearning.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 「去练真题」推荐响应：全部为真实来源题目，绝不含 AI 编造或搜索链接。
 *  - theory：企业八股真实 MCQ（后端判分）
 *  - algorithms：算法真题（Codeforces 真实题，前端按需拉取完整题面；或本地真实题兜底）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PracticeSet {

    private String knowledgePoint;
    /** 八股选择题（答案已抹除，提交后由后端判定） */
    private List<ExamQuestion> theory;
    /** 算法真题（带 contestId/index 或内嵌题面） */
    private List<ExamQuestion> algorithms;

    public PracticeSet() {
    }

    public String getKnowledgePoint() {
        return knowledgePoint;
    }

    public void setKnowledgePoint(String knowledgePoint) {
        this.knowledgePoint = knowledgePoint;
    }

    public List<ExamQuestion> getTheory() {
        return theory;
    }

    public void setTheory(List<ExamQuestion> theory) {
        this.theory = theory;
    }

    public List<ExamQuestion> getAlgorithms() {
        return algorithms;
    }

    public void setAlgorithms(List<ExamQuestion> algorithms) {
        this.algorithms = algorithms;
    }
}
