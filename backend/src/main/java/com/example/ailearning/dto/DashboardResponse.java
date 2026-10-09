package com.example.ailearning.dto;

import java.util.List;

/**
 * 每日学习仪表盘响应。
 */
public class DashboardResponse {

    /** 今日学习数据 */
    private TodayStat today;
    /** 全局整体掌握度 0-100 */
    private int overallMastery;
    /** 掌握度最差的前 N 个知识点 */
    private List<WeakPoint> weakPoints;

    public TodayStat getToday() {
        return today;
    }

    public void setToday(TodayStat today) {
        this.today = today;
    }

    public int getOverallMastery() {
        return overallMastery;
    }

    public void setOverallMastery(int overallMastery) {
        this.overallMastery = overallMastery;
    }

    public List<WeakPoint> getWeakPoints() {
        return weakPoints;
    }

    public void setWeakPoints(List<WeakPoint> weakPoints) {
        this.weakPoints = weakPoints;
    }

    /** 今日统计 */
    public static class TodayStat {
        private int answerCount;     // 今日答题数
        private int correctCount;    // 今日答对题数
        private int accuracy;        // 今日正确率（百分比，0-100）
        private int studyMinutes;    // 今日学习时长（分钟，由作答耗时累加）

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

    /** 薄弱知识点 */
    public static class WeakPoint {
        private String knowledgePoint;  // 知识点名称（如 "动态规划"）
        private String tag;             // 分类标签（如 "算法/动态规划"）
        private int mastery;            // 掌握度 0-100
        private String nextReview;      // 下次复习时间（ISO，可空）
        private int answeredCount;      // 该知识点累计作答次数

        public String getKnowledgePoint() {
            return knowledgePoint;
        }

        public void setKnowledgePoint(String knowledgePoint) {
            this.knowledgePoint = knowledgePoint;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public int getMastery() {
            return mastery;
        }

        public void setMastery(int mastery) {
            this.mastery = mastery;
        }

        public String getNextReview() {
            return nextReview;
        }

        public void setNextReview(String nextReview) {
            this.nextReview = nextReview;
        }

        public int getAnsweredCount() {
            return answeredCount;
        }

        public void setAnsweredCount(int answeredCount) {
            this.answeredCount = answeredCount;
        }
    }
}
