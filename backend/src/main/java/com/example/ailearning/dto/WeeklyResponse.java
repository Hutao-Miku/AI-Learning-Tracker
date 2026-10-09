package com.example.ailearning.dto;

import java.util.List;

/**
 * 最近 7 天学习节奏响应：按天统计答题数量与学习时长。
 * 固定返回过去 7 天（含今天），没有数据的天 studyMinutes / answerCount 为 0，绝不漏掉某一天。
 */
public class WeeklyResponse {

    private List<DayStat> days;

    public List<DayStat> getDays() {
        return days;
    }

    public void setDays(List<DayStat> days) {
        this.days = days;
    }

    /** 单日统计 */
    public static class DayStat {
        private String date;        // yyyy-MM-dd
        private String weekday;     // 周一..周日
        private int answerCount;    // 当日答题数
        private int studyMinutes;   // 当日学习时长（分钟，由作答耗时累加）

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getWeekday() {
            return weekday;
        }

        public void setWeekday(String weekday) {
            this.weekday = weekday;
        }

        public int getAnswerCount() {
            return answerCount;
        }

        public void setAnswerCount(int answerCount) {
            this.answerCount = answerCount;
        }

        public int getStudyMinutes() {
            return studyMinutes;
        }

        public void setStudyMinutes(int studyMinutes) {
            this.studyMinutes = studyMinutes;
        }
    }
}
