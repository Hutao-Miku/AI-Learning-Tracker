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
import com.example.ailearning.entity.Question;
import com.example.ailearning.repository.AnswerRecordRepository;
import com.example.ailearning.repository.ExamAnswerRepository;
import com.example.ailearning.repository.ExamPaperRepository;
import com.example.ailearning.repository.QuestionRepository;
import com.example.ailearning.service.ExamService;
import com.example.ailearning.service.MasteryService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 定期考核（周测 / 月测）组卷与批改引擎。
 *
 * 设计铁律：题目只能来自「真实来源」，绝不允许 AI 自行编造：
 *  1) 算法真题 → Codeforces 正赛 API 拉取真实题目；若 API 不可达，则退化为「洛谷 / 牛客」等
 *     知名题库的搜索链接（同样不是假题）。
 *  2) 企业八股 → 内置的「真实高频面试题库」（公开、权威的面试知识点 MCQ），不调用任何生成模型。
 * 因此整个组卷过程不依赖智谱等生成式 AI，从根本上杜绝编造假题。
 */
@Service
public class ExamServiceImpl implements ExamService {

    private static final String CF_API = "https://codeforces.com/api/problemset.problems";
    private static final long CF_CACHE_MS = 10 * 60 * 1000; // CF 真题列表缓存 10 分钟

    private final ExamPaperRepository examPaperRepository;
    private final ExamAnswerRepository examAnswerRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final MasteryService masteryService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // CF 真题缓存
    private volatile List<CfProblem> cfCache;
    private volatile long cfCacheTs;

