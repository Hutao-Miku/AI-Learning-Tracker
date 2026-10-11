package com.example.ailearning.service;

import com.example.ailearning.dto.AlgorithmStatement;
import com.example.ailearning.dto.ExamQuestion;
import com.example.ailearning.dto.PracticeSet;
import com.example.ailearning.dto.SubmitPracticeRequest;
import com.example.ailearning.dto.SubmitPracticeResponse;
import com.example.ailearning.entity.AnswerRecord;
import com.example.ailearning.entity.PracticeRecord;
import com.example.ailearning.entity.Question;
import com.example.ailearning.repository.PracticeRecordRepository;
import com.example.ailearning.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 「去练真题」业务逻辑：全部题目来自真实来源（企业八股题库 / Codeforces / 本地经典题），
 * 作答完全在站内完成：八股由后端判定对错，算法记录自报结果与代码。
 */
@Service
public class PracticeService {

    private static final List<String> DEFAULT_WEAK = Arrays.asList(
            "MySQL索引", "TCP三次握手", "JVM垃圾回收", "Redis数据类型",
            "Spring-AOP", "HashMap", "动态规划", "图论", "字符串", "排序算法");

    private final QuestionBankService questionBankService;
    private final AnswerRecordService answerRecordService;
    private final QuestionRepository questionRepository;
    private final PracticeRecordRepository practiceRecordRepository;

    public PracticeService(QuestionBankService questionBankService,
                           AnswerRecordService answerRecordService,
                           QuestionRepository questionRepository,
                           PracticeRecordRepository practiceRecordRepository) {
        this.questionBankService = questionBankService;
        this.answerRecordService = answerRecordService;
        this.questionRepository = questionRepository;
        this.practiceRecordRepository = practiceRecordRepository;
    }

    /** 推荐真实真题集合：八股 MCQ + 算法真题（CF 引用或本地题面）。 */
    public PracticeSet recommend(String knowledgePoint) {
        List<String> weak = new ArrayList<>();
        if (knowledgePoint != null && !knowledgePoint.isBlank()) {
            weak.add(knowledgePoint.trim());
        } else {
            weak.addAll(DEFAULT_WEAK);
        }
        PracticeSet set = new PracticeSet();
        set.setKnowledgePoint(knowledgePoint);
        set.setTheory(questionBankService.pickTheory(weak, 5, true));
        set.setAlgorithms(questionBankService.pickAlgorithm(weak, 3, 800, 1800));
        return set;
    }

    /** 后端实时抓取单个算法真题完整题面（CF 不可达时回退本地真实题）。 */
    public AlgorithmStatement algorithmStatement(int contestId, String index) {
        return questionBankService.fetchStatement(contestId, index);
    }

    @Transactional
    public SubmitPracticeResponse submit(SubmitPracticeRequest req) {
        SubmitPracticeResponse resp = new SubmitPracticeResponse();
        String kind = req.getKind() == null ? "" : req.getKind();
        if ("algorithm".equals(kind)) {
            PracticeRecord rec = new PracticeRecord();
            rec.setKind("algorithm");
            rec.setKnowledgePoint(buildAlgoRef(req));
            rec.setRef(buildAlgoRef(req));
            rec.setPassed(req.getPassed());
            rec.setCode(req.getCode());
            rec.setUserAnswer(req.getPassed() != null ? (req.getPassed() ? "passed" : "failed") : "");
            rec.setAnsweredTime(LocalDateTime.now());
            rec.setDurationSeconds(req.getDurationSeconds());
            practiceRecordRepository.save(rec);
            resp.setOk(true);
            resp.setMessage("已记录：算法题" + (req.getPassed() != null && req.getPassed() ? "已通过" : "未通过"));
            return resp;
        }
        // 八股：后端判定对错
        if (req.getQuestionId() == null) {
            resp.setOk(false);
            resp.setMessage("缺少题目 id");
            return resp;
        }
        Question q = questionRepository.findById(req.getQuestionId()).orElse(null);
        if (q == null) {
            resp.setOk(false);
            resp.setMessage("题目不存在");
            return resp;
        }
        AnswerRecord ar = answerRecordService.submit(req.getQuestionId(), req.getUserAnswer(), req.getDurationSeconds());
        boolean correct = Boolean.TRUE.equals(ar.getIsCorrect());
        resp.setCorrect(correct);
        resp.setCorrectAnswer(q.getAnswer());
        resp.setExplanation(q.getExplanation());
        resp.setOk(true);

        PracticeRecord rec = new PracticeRecord();
        rec.setKind("theory");
        rec.setKnowledgePoint(q.getKnowledgePoint());
        rec.setQuestionId(q.getId());
        rec.setUserAnswer(req.getUserAnswer());
        rec.setIsCorrect(correct);
        rec.setAnsweredTime(LocalDateTime.now());
        rec.setDurationSeconds(req.getDurationSeconds());
        practiceRecordRepository.save(rec);
        return resp;
    }

    private String buildAlgoRef(SubmitPracticeRequest req) {
        if (req.getContestId() != null && req.getIndex() != null) {
            return req.getContestId() + "" + req.getIndex();
        }
        return req.getIndex() != null ? req.getIndex() : "local";
    }
}
