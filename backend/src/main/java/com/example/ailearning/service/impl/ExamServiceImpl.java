package com.example.ailearning.service.impl;

import com.example.ailearning.dto.ExamPaperView;
import com.example.ailearning.dto.ExamQuestion;
import com.example.ailearning.dto.ExamQuestionResult;
import com.example.ailearning.dto.ExamResult;
import com.example.ailearning.dto.SubmitExamItem;
import com.example.ailearning.dto.SubmitExamRequest;
import com.example.ailearning.entity.AnswerRecord;
import com.example.ailearning.entity.ExamAnswer;
import com.example.ailearning.entity.ExamPaper;
import com.example.ailearning.repository.AnswerRecordRepository;
import com.example.ailearning.repository.ExamAnswerRepository;
import com.example.ailearning.repository.ExamPaperRepository;
import com.example.ailearning.service.ExamService;
import com.example.ailearning.service.MasteryService;
import com.example.ailearning.service.QuestionBankService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 定期考核（周测 / 月测）组卷与批改引擎。
 *
 * 设计铁律：题目只能来自「真实来源」，绝不允许 AI 自行编造：
 *  - 算法真题 → Codeforces 正赛（题面由前端经后端实时抓取，在站内展示）；CF 不可达时退化为本地真实经典题。
 *  - 企业八股 → 内置「真实高频面试题库」（公开、权威的面试知识点 MCQ），不调用任何生成模型。
 * 整张试卷在站内铺开作答、统一交卷打分，绝不再出现「给出搜索链接让我自己去刷」的设计。
 */
@Service
public class ExamServiceImpl implements ExamService {

    private final ExamPaperRepository examPaperRepository;
    private final ExamAnswerRepository examAnswerRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final MasteryService masteryService;
    private final QuestionBankService questionBankService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExamServiceImpl(ExamPaperRepository examPaperRepository,
                           ExamAnswerRepository examAnswerRepository,
                           AnswerRecordRepository answerRecordRepository,
                           MasteryService masteryService,
                           QuestionBankService questionBankService) {
        this.examPaperRepository = examPaperRepository;
        this.examAnswerRepository = examAnswerRepository;
        this.answerRecordRepository = answerRecordRepository;
        this.masteryService = masteryService;
        this.questionBankService = questionBankService;
    }

    // ===================== 组卷 =====================

