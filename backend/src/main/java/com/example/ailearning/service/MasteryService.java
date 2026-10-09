package com.example.ailearning.service;

import com.example.ailearning.dto.DashboardResponse;
import com.example.ailearning.dto.RecommendResponse;
import com.example.ailearning.dto.WeeklyResponse;

public interface MasteryService {

    /** 每日学习仪表盘：今日数据 + 整体掌握度 + 最弱知识点 */
    DashboardResponse getDashboard();

    /** 根据薄弱知识点推荐真实竞赛真题（Codeforces 正赛 + 知名题库兜底链接） */
    RecommendResponse recommend(String knowledgePoint);

    /** 最近 7 天学习节奏：按天统计答题数量与学习时长 */
    WeeklyResponse getWeekly();
}
