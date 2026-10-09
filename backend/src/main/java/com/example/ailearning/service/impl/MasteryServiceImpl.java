package com.example.ailearning.service.impl;

import com.example.ailearning.config.ZhipuAiClient;
import com.example.ailearning.dto.DashboardResponse;
import com.example.ailearning.dto.RecommendResponse;
import com.example.ailearning.dto.ReportResponse;
import com.example.ailearning.dto.SummaryResponse;
import com.example.ailearning.dto.WeeklyResponse;
import com.example.ailearning.entity.AnswerRecord;
import com.example.ailearning.entity.Question;
import com.example.ailearning.entity.StudyReport;
import com.example.ailearning.repository.AnswerRecordRepository;
import com.example.ailearning.repository.QuestionRepository;
import com.example.ailearning.repository.StudyReportRepository;
import com.example.ailearning.service.MasteryService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 掌握度引擎：基于 SM-2 间隔重复算法，由 AnswerRecord 回放计算每个知识点的掌握度（EF）、
 * 整体掌握度、薄弱知识点，以及按薄弱知识点推荐真实竞赛真题（Codeforces 正赛 + 知名题库兜底）。
 */
@Service
public class MasteryServiceImpl implements MasteryService {

    private static final String CF_API = "https://codeforces.com/api/problemset.problems";
    private static final long CF_CACHE_MS = 10 * 60 * 1000; // 真题列表缓存 10 分钟

    private final AnswerRecordRepository answerRecordRepository;
    private final QuestionRepository questionRepository;
    private final StudyReportRepository studyReportRepository;
    private final ZhipuAiClient zhipuAiClient;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Codeforces 真题缓存（避免频繁请求触发限流）
    private volatile List<CfProblem> cfCache;
    private volatile long cfCacheTs;

    public MasteryServiceImpl(AnswerRecordRepository answerRecordRepository,
                             QuestionRepository questionRepository,
                             StudyReportRepository studyReportRepository,
                             ZhipuAiClient zhipuAiClient,
                             RestTemplate restTemplate) {
        this.answerRecordRepository = answerRecordRepository;
        this.questionRepository = questionRepository;
        this.studyReportRepository = studyReportRepository;
        this.zhipuAiClient = zhipuAiClient;
        this.restTemplate = restTemplate;
    }

    // ===================== Dashboard =====================

