package com.example.ailearning.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 算法真题完整题面（后端实时从 Codeforces 抓取并解析；CF 不可达时回退到本地真实题库）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlgorithmStatement {

    private Integer contestId;
    private String index;
    private String name;
    /** 题面正文（纯文本，已去除 HTML 标签） */
    private String statement;
    /** 输入 / 输出样例 */
    private List<IoSample> ioSamples;
    /** 真实题面 / 评测页地址（供「去评测」跳转，非搜索页） */
    private String url;
    /** codeforces（实时抓取）/ local-algo（本地真实题兜底） */
    private String source;

    public AlgorithmStatement() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public List<IoSample> getIoSamples() {
        return ioSamples;
    }

    public void setIoSamples(List<IoSample> ioSamples) {
        this.ioSamples = ioSamples;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
