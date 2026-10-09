package com.example.ailearning.dto;

/**
 * AI 学情分析报告响应。
 * content 为 AI 生成的中文分析文本（支持换行与排版），cached 表示是否命中已生成的缓存。
 */
public class ReportResponse {

    /** 周期 token（如 weekly:2026-10-05），用于去重缓存 */
    private String period;
    /** 周期中文标签（如 本周 / 本月 / 今日 / 今年） */
    private String periodLabel;
    /** AI 生成的学情分析文本 */
    private String content;
    /** 是否直接读取已有缓存（true 表示未重新调用 AI） */
    private boolean cached;
    /** 报告生成时间（ISO，命中缓存时为原生成时间） */
    private String createdTime;

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getPeriodLabel() {
        return periodLabel;
    }

    public void setPeriodLabel(String periodLabel) {
        this.periodLabel = periodLabel;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isCached() {
        return cached;
    }

    public void setCached(boolean cached) {
        this.cached = cached;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }
}
