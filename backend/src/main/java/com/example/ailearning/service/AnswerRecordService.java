package com.example.ailearning.service;

import com.example.ailearning.entity.AnswerRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AnswerRecordService {

    AnswerRecord submit(Long questionId, String userAnswer, Integer durationSeconds);

    List<AnswerRecord> listByQuestion(Long questionId);

    /** 分页查询全部答题记录（防膨胀：不一次性加载全部历史到内存） */
    Page<AnswerRecord> pageHistory(Pageable pageable);
}
