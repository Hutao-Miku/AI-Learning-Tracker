package com.example.ailearning.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 考核答题记录：某次试卷（paperId）中某一题（questionId）的用户作答与批改结果。
 * questionId 对应 ExamPaper.paperData 中的题号 qid；若该题为实战挑战题（无客观答案），
 * questionId 与 isCorrect 均可为空。
 */
@Entity
@Table(name = "exam_answers")
public class ExamAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    /** 试卷内的题号（与 paperData 中的 qid 对应） */
    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "user_answer", columnDefinition = "TEXT")
    private String userAnswer;

    /** 客观题的批改结果；实战挑战题为 null */
    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "answered_time")
    private LocalDateTime answeredTime;

    public ExamAnswer() {
    }

    public ExamAnswer(Long paperId, Long questionId, String userAnswer, Boolean isCorrect, LocalDateTime answeredTime) {
        this.paperId = paperId;
        this.questionId = questionId;
        this.userAnswer = userAnswer;
        this.isCorrect = isCorrect;
        this.answeredTime = answeredTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPaperId() {
        return paperId;
    }

    public void setPaperId(Long paperId) {
        this.paperId = paperId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
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

    public LocalDateTime getAnsweredTime() {
        return answeredTime;
    }

    public void setAnsweredTime(LocalDateTime answeredTime) {
        this.answeredTime = answeredTime;
    }
}
