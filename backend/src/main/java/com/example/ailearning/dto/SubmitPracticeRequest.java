package com.example.ailearning.dto;

/**
 * 「去练真题」单题提交请求。
 *  - 八股（kind=theory）：携带 bank 题目 id 与用户选项，由后端判定对错。
 *  - 算法（kind=algorithm）：携带 Codeforces 引用（contestId+index）或本地题引用，记录自报 已通过/未通过 + 代码。
 */
public class SubmitPracticeRequest {

    /** theory / algorithm */
    private String kind;
    /** 八股题对应的企业题库 Question id */
    private Long questionId;
    /** 算法题 Codeforces 比赛 ID */
    private Integer contestId;
    /** 算法题号 */
    private String index;
    /** 八股用户作答（选项字母） */
    private String userAnswer;
    /** 算法自报结果：true=已通过, false=未通过 */
    private Boolean passed;
    /** 算法代码（用户草稿） */
    private String code;
    /** 作答耗时（秒） */
    private Integer durationSeconds;

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Integer getContestId() {
        return contestId;
    }

    public void setContestId(Integer contestId) {
        this.contestId = contestId;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
}