    @Override
    @Transactional
    public ExamPaperView generate(String period) {
        String p = period == null ? "" : period.toLowerCase();
        if (!List.of("weekly", "monthly").contains(p)) {
            throw new RuntimeException("不支持的周期类型：period 必须是 weekly（周测）或 monthly（月测）");
        }
        String token = periodToken(p);
        ExamPaper existing = examPaperRepository.findByPeriod(token);
        if (existing != null) {
            // 命中同周期缓存：直接读取，不重复消耗外部接口额度
            return toStudentView(existing, true);
        }

        // 题目数量：周测 5（3 算法 + 2 八股），月测 10（6 算法 + 4 八股）
        int algoCount = "weekly".equals(p) ? 3 : 6;
        int interviewCount = "weekly".equals(p) ? 2 : 4;

        List<String> weakPoints = getWeakKnowledgePoints();
        List<ExamQuestion> questions = new ArrayList<>();

        int lo = "weekly".equals(p) ? 800 : 1200;
        int hi = "weekly".equals(p) ? 1500 : 1800;

        // 1) 算法真题（Codeforces 真实题，题面站内展示；不可达退化为本地真实经典题，无搜索链接）
        questions.addAll(questionBankService.pickAlgorithm(weakPoints, algoCount, lo, hi));
        // 2) 企业八股（真实高频面试题库 MCQ，保留答案用于判分）
        questions.addAll(questionBankService.pickTheory(weakPoints, interviewCount, false));

        // 顺序打号
        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).setQid(i + 1);
        }

        String paperData;
        try {
            paperData = objectMapper.writeValueAsString(questions);
        } catch (Exception e) {
            throw new RuntimeException("试卷序列化失败：" + e.getMessage());
        }
        ExamPaper saved = examPaperRepository.save(new ExamPaper(token, paperData, LocalDateTime.now()));
        return toStudentView(saved, false);
    }

    private ExamPaperView toStudentView(ExamPaper paper, boolean cached) {
        List<ExamQuestion> questions = parsePaper(paper.getPaperData());
        // 学生视图：抹除答案与解析，防止提前泄露（题面 / 题号 / 链接等用于站内作答的字段保留）
        for (ExamQuestion q : questions) {
            q.setAnswer(null);
            q.setExplanation(null);
        }
        ExamPaperView view = new ExamPaperView();
        view.setPaperId(paper.getId());
        view.setPeriod(paper.getPeriod());
        view.setCreatedTime(paper.getCreatedTime());
        view.setCached(cached);
        view.setQuestions(questions);
        return view;
    }

    private List<ExamQuestion> parsePaper(String paperData) {
        try {
            return objectMapper.readValue(paperData, new TypeReference<List<ExamQuestion>>() {});
        } catch (Exception e) {
            throw new RuntimeException("试卷解析失败：" + e.getMessage());
        }
    }

    /** 周期 token：周一日期（周测）/ 当月首日（月测） */
    private String periodToken(String p) {
        LocalDate today = LocalDate.now();
        if ("weekly".equals(p)) {
            LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
            return "weekly:" + monday.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        LocalDate first = today.withDayOfMonth(1);
        return "monthly:" + first.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    // ===================== 提交批改 =====================

    @Override
    @Transactional
    public ExamResult submit(SubmitExamRequest req) {
        if (req.getPaperId() == null) {
            throw new RuntimeException("缺少 paperId");
        }
        ExamPaper paper = examPaperRepository.findById(req.getPaperId()).orElse(null);
        if (paper == null) {
            throw new RuntimeException("试卷不存在：id=" + req.getPaperId());
        }
        List<ExamQuestion> questions = parsePaper(paper.getPaperData());

        Map<Integer, String> answerMap = new HashMap<>();
        if (req.getAnswers() != null) {
            for (SubmitExamItem it : req.getAnswers()) {
                answerMap.put(it.getQid(), it.getUserAnswer());
            }
        }

        int correctCount = 0;
        int totalGradable = 0;
        int challengeCount = 0;
        List<ExamQuestionResult> items = new ArrayList<>();

        for (ExamQuestion eq : questions) {
            String userAnswer = answerMap.get(eq.getQid());

            ExamQuestionResult r = new ExamQuestionResult();
            r.setQid(eq.getQid());
            r.setKnowledgePoint(eq.getKnowledgePoint());
            r.setStem(eq.getStem());
            r.setSource(eq.getSource());
            r.setType(eq.getType());
            r.setUserAnswer(userAnswer);
            r.setUrl(eq.getUrl());
            r.setContestId(eq.getContestId());
            r.setIndex(eq.getIndex());

            if (eq.isGradable()) {
                totalGradable++;
                boolean correct = userAnswer != null && eq.getAnswer() != null
                        && normalize(userAnswer).equals(normalize(eq.getAnswer()));
                if (correct) correctCount++;
                r.setIsCorrect(correct);
                r.setCorrectAnswer(eq.getAnswer());
                r.setExplanation(eq.getExplanation());

                // 回灌 AnswerRecord → 触发 SM-2 掌握度更新
                AnswerRecord ar = new AnswerRecord();
                ar.setQuestionId(eq.getQuestionId());
                ar.setUserAnswer(userAnswer);
                ar.setIsCorrect(correct);
                ar.setAnsweredTime(LocalDateTime.now());
                answerRecordRepository.save(ar);
            } else {
                challengeCount++;
                r.setIsCorrect(null);
                r.setCorrectAnswer(null);
                r.setExplanation(eq.getExplanation());
            }

            // 无论是否客观题，都保存一份考核作答记录（算法题记录自报 已通过/未通过）
            examAnswerRepository.save(new ExamAnswer(
                    paper.getId(), eq.getQuestionId(), userAnswer, r.getIsCorrect(), LocalDateTime.now()));

            items.add(r);
        }

        ExamResult result = new ExamResult();
        result.setPaperId(paper.getId());
        result.setPeriod(paper.getPeriod());
        result.setCorrectCount(correctCount);
        result.setTotalGradable(totalGradable);
        result.setChallengeCount(challengeCount);
        result.setScore(totalGradable > 0 ? Math.round((float) correctCount / totalGradable * 100) : -1);
        result.setCached(false);
        result.setItems(items);
        return result;
    }

    private String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
    }

    // ===================== 薄弱知识点 =====================

    private List<String> getWeakKnowledgePoints() {
        try {
            var weak = masteryService.getDashboard().getWeakPoints();
            if (weak != null) {
                List<String> ks = weak.stream()
                        .map(w -> w.getKnowledgePoint())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!ks.isEmpty()) return ks;
            }
        } catch (Exception e) {
            // 仪表盘计算异常不影响组卷，走默认知识点
        }
        // 无作答记录时的默认知识点，保证首次使用也能出卷
        return List.of("动态规划", "图论", "贪心", "数论", "字符串", "排序", "二分", "数据结构",
                "TCP三次握手", "HTTP与HTTPS", "进程与线程", "MySQL索引", "JVM垃圾回收");
    }
}
