package com.example.ailearning.dto;

import java.util.List;

/**
 * 学习数据按周期归档的聚合结果。
 * period 取值：daily（按天）/ weekly（按周）/ monthly（按月）/ yearly（按年）。
 * items 按时间先后排序（由后端使用 TreeMap 收集，天然有序）。
 */
public class SummaryResponse {

    /** 周期类型：daily / weekly / monthly / yearly */
    private String period;
    /** 全部周期内的累计答题数 */
    private int totalAnswerCount;
    /** 全部周期内的累计答对题数 */
    private int totalCorrectCount;
    /** 全部周期内的累计学习时长（分钟） */
    private int totalStudyMinutes;
    /** 整体正确率（百分比 0-100） */
    private int overallAccuracy;
    /** 各周期的明细（已按时间升序排列） */
    private List<PeriodStat> items;

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public int getTotalAnswerCount() {
        return totalAnswerCount;
    }

    public void setTotalAnswerCount(int totalAnswerCount) {
        this.totalAnswerCount = totalAnswerCount;
    }

    public int getTotalCorrectCount() {
        return totalCorrectCount;
    }

    public void setTotalCorrectCount(int totalCorrectCount) {
        this.totalCorrectCount = totalCorrectCount;
    }

    public int getTotalStudyMinutes() {
        return totalStudyMinutes;
    }

    public void setTotalStudyMinutes(int totalStudyMinutes) {
        this.totalStudyMinutes = totalStudyMinutes;
    }

    public int getOverallAccuracy() {
        return overallAccuracy;
    }

    public void setOverallAccuracy(int overallAccuracy) {
        this.overallAccuracy = overallAccuracy;
    }

    public List<PeriodStat> getItems() {
        return items;
    }

    public void setItems(List<PeriodStat> items) {
        this.items = items;
    }

    /** 单个周期（一天 / 一周 / 一月 / 一年）的聚合统计 */
    public static class PeriodStat {
        private String key;        // 规范化周期键（如 2026-10-09 / 2026-10 / 2026）
        private String label;      // 展示用标签（如 10-09 / 2026-10 / 2026）
        private String range;      // 区间描述（如周内 "10-06 ~ 10-12"；日/月/年与 label 相同）
        private int answerCount;   // 该周期答题数
        private int correctCount;  // 该周期答对题数
        private int accuracy;      // 该周期正确率（百分比）
        private int studyMinutes;  // 该周期学习时长（分钟）

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getRange() {
            return range;
        }

        public void setRange(String range) {
            this.range = range;
        }

        public int getAnswerCount() {
            return answerCount;
        }

        public void setAnswerCount(int answerCount) {
            this.answerCount = answerCount;
        }

        public int getCorrectCount() {
            return correctCount;
        }

        public void setCorrectCount(int correctCount) {
            this.correctCount = correctCount;
        }

        public int getAccuracy() {
            return accuracy;
        }

        public void setAccuracy(int accuracy) {
            this.accuracy = accuracy;
        }

        public int getStudyMinutes() {
            return studyMinutes;
        }

        public void setStudyMinutes(int studyMinutes) {
            this.studyMinutes = studyMinutes;
        }
    }
}