    public ExamServiceImpl(ExamPaperRepository examPaperRepository,
                           ExamAnswerRepository examAnswerRepository,
                           QuestionRepository questionRepository,
                           AnswerRecordRepository answerRecordRepository,
                           MasteryService masteryService,
                           RestTemplate restTemplate) {
        this.examPaperRepository = examPaperRepository;
        this.examAnswerRepository = examAnswerRepository;
        this.questionRepository = questionRepository;
        this.answerRecordRepository = answerRecordRepository;
        this.masteryService = masteryService;
        this.restTemplate = restTemplate;
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

        // 1) 算法真题（Codeforces 真实题目；不可达则退化为题库链接）
        questions.addAll(buildAlgorithmQuestions(weakPoints, algoCount, p));
        // 2) 企业八股（真实高频面试题库 MCQ）
        questions.addAll(buildInterviewQuestions(weakPoints, interviewCount));

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
        // 学生视图：抹除答案与解析，防止提前泄露
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

    // ===================== 算法真题（Codeforces） =====================

    private List<ExamQuestion> buildAlgorithmQuestions(List<String> weakPoints, int count, String period) {
        List<ExamQuestion> result = new ArrayList<>();
        List<CfProblem> all = fetchCfProblems();

        if (all != null && !all.isEmpty()) {
            List<String> cfTags = mapCfTags(weakPoints);
            int lo = "weekly".equals(period) ? 800 : 1200;
            int hi = "weekly".equals(period) ? 1500 : 1800;

            // 第一优先：难度区间 + 薄弱点标签同时命中
            List<CfProblem> tagMatched = new ArrayList<>();
            // 第二优先：仅命中难度区间（保证真实真题，难度贴合）
            List<CfProblem> ratingMatched = new ArrayList<>();
            for (CfProblem prob : all) {
                if (prob.rating == null || prob.rating < lo || prob.rating > hi) continue;
                if (!cfTags.isEmpty() && prob.tags != null && !Collections.disjoint(prob.tags, cfTags)) {
                    tagMatched.add(prob);
                } else {
                    ratingMatched.add(prob);
                }
            }
            // 按 rating 升序，稳定且贴近目标难度
            tagMatched.sort(Comparator.comparingInt(a -> a.rating == null ? 0 : a.rating));
            ratingMatched.sort(Comparator.comparingInt(a -> a.rating == null ? 0 : a.rating));

            // 先取标签命中，不足再用「仅难度」补真实 CF 题
            for (int i = 0; i < tagMatched.size() && result.size() < count; i++) {
                result.add(toCfQuestion(tagMatched.get(i)));
            }
            for (int i = 0; i < ratingMatched.size() && result.size() < count; i++) {
                result.add(toCfQuestion(ratingMatched.get(i)));
            }
        }

        // 不足或 CF 不可达 → 用「洛谷 / 牛客」真实题库搜索链接兜底（绝不编造假题）
        if (result.size() < count) {
            List<String> topics = weakPoints.isEmpty()
                    ? List.of("动态规划", "图论", "贪心", "字符串", "二分", "数据结构")
                    : weakPoints;
            int idx = 0;
            while (result.size() < count) {
                String topic = topics.get(idx % topics.size());
                result.add(toLinkFallback(topic));
                idx++;
            }
        }
        return result;
    }

    private ExamQuestion toCfQuestion(CfProblem p) {
        ExamQuestion q = new ExamQuestion();
        q.setSource("codeforces");
        q.setCategory("algorithm");
        q.setType("challenge");
        q.setGradable(false);
        q.setKnowledgePoint("算法 · Codeforces");
        q.setStem("【Codeforces 真实真题】" + p.name
                + (p.rating != null ? "（难度 rating " + p.rating + "）" : "")
                + "。请前往官方题面阅读并思考解法；本题为实战挑战，不计入客观分，提交后请在链接中对照题解。");
        q.setUrl("https://codeforces.com/problemset/problem/" + p.contestId + "/" + p.index);
        q.setOptions(null);
        q.setAnswer(null);
        q.setExplanation(null);
        q.setQuestionId(null);
        return q;
    }

    private ExamQuestion toLinkFallback(String topic) {
        String enc = URLEncoder.encode(topic, StandardCharsets.UTF_8);
        ExamQuestion q = new ExamQuestion();
        q.setSource("link");
        q.setCategory("algorithm");
        q.setType("challenge");
        q.setGradable(false);
        q.setKnowledgePoint("算法 · " + topic);
        q.setStem("【实战挑战 · 题库链接】Codeforces 暂不可达，请在以下知名题库中查找「" + topic
                + "」相关真题并练习。本题不计入客观分。");
        q.setUrl("https://www.luogu.com.cn/problem/list?keyword=" + enc);
        q.setOptions(null);
        q.setAnswer(null);
        q.setExplanation(null);
        q.setQuestionId(null);
        // 把牛客链接作为补充说明存进 explanation 字段（revealed 阶段展示，不影响计分）
        q.setExplanation("牛客网搜索：" + topic + " → https://www.nowcoder.com/search?query=" + enc + "&type=1");
        return q;
    }

    // ===================== 企业八股（真实面试题库） =====================

    private List<ExamQuestion> buildInterviewQuestions(List<String> weakPoints, int count) {
        List<ExamQuestion> candidates = new ArrayList<>();
        if (!weakPoints.isEmpty()) {
            for (ExamQuestion q : INTERVIEW_BANK) {
                for (String wp : weakPoints) {
                    if (containsKp(q.getKnowledgePoint(), wp) || containsKp(wp, q.getKnowledgePoint())) {
                        candidates.add(q);
                        break;
                    }
                }
            }
        }
        // 去重候选
        candidates = candidates.stream().distinct().collect(Collectors.toList());
        // 若匹配不足，用全库随机补足
        List<ExamQuestion> pool = new ArrayList<>(candidates);
        if (pool.size() < count) {
            List<ExamQuestion> rest = INTERVIEW_BANK.stream()
                    .filter(q -> !pool.contains(q)).collect(Collectors.toList());
            Collections.shuffle(rest);
            pool.addAll(rest);
        }
        Collections.shuffle(pool);
        List<ExamQuestion> chosen = pool.stream().limit(count).collect(Collectors.toList());

        // 持久化为企业真题 Question 记录（去重），使错题可回灌 SM-2
        for (ExamQuestion eq : chosen) {
            Question saved = findOrCreateBankQuestion(eq);
            eq.setQuestionId(saved.getId());
        }
        return chosen;
    }

    private boolean containsKp(String a, String b) {
        if (a == null || b == null) return false;
        return a.toLowerCase().contains(b.toLowerCase());
    }

    private Question findOrCreateBankQuestion(ExamQuestion eq) {
        Question existing = questionRepository.findByKnowledgePointAndStem(eq.getKnowledgePoint(), eq.getStem());
        if (existing != null) return existing;
        Question q = new Question();
        q.setVideoRecordId(-1L); // 哨兵：来源于企业真题库，不属于任何视频
        q.setKnowledgePoint(eq.getKnowledgePoint());
        q.setType("选择题");
        q.setStem(eq.getStem());
        try {
            q.setOptions(objectMapper.writeValueAsString(eq.getOptions()));
        } catch (Exception e) {
            q.setOptions("[]");
        }
        q.setAnswer(eq.getAnswer());
        q.setExplanation(eq.getExplanation());
        q.setCreatedTime(LocalDateTime.now());
        return questionRepository.save(q);
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

            // 无论是否客观题，都保存一份考核作答记录
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

    // ===================== Codeforces =====================

    private List<CfProblem> fetchCfProblems() {
        long now = System.currentTimeMillis();
        if (cfCache != null && now - cfCacheTs < CF_CACHE_MS) return cfCache;
        synchronized (this) {
            if (cfCache != null && now - cfCacheTs < CF_CACHE_MS) return cfCache;
            try {
                String json = restTemplate.getForObject(CF_API, String.class);
                if (json == null) return null;
                CfResponse resp = objectMapper.readValue(json, CfResponse.class);
                if (resp != null && "OK".equals(resp.status) && resp.result != null) {
                    cfCache = resp.result.problems;
                    cfCacheTs = now;
                    return cfCache;
                }
            } catch (Exception e) {
                // 网络不可达 / 限流 → 返回 null，调用方走「题库链接」兜底
                return null;
            }
            return null;
        }
    }

    private List<String> mapCfTags(List<String> knowledgePoints) {
        List<String> tags = new ArrayList<>();
        for (String kp : knowledgePoints) {
            String s = (kp == null ? "" : kp).toLowerCase();
            if (s.contains("动态规划") || s.contains("dp") || s.contains("背包") || s.contains("knapsack")) tags.add("dp");
            if (s.contains("图") || s.contains("最短路") || s.contains("bfs") || s.contains("dfs") || s.contains("tree") || s.contains("graph")) tags.add("graphs");
            if (s.contains("贪心") || s.contains("greedy")) tags.add("greedy");
            if (s.contains("数论") || s.contains("number")) tags.add("number theory");
            if (s.contains("组合") || s.contains("combinator")) tags.add("combinatorics");
            if (s.contains("数学") || s.contains("math")) tags.add("math");
            if (s.contains("字符串") || s.contains("string")) tags.add("strings");
            if (s.contains("排序") || s.contains("sort")) tags.add("sortings");
            if (s.contains("二分") || s.contains("binary")) tags.add("binary search");
            if (s.contains("数据结构") || s.contains("data structure")) tags.add("data structures");
            if (s.contains("几何") || s.contains("geometry")) tags.add("geometry");
            if (s.contains("模拟") || s.contains("implementation") || s.contains("simul")) tags.add("implementation");
            if (s.contains("位运算") || s.contains("bit")) tags.add("bitmasks");
        }
        return tags.stream().distinct().collect(Collectors.toList());
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

    // ===================== CF 响应结构 =====================

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CfResponse {
        public String status;
        public CfResult result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CfResult {
        public List<CfProblem> problems;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CfProblem {
        public int contestId;
        public String index;
        public String name;
        public Integer rating;
        public List<String> tags;
    }

    // ===================== 企业高频面试题库（真实、权威，绝不 AI 编造） =====================

    private static final List<ExamQuestion> INTERVIEW_BANK = buildInterviewBank();

    private static List<ExamQuestion> buildInterviewBank() {
        List<ExamQuestion> bank = new ArrayList<>();
        bank.add(mcq("TCP三次握手",
                "关于 TCP 三次握手，下列说法正确的是？",
                new String[]{"A. 第一次握手客户端发送 SYN 并直接进入 ESTABLISHED",
                        "B. 第二次握手服务端发送 SYN+ACK，并进入 SYN_RCVD",
                        "C. 第三次握手客户端发送 ACK 并进入 TIME_WAIT（建立阶段）",
                        "D. 握手完成后服务端先进入 ESTABLISHED，客户端后进入"},
                "B",
                "三次握手：1)客户端发 SYN→SYN_SENT；2)服务端回 SYN+ACK→SYN_RCVD；3)客户端发 ACK→双方 ESTABLISHED。TIME_WAIT 是连接关闭后的状态，不在建立阶段。"));
        bank.add(mcq("HTTP与HTTPS",
                "关于 HTTP 与 HTTPS，下列说法错误的是？",
                new String[]{"A. HTTPS 在 HTTP 之下增加了 TLS/SSL 加密层",
                        "B. HTTPS 默认端口是 443",
                        "C. HTTP 是明文传输，HTTPS 是加密传输",
                        "D. HTTPS 比 HTTP 更安全，完全无法被中间人攻击"},
                "D",
                "HTTPS 仍可能遭受中间人攻击（如证书被伪造、用户忽略浏览器警告），并非“完全无法”被攻击；其余说法正确。"));
        bank.add(mcq("进程与线程",
                "关于进程与线程，下列说法正确的是？",
                new String[]{"A. 线程是资源分配的基本单位，进程是调度的基本单位",
                        "B. 同一进程内的线程共享地址空间",
                        "C. 进程切换一定比线程切换开销小",
                        "D. 线程之间不存在同步问题"},
                "B",
                "进程是资源分配的基本单位，线程是 CPU 调度的基本单位；同一进程内线程共享内存地址空间，因此需要同步互斥。"));
        bank.add(mcq("MySQL索引",
                "关于 MySQL 索引（以 InnoDB 为例），下列说法正确的是？",
                new String[]{"A. 主键索引的叶子节点存储整行数据（聚簇索引）",
                        "B. 二级索引的叶子节点存储整行数据",
                        "C. 索引一定会使所有查询变快",
                        "D. 联合索引 (a,b,c) 无法利用 a 进行最左前缀查询"},
                "A",
                "InnoDB 主键为聚簇索引，叶子存整行；二级索引叶子存主键值；索引并非对所有查询有效（函数操作/隐式转换会失效）；联合索引遵循最左前缀原则。"));
        bank.add(mcq("MySQL事务",
                "关于数据库事务的 ACID，下列说法错误的是？",
                new String[]{"A. 原子性保证事务内操作要么全做要么全不做",
                        "B. 隔离性通过锁或 MVCC 实现",
                        "C. 持久性指事务提交后对数据的影响是永久的",
                        "D. 一致性是指多个事务并发执行时结果一定与串行执行完全一致（无需任何隔离级别）"},
                "D",
                "一致性是目标，需要原子性、隔离性、持久性共同保障；并发一致的结果需通过隔离级别（如可串行化）来近似保证，并非“无需隔离”自然一致。"));
        bank.add(mcq("JVM垃圾回收",
                "关于 JVM 垃圾回收，下列说法正确的是？",
                new String[]{"A. 引用计数法是 Java 主流 GC 采用的算法",
                        "B. 可达性分析通过 GC Roots 不可达的对象可被回收",
                        "C. Minor GC 主要回收老年代",
                        "D. 垃圾回收时一定会长时间暂停所有用户线程且无法优化"},
                "B",
                "Java 使用可达性分析（从 GC Roots 出发），不可达对象可被回收；引用计数无法解决循环引用，Java 未采用；Minor GC 回收新生代；现代 GC 有并发/增量回收以减少停顿。"));
        bank.add(mcq("Java内存模型",
                "关于 Java 内存模型（JMM），下列说法正确的是？",
                new String[]{"A. volatile 变量保证可见性但不保证原子性",
                        "B. synchronized 不能保证可见性",
                        "C. 每个线程拥有独立的主内存",
                        "D. 原子操作 int++ 在多线程下是线程安全的"},
                "A",
                "volatile 保证可见性与有序性（禁止重排），但不保证复合操作原子性；synchronized 同时保证原子性与可见性；线程有自己的工作内存、主内存共享；i++ 非原子，需同步。"));
        bank.add(mcq("排序算法",
                "关于排序算法，下列说法正确的是？",
                new String[]{"A. 快速排序平均时间复杂度为 O(n^2)",
                        "B. 归并排序是稳定排序且最坏时间复杂度 O(n log n)",
                        "C. 堆排序是不稳定的且空间复杂度 O(n)",
                        "D. 冒泡排序最好情况复杂度仍为 O(n^2)"},
                "B",
                "归并排序稳定，最坏/平均均为 O(n log n)；快排平均 O(n log n)最坏 O(n^2)；堆排序空间 O(1)但不稳定；冒泡最好情况（已序）可优化到 O(n)。"));
        bank.add(mcq("二叉树",
                "关于平衡二叉搜索树（AVL），下列说法正确的是？",
                new String[]{"A. AVL 树任意节点左右子树高度差不超过 1",
                        "B. AVL 树退化为链表时查询仍为 O(1)",
                        "C. AVL 插入不需要旋转",
                        "D. 红黑树比 AVL 更严格平衡"},
                "A",
                "AVL 要求左右子树高度差 ≤1；退化为链表时查询退化为 O(n)；插入/删除需要旋转维持平衡；红黑树平衡要求比 AVL 宽松。"));
        bank.add(mcq("哈希表",
                "关于哈希表，下列说法正确的是？",
                new String[]{"A. 哈希冲突只能通过开放寻址解决",
                        "B. 链地址法（拉链法）将冲突元素放入同一桶的链表",
                        "C. 负载因子越小查询一定越慢",
                        "D. 哈希表不支持 O(1) 平均查找"},
                "B",
                "解决冲突有开放寻址与链地址法等；链地址法用链表/红黑树存同桶元素；负载因子越小空间越大、通常越快；哈希表平均查找 O(1)。"));
        bank.add(mcq("操作系统内存",
                "关于虚拟内存，下列说法正确的是？",
                new String[]{"A. 虚拟内存让每个进程拥有独立的连续地址空间",
                        "B. 虚拟内存完全替代了物理内存",
                        "C. 页面置换只会发生在内存充足时",
                        "D. 虚拟地址直接等于物理地址"},
                "A",
                "虚拟内存为每个进程提供独立、连续的虚拟地址空间，经 MMU 映射到物理页；它不能替代物理内存，页面置换发生在内存不足时；虚拟地址需转换得物理地址。"));
        bank.add(mcq("死锁",
                "产生死锁的四个必要条件不包括？",
                new String[]{"A. 互斥", "B. 占有并等待", "C. 不可抢占", "D. 时间片轮转"},
                "D",
                "死锁四条件：互斥、占有并等待、不可抢占、循环等待；时间片轮转是调度方式，不是死锁条件。"));
        bank.add(mcq("RESTful API",
                "关于 RESTful API 设计，下列说法正确的是？",
                new String[]{"A. GET 请求应当有副作用（修改服务器状态）",
                        "B. 用 HTTP 状态码表达结果，如 200/404/500",
                        "C. 删除资源应使用 POST",
                        "D. URL 中应包含动词表示操作"},
                "B",
                "REST 用 HTTP 方法语义：GET 幂等无副作用，DELETE 删除，POST 新建；URL 用名词资源，状态码表达结果。"));
        bank.add(mcq("缓存",
                "关于缓存穿透、击穿、雪崩，下列说法错误的是？",
                new String[]{"A. 缓存穿透指查询不存在的数据导致每次都打到数据库",
                        "B. 缓存击穿指热点 key 失效瞬间大量请求涌入",
                        "C. 缓存雪崩指大量 key 同时失效",
                        "D. 缓存雪崩与缓存击穿是同一概念"},
                "D",
                "三者不同：穿透=查不存在数据；击穿=单热点 key 失效；雪崩=大量 key 同时失效。可用布隆过滤器、互斥锁、错峰过期等缓解。"));
        bank.add(mcq("数据库范式",
                "关于数据库范式，下列说法正确的是？",
                new String[]{"A. 第一范式要求属性不可再分（原子性）",
                        "B. 第三范式允许存在传递依赖",
                        "C. 范式越高查询一定越快",
                        "D. 反范式化没有任何好处"},
                "A",
                "1NF 要求字段原子不可分；3NF 消除传递依赖；高范式减少冗余但可能增加 join；反范式化可提升读性能，需权衡。"));
        bank.add(mcq("计算机网络-DNS",
                "关于 DNS，下列说法正确的是？",
                new String[]{"A. DNS 用于将域名解析为 IP 地址",
                        "B. DNS 查询只能使用 TCP",
                        "C. 本地 hosts 文件不参与域名解析",
                        "D. DNS 解析结果不会被缓存"},
                "A",
                "DNS 将域名解析为 IP；查询通常使用 UDP（大响应用 TCP）；本地 hosts 优先级最高；解析结果可被各级缓存。"));
        bank.add(mcq("数据库事务隔离",
                "关于事务隔离级别，下列说法正确的是？",
                new String[]{"A. 读未提交（READ UNCOMMITTED）可避免脏读",
                        "B. 可串行化（SERIALIZABLE）能避免脏读、不可重复读和幻读",
                        "C. 读已提交（READ COMMITTED）保证可重复读",
                        "D. 隔离级别越高并发性能一定越好"},
                "B",
                "可串行化提供最高隔离，能避免脏读、不可重复读、幻读；读未提交会出现脏读；读已提交不保证可重复读；隔离级别越高并发性通常越差。"));
        return bank;
    }

    private static ExamQuestion mcq(String kp, String stem, String[] options, String answer, String explanation) {
        ExamQuestion q = new ExamQuestion();
        q.setSource("bank");
        q.setCategory("interview");
        q.setType("choice");
        q.setGradable(true);
        q.setKnowledgePoint(kp);
        q.setStem(stem);
        q.setOptions(Arrays.asList(options));
        q.setAnswer(answer);
        q.setExplanation(explanation);
        q.setUrl(null);
        q.setQuestionId(null);
        return q;
    }
}