    @Override
    public DashboardResponse getDashboard() {
        DashboardResponse resp = new DashboardResponse();

        // 今日统计
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        List<AnswerRecord> todayAnswers =
                answerRecordRepository.findByAnsweredTimeBetween(startOfToday, now);
        int answerCount = todayAnswers.size();
        int correctCount = 0;
        int studySeconds = 0;
        for (AnswerRecord a : todayAnswers) {
            if (Boolean.TRUE.equals(a.getIsCorrect())) correctCount++;
            if (a.getDurationSeconds() != null) studySeconds += a.getDurationSeconds();
        }
        int accuracy = answerCount > 0 ? Math.round((float) correctCount / answerCount * 100) : 0;
        DashboardResponse.TodayStat today = new DashboardResponse.TodayStat();
        today.setAnswerCount(answerCount);
        today.setCorrectCount(correctCount);
        today.setAccuracy(accuracy);
        today.setStudyMinutes(studySeconds / 60);
        resp.setToday(today);

        // 掌握度（按知识点）
        Map<String, MasteryState> masteryMap = computeMasteryMap();
        if (masteryMap.isEmpty()) {
            resp.setOverallMastery(0);
            resp.setWeakPoints(Collections.emptyList());
            return resp;
        }
        int sum = 0;
        for (MasteryState s : masteryMap.values()) sum += s.mastery;
        resp.setOverallMastery(sum / masteryMap.size());

        // 最弱前 5：掌握度升序，其次作答次数降序
        List<DashboardResponse.WeakPoint> weak = masteryMap.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, MasteryState> e) -> e.getValue().mastery)
                        .thenComparingInt((Map.Entry<String, MasteryState> e) -> -e.getValue().answeredCount))
                .limit(5)
                .map(e -> {
                    DashboardResponse.WeakPoint wp = new DashboardResponse.WeakPoint();
                    wp.setKnowledgePoint(e.getKey());
                    wp.setTag(mapTag(e.getKey()));
                    wp.setMastery(e.getValue().mastery);
                    wp.setAnsweredCount(e.getValue().answeredCount);
                    wp.setNextReview(e.getValue().nextReview);
                    return wp;
                })
                .collect(Collectors.toList());
        resp.setWeakPoints(weak);
        return resp;
    }

    // ===================== Weekly =====================

    private static final String[] WEEKDAY_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    @Override
    public WeeklyResponse getWeekly() {
        WeeklyResponse resp = new WeeklyResponse();
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6); // 过去 7 天（含今天）

        // 预置 7 天，保证即使某天无数据也返回（值为 0）
        Map<LocalDate, WeeklyResponse.DayStat> bucket = new LinkedHashMap<>();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            WeeklyResponse.DayStat s = new WeeklyResponse.DayStat();
            s.setDate(df.format(d));
            s.setWeekday(WEEKDAY_LABELS[d.getDayOfWeek().getValue() - 1]);
            s.setAnswerCount(0);
            s.setStudyMinutes(0);
            bucket.put(d, s);
        }

        // 统计窗口内的作答记录
        List<AnswerRecord> records =
                answerRecordRepository.findByAnsweredTimeBetween(start.atStartOfDay(), LocalDateTime.now());
        for (AnswerRecord a : records) {
            if (a.getAnsweredTime() == null) continue;
            LocalDate d = a.getAnsweredTime().toLocalDate();
            WeeklyResponse.DayStat s = bucket.get(d);
            if (s == null) continue; // 理论上不会越界
            s.setAnswerCount(s.getAnswerCount() + 1);
            if (a.getDurationSeconds() != null) {
                s.setStudyMinutes(s.getStudyMinutes() + a.getDurationSeconds() / 60);
            }
        }

        resp.setDays(new ArrayList<>(bucket.values()));
        return resp;
    }

    // ===================== Summary（周期归档） =====================

    private static final DateTimeFormatter F_DAY = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter F_FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter F_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public SummaryResponse getSummary(String period) {
        SummaryResponse resp = new SummaryResponse();
        resp.setPeriod(period);

        // 取全部作答记录，按周期分组（TreeMap 保证按时间升序）
        List<AnswerRecord> all = answerRecordRepository.findAll();
        Function<AnswerRecord, LocalDate> classifier = periodClassifier(period);

        Map<LocalDate, List<AnswerRecord>> groups = all.stream()
                .filter(a -> a.getAnsweredTime() != null)
                .collect(Collectors.groupingBy(classifier, TreeMap::new, Collectors.toList()));

        int totalAnswer = 0, totalCorrect = 0, totalMinutes = 0;
        List<SummaryResponse.PeriodStat> items = new ArrayList<>();
        for (Map.Entry<LocalDate, List<AnswerRecord>> e : groups.entrySet()) {
            LocalDate key = e.getKey();
            List<AnswerRecord> recs = e.getValue();
            int ans = recs.size();
            int cor = 0;
            int sec = 0;
            for (AnswerRecord a : recs) {
                if (Boolean.TRUE.equals(a.getIsCorrect())) cor++;
                if (a.getDurationSeconds() != null) sec += a.getDurationSeconds();
            }
            int acc = ans > 0 ? Math.round((float) cor / ans * 100) : 0;
            totalAnswer += ans;
            totalCorrect += cor;
            totalMinutes += sec / 60;

            SummaryResponse.PeriodStat stat = new SummaryResponse.PeriodStat();
            stat.setKey(periodKey(period, key));
            stat.setLabel(periodLabel(period, key));
            stat.setRange(periodRange(period, key));
            stat.setAnswerCount(ans);
            stat.setCorrectCount(cor);
            stat.setAccuracy(acc);
            stat.setStudyMinutes(sec / 60);
            items.add(stat);
        }

        resp.setItems(items);
        resp.setTotalAnswerCount(totalAnswer);
        resp.setTotalCorrectCount(totalCorrect);
        resp.setTotalStudyMinutes(totalMinutes);
        resp.setOverallAccuracy(totalAnswer > 0 ? Math.round((float) totalCorrect / totalAnswer * 100) : 0);
        return resp;
    }

    /** 根据周期类型，把作答记录映射到对应的周期首日 LocalDate（用于 groupingBy） */
    private Function<AnswerRecord, LocalDate> periodClassifier(String period) {
        switch (period == null ? "" : period.toLowerCase()) {
            case "weekly":
                return a -> mondayOf(a.getAnsweredTime().toLocalDate());
            case "monthly":
                return a -> YearMonth.from(a.getAnsweredTime().toLocalDate()).atDay(1);
            case "yearly":
                return a -> a.getAnsweredTime().toLocalDate().withDayOfYear(1);
            case "daily":
            default:
                return a -> a.getAnsweredTime().toLocalDate();
        }
    }

    private LocalDate mondayOf(LocalDate d) {
        return d.with(DayOfWeek.MONDAY);
    }

    private String periodKey(String period, LocalDate d) {
        switch (period.toLowerCase()) {
            case "monthly": return d.format(F_MONTH);
            case "yearly": return String.valueOf(d.getYear());
            default: return d.format(F_FULL); // daily / weekly 用首日绝对日期作 key
        }
    }

    private String periodLabel(String period, LocalDate d) {
        switch (period.toLowerCase()) {
            case "monthly": return d.format(F_MONTH);
            case "yearly": return String.valueOf(d.getYear());
            default: return d.format(F_DAY);
        }
    }

    private String periodRange(String period, LocalDate d) {
        switch (period.toLowerCase()) {
            case "weekly": {
                LocalDate end = d.plusDays(6);
                return d.format(F_DAY) + " ~ " + end.format(F_DAY);
            }
            default:
                return periodLabel(period, d);
        }
    }

    // ===================== Report（AI 学情分析） =====================

    @Override
    public ReportResponse generateReport(String period) {
        String p = period == null ? "" : period.toLowerCase();
        if (!List.of("daily", "weekly", "monthly", "yearly").contains(p)) {
            throw new RuntimeException("不支持的周期类型：period 必须是 daily / weekly / monthly / yearly 之一");
        }

        // 1) 计算当前周期窗口 + token（同一周期命中缓存，不重复消耗 AI 额度）
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start;
        String token;
        String label;
        switch (p) {
            case "weekly": {
                LocalDate mon = mondayOf(today);
                start = mon.atStartOfDay();
                token = "weekly:" + mon.format(F_FULL);
                label = "本周";
                break;
            }
            case "monthly": {
                LocalDate first = today.withDayOfMonth(1);
                start = first.atStartOfDay();
                token = "monthly:" + first.format(F_MONTH);
                label = "本月";
                break;
            }
            case "yearly": {
                LocalDate first = today.withDayOfYear(1);
                start = first.atStartOfDay();
                token = "yearly:" + first.getYear();
                label = "今年";
                break;
            }
            case "daily":
            default: {
                start = today.atStartOfDay();
                token = "daily:" + today.format(F_FULL);
                label = "今日";
                break;
            }
        }

        // 2) 命中缓存直接返回
        Optional<StudyReport> cached = studyReportRepository.findByPeriod(token);
        if (cached.isPresent()) {
            StudyReport r = cached.get();
            ReportResponse resp = new ReportResponse();
            resp.setPeriod(token);
            resp.setPeriodLabel(label);
            resp.setContent(r.getContent());
            resp.setCached(true);
            resp.setCreatedTime(r.getCreatedTime() != null
                    ? r.getCreatedTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
            return resp;
        }

        // 3) 统计当前周期数据
        List<AnswerRecord> recs = answerRecordRepository.findByAnsweredTimeBetween(start, now);
        int ans = recs.size();
        int cor = 0;
        int sec = 0;
        for (AnswerRecord a : recs) {
            if (Boolean.TRUE.equals(a.getIsCorrect())) cor++;
            if (a.getDurationSeconds() != null) sec += a.getDurationSeconds();
        }
        int acc = ans > 0 ? Math.round((float) cor / ans * 100) : 0;

        // 整体掌握度 + 最弱 5 个知识点
        Map<String, MasteryState> masteryMap = computeMasteryMap();
        int overall = 0;
        if (!masteryMap.isEmpty()) {
            int sum = 0;
            for (MasteryState s : masteryMap.values()) sum += s.mastery;
            overall = sum / masteryMap.size();
        }
        String weakText = masteryMap.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, MasteryState> e) -> e.getValue().mastery))
                .limit(5)
                .map(e -> "  - " + e.getKey() + "（掌握度 " + e.getValue().mastery + "%）")
                .collect(Collectors.joining("\n"));

        // 4) 组装 Prompt 并调用 AI
        String prompt = "你是一位专业的数据分析师，请根据学生" + label + "的学习数据，分析其强项、弱项，并给出至少3条具体的学习改进建议。\n\n"
                + "【" + label + "学习数据概览】\n"
                + "- 答题总数：" + ans + " 题\n"
                + "- 正确率：" + acc + "%\n"
                + "- 学习总时长：" + (sec / 60) + " 分钟\n"
                + "- 整体掌握度：" + overall + "%\n"
                + (weakText.isEmpty() ? "- 薄弱知识点：暂无（尚未形成足够作答记录）\n"
                                     : "- 当前最薄弱的 5 个知识点：\n" + weakText + "\n")
                + "\n请用清晰的条理（分点、换行）输出分析报告，先总结整体表现，再指出强项与弱项，最后给出可落地的改进建议。";

        String content = zhipuAiClient.chat(prompt);

        // 5) 落库（同一 token 唯一，重复提交由数据库唯一约束兜底）
        StudyReport saved = studyReportRepository.save(new StudyReport(token, content, now));

        ReportResponse resp = new ReportResponse();
        resp.setPeriod(token);
        resp.setPeriodLabel(label);
        resp.setContent(saved.getContent());
        resp.setCached(false);
        resp.setCreatedTime(saved.getCreatedTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return resp;
    }

    // ===================== Recommend =====================

    @Override
    public RecommendResponse recommend(String knowledgePoint) {
        RecommendResponse resp = new RecommendResponse();
        resp.setKnowledgePoint(knowledgePoint);
        resp.setFallbackLinks(buildFallbackLinks(knowledgePoint));

        List<CfProblem> all = fetchCfProblems();
        if (all != null && !all.isEmpty()) {
            List<String> cfTags = mapCfTags(knowledgePoint);
            int mastery = masteryOf(knowledgePoint);
            int target = 800 + (int) Math.round(mastery * 12); // 掌握越低，推荐越简单（用于排序）
            final int LO = 800;   // Codeforces 题目 rating 下限
            final int HI = 2600;  // 上限，避免推荐过高难度

            List<RecommendResponse.ProblemItem> matched = new ArrayList<>();
            for (CfProblem p : all) {
                if (p.rating == null || p.rating < LO || p.rating > HI) continue;
                if (!cfTags.isEmpty() && p.tags != null && !Collections.disjoint(p.tags, cfTags)) {
                    matched.add(toItem(p));
                }
            }
            matched.sort(Comparator.comparingInt(
                    p -> Math.abs((p.getRating() != null ? p.getRating() : target) - target)));

            if (!matched.isEmpty()) {
                resp.setSource("codeforces");
                resp.setProblems(matched.subList(0, Math.min(8, matched.size())));
                return resp;
            }
        }
        // 没匹配到真题（或 CF 不可用）→ 仅返回知名题库搜索链接
        resp.setSource("fallback");
        resp.setProblems(Collections.emptyList());
        return resp;
    }

    // ===================== SM-2 核心 =====================

    /** 回放每个知识点的作答记录，计算 EF / 掌握度 / 下次复习时间。 */
    private Map<String, MasteryState> computeMasteryMap() {
        // 题目 id -> 知识点
        Map<Long, String> qKp = new HashMap<>();
        for (Question q : questionRepository.findAll()) {
            qKp.put(q.getId(), q.getKnowledgePoint());
        }
        // 知识点 -> 作答记录
        Map<String, List<AnswerRecord>> byKp = new LinkedHashMap<>();
        for (AnswerRecord a : answerRecordRepository.findAll()) {
            String kp = qKp.get(a.getQuestionId());
            if (kp == null) continue;
            byKp.computeIfAbsent(kp, k -> new ArrayList<>()).add(a);
        }

        Map<String, MasteryState> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<AnswerRecord>> e : byKp.entrySet()) {
            List<AnswerRecord> list = e.getValue();
            list.sort(Comparator.comparing(a ->
                    a.getAnsweredTime() != null ? a.getAnsweredTime() : LocalDateTime.MIN));

            double ef = 2.5;
            int rep = 0;
            int interval = 0;
            LocalDateTime last = null;
            for (AnswerRecord a : list) {
                int q = Boolean.TRUE.equals(a.getIsCorrect()) ? 5 : 2; // 对=5，错=2（触发重学但不彻底归零）
                if (q >= 3) {
                    if (rep == 0) interval = 1;
                    else if (rep == 1) interval = 6;
                    else interval = (int) Math.round(interval * ef);
                    rep++;
                } else {
                    rep = 0;
                    interval = 1;
                }
                ef = ef + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
                if (ef < 1.3) ef = 1.3;
                if (ef > 3.0) ef = 3.0;
                last = a.getAnsweredTime();
            }
            int mastery = clamp((int) Math.round((ef - 1.3) / (2.5 - 1.3) * 100), 0, 100);
            MasteryState state = new MasteryState();
            state.ef = ef;
            state.mastery = mastery;
            state.answeredCount = list.size();
            state.nextReview = last != null
                    ? last.plusDays(interval).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    : null;
            result.put(e.getKey(), state);
        }
        return result;
    }

    private int masteryOf(String knowledgePoint) {
        MasteryState s = computeMasteryMap().get(knowledgePoint);
        return s == null ? 50 : s.mastery; // 无记录时给中间值，避免推荐过难/过易
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
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
                // 网络不可用 / 限流：返回 null，由调用方走兜底
                return null;
            }
            return null;
        }
    }

    private RecommendResponse.ProblemItem toItem(CfProblem p) {
        RecommendResponse.ProblemItem item = new RecommendResponse.ProblemItem();
        item.setName(p.name);
        item.setRating(p.rating);
        item.setUrl("https://codeforces.com/problemset/problem/" + p.contestId + "/" + p.index);
        item.setTags(p.tags != null ? p.tags : Collections.emptyList());
        return item;
    }

    // ===================== 标签 / 兜底映射 =====================

    private String mapTag(String kp) {
        String s = (kp == null ? "" : kp).toLowerCase();
        if (s.contains("动态规划") || s.contains("dp") || s.contains("背包") || s.contains("knapsack")) return "算法 · 动态规划";
        if (s.contains("图") || s.contains("bfs") || s.contains("dfs") || s.contains("tree") || s.contains("graph")) return "算法 · 图论";
        if (s.contains("数论") || s.contains("number")) return "算法 · 数论";
        if (s.contains("组合") || s.contains("combinator")) return "算法 · 组合数学";
        if (s.contains("贪心") || s.contains("greedy")) return "算法 · 贪心";
        if (s.contains("字符串") || s.contains("string")) return "算法 · 字符串";
        if (s.contains("排序") || s.contains("sort")) return "算法 · 排序";
        if (s.contains("二分") || s.contains("binary")) return "算法 · 二分";
        if (s.contains("数据结构") || s.contains("data structure")) return "算法 · 数据结构";
        if (s.contains("几何") || s.contains("geometry")) return "算法 · 计算几何";
        if (s.contains("网络") || s.contains("tcp") || s.contains("osi") || s.contains("http")) return "计算机基础 · 网络";
        if (s.contains("vue") || s.contains("react") || s.contains("前端") || s.contains("js")) return "前端开发";
        if (s.contains("数据库") || s.contains("sql")) return "数据库";
        return "知识点";
    }

    /** 把知识点映射为 Codeforces 标签（空列表表示 CF 无对应算法分类 → 走兜底）。 */
    private List<String> mapCfTags(String kp) {
        String s = (kp == null ? "" : kp).toLowerCase();
        List<String> tags = new ArrayList<>();
        if (containsAny(s, "动态规划", "dp", "dynamic", "背包", "knapsack")) tags.add("dp");
        if (containsAny(s, "图论", "图", "最短路", "bfs", "dfs", "tree", "graph")) tags.add("graphs");
        if (containsAny(s, "贪心", "greedy")) tags.add("greedy");
        if (containsAny(s, "数论", "number theory", "number")) tags.add("number theory");
        if (containsAny(s, "组合", "combinator")) tags.add("combinatorics");
        if (containsAny(s, "数学", "math")) tags.add("math");
        if (containsAny(s, "字符串", "string")) tags.add("strings");
        if (containsAny(s, "排序", "sort")) tags.add("sortings");
        if (containsAny(s, "二分", "binary")) tags.add("binary search");
        if (containsAny(s, "数据结构", "data structure")) tags.add("data structures");
        if (containsAny(s, "几何", "geometry")) tags.add("geometry");
        if (containsAny(s, "模拟", "implementation", "simul")) tags.add("implementation");
        if (containsAny(s, "位运算", "bit")) tags.add("bitmasks");
        return tags;
    }

    private boolean containsAny(String s, String... keys) {
        for (String k : keys) {
            if (s.contains(k.toLowerCase())) return true;
        }
        return false;
    }

    private List<RecommendResponse.FallbackLink> buildFallbackLinks(String kp) {
        String enc = URLEncoder.encode(kp == null ? "" : kp, StandardCharsets.UTF_8);
        List<RecommendResponse.FallbackLink> links = new ArrayList<>();
        links.add(new RecommendResponse.FallbackLink(
                "洛谷搜索：" + kp, "https://www.luogu.com.cn/problem/list?keyword=" + enc));
        links.add(new RecommendResponse.FallbackLink(
                "蓝桥杯题库：" + kp, "https://www.lanqiao.cn/problems/?keyword=" + enc));
        links.add(new RecommendResponse.FallbackLink(
                "牛客网搜索：" + kp, "https://www.nowcoder.com/search?query=" + enc + "&type=1"));
        return links;
    }

    // ===================== 内部状态 =====================

    private static class MasteryState {
        double ef;
        int mastery;
        int answeredCount;
        String nextReview;
    }

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
}
