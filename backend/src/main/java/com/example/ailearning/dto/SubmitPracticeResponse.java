package com.example.ailearning.dto;

/**
 * 「去练真题」单题提交响应。
 */
public class SubmitPracticeResponse {

    /** 八股题是否正确 */
    private boolean isCorrect;
    /** 八股题正确答案（提交后回传用于解析） */
    private String correctAnswer;
    /** 八股题解析 */
    private String explanation;
    /** 是否成功记录 */
    private boolean ok;
    private String message;

    public SubmitPracticeResponse() {
    }

    public boolean isCorrect() {
        return isCorrect;
    }

    public void setCorrect(boolean correct) {
        isCorrect = correct;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
