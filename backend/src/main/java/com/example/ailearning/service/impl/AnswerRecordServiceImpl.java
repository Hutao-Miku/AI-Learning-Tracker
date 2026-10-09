package com.example.ailearning.service.impl;

import com.example.ailearning.entity.AnswerRecord;
import com.example.ailearning.entity.Question;
import com.example.ailearning.repository.AnswerRecordRepository;
import com.example.ailearning.repository.QuestionRepository;
import com.example.ailearning.service.AnswerRecordService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnswerRecordServiceImpl implements AnswerRecordService {

    private final QuestionRepository questionRepository;
    private final AnswerRecordRepository answerRecordRepository;

    public AnswerRecordServiceImpl(QuestionRepository questionRepository,
                                   AnswerRecordRepository answerRecordRepository) {
        this.questionRepository = questionRepository;
        this.answerRecordRepository = answerRecordRepository;
    }

    @Override
    @Transactional
    public AnswerRecord submit(Long questionId, String userAnswer, Integer durationSeconds) {
        Question q = questionRepository.findById(questionId).orElse(null);
        if (q == null) {
            throw new RuntimeException("题目不存在: id=" + questionId);
        }
        boolean correct = judge(q, userAnswer);
        AnswerRecord record = new AnswerRecord();
        record.setQuestionId(questionId);
        record.setUserAnswer(userAnswer);
        record.setIsCorrect(correct);
        record.setAnsweredTime(LocalDateTime.now());
        record.setDurationSeconds(durationSeconds);
        return answerRecordRepository.save(record);
    }

    @Override
    public List<AnswerRecord> listByQuestion(Long questionId) {
        return answerRecordRepository.findByQuestionIdOrderByAnsweredTimeDesc(questionId);
    }

    @Override
    public Page<AnswerRecord> pageHistory(Pageable pageable) {
        return answerRecordRepository.findAllByOrderByAnsweredTimeDesc(pageable);
    }

    private boolean judge(Question q, String userAnswer) {
        if (userAnswer == null || q.getAnswer() == null) {
            return false;
        }
        String ua = normalize(userAnswer);
        String ans = normalize(q.getAnswer());
        if ("简答题".equals(q.getType())) {
            // 简答题：双向包含即视为正确（简单启发式，后续可接 LLM 批改）
            return ua.contains(ans) || ans.contains(ua);
        }
        return ua.equals(ans);
    }

    private String normalize(String s) {
        return s.replaceAll("\\s+", "").toLowerCase();
    }
}
