package com.example.ailearning.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 「去练真题」作答记录。
 *  - 八股（kind=theory）：记录用户选项与对错，并同步写入 AnswerRecord 以回灌 SM-2；
 *  - 算法（kind=algorithm）：记录用户自报 已通过/未通过 与代码草稿（真实评测在 Codeforces 完成）。
 */
@Entity
@Table(name = "practice_records")
public class PracticeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** theory / algorithm */
    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "knowledge_point")
    private String knowledgePoint;

    /** 八股题对应的企业题库 Question id（算法题可为空） */
    @Column(name = "question_id")
    private Long questionId;

    /** 算法题引用（如 "1234A"）或本地真实题 id */
    @Column(name = "ref")
    private String ref;

    /** 八股用户作答（字母）；算法题存 passed/failed */
    @Column(name = "user_answer", columnDefinition = "TEXT")
    private String userAnswer;

    /** 八股是否正确（算法题为空） */
    @Column(name = "is_correct")
    private Boolean isCorrect;

    /** 算法代码草稿 */
    @Column(name = "code", columnDefinition = "TEXT")
    private String code;

    /** 算法自报结果：已通过/未通过 */
    @Column(name = "passed")
    private Boolean passed;

    @Column(name = "answered_time")
    private LocalDateTime answeredTime;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    public PracticeRecord() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getKnowledgePoint() {
        return knowledgePoint;
    }

    public void setKnowledgePoint(String knowledgePoint) {
        this.knowledgePoint = knowledgePoint;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public LocalDateTime getAnsweredTime() {
        return answeredTime;
    }

    public void setAnsweredTime(LocalDateTime answeredTime) {
        this.answeredTime = answeredTime;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
}
