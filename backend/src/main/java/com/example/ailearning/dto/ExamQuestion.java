package com.example.ailearning.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 试卷中的单道题（同时用于「存储（含答案）」与「学生视图（答案置空）」）。
 * 题目来源严格限定为真实数据：
 *  - source=codeforces：Codeforces 正赛真题（实战挑战，客观题不计分，提供官方链接）
 *  - source=link：外部题库（洛谷 / 牛客）搜索链接兜底（CF 不可达时）
 *  - source=bank：企业高频八股真实题库（MCQ，可自动批改）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExamQuestion {

    /** 试卷内题号（从 1 开始，提交时回传用于批改） */
    private int qid;
    /** codeforces / link / bank */
    private String source;
    /** algorithm（算法真题）/ interview（企业八股） */
    private String category;
    /** choice（选择题，可批改）/ challenge（实战挑战，不计分） */
    private String type;
    /** 是否可自动批改（challenge 为 false） */
    private boolean gradable;
    private String knowledgePoint;
    private String stem;
    private List<String> options;
    /** 选择题正确答案（学生视图为 null） */
    private String answer;
    /** 解析（学生视图为 null） */
    private String explanation;
    /** 真题 / 题库链接（challenge 题提供，指向真实题面评测页，非搜索页） */
    private String url;
    /** 对应 questions 表真实记录 id（bank 题用于回灌 SM-2；challenge 为 null） */
    private Long questionId;

    // ===== 算法真题在站内含完整题面所需字段 =====
    /** Codeforces 比赛 ID（source=codeforces 时有效，用于后端实时抓取题面） */
    private Integer contestId;
    /** Codeforces 题号（如 A/B/C） */
    private String index;
    /** 算法真题完整题面（本地兜底题直接内嵌；CF 题由前端按需拉取） */
    private String statement;
    /** 算法真题输入 / 输出样例 */
    private java.util.List<IoSample> ioSamples;

    public ExamQuestion() {
    }

    public int getQid() {
        return qid;
    }

    public void setQid(int qid) {
        this.qid = qid;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isGradable() {
        return gradable;
    }

    public void setGradable(boolean gradable) {
        this.gradable = gradable;
    }

    public String getKnowledgePoint() {
        return knowledgePoint;
    }

    public void setKnowledgePoint(String knowledgePoint) {
        this.knowledgePoint = knowledgePoint;
    }

    public String getStem() {
        return stem;
    }

    public void setStem(String stem) {
        this.stem = stem;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Integer getContestId() {
        return contestId;
    }

    public void setContestId(Integer contestId) {
        this.contestId = contestId;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public java.util.List<IoSample> getIoSamples() {
        return ioSamples;
    }

    public void setIoSamples(java.util.List<IoSample> ioSamples) {
        this.ioSamples = ioSamples;
    }
}
