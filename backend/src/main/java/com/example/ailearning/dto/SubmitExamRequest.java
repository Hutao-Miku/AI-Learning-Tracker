package com.example.ailearning.dto;

import java.util.List;

public class SubmitExamRequest {

    private Long paperId;
    private List<SubmitExamItem> answers;

    public Long getPaperId() {
        return paperId;
    }

    public void setPaperId(Long paperId) {
        this.paperId = paperId;
    }

    public List<SubmitExamItem> getAnswers() {
        return answers;
    }

    public void setAnswers(List<SubmitExamItem> answers) {
        this.answers = answers;
    }
}
