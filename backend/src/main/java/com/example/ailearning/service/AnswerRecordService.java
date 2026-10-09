package com.example.ailearning.service;

import com.example.ailearning.entity.AnswerRecord;

import java.util.List;

public interface AnswerRecordService {

    AnswerRecord submit(Long questionId, String userAnswer, Integer durationSeconds);

    List<AnswerRecord> listByQuestion(Long questionId);
}
