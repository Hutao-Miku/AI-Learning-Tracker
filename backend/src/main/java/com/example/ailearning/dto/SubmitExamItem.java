package com.example.ailearning.dto;

/** 提交试卷时单题作答：qid 对应试卷题号，userAnswer 为用户作答。 */
public class SubmitExamItem {

    private int qid;
    private String userAnswer;

    public SubmitExamItem() {
    }

    public int getQid() {
        return qid;
    }

    public void setQid(int qid) {
        this.qid = qid;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
    }
}
