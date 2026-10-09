package com.example.ailearning.service;

import com.example.ailearning.entity.Question;

import java.util.List;

public interface QuestionService {

    List<Question> generateFromVideo(Long videoRecordId, String text);

    List<Question> listByVideoRecord(Long videoRecordId);

    /** 浏览器插件无感追踪：按 BV 号去重，自动建课程与视频记录并出题 */
    List<Question> generateFromBili(String bvid, String title, String text);

    /** 返回全部题目（按创建时间倒序），供 /quiz 全局作答页使用 */
    List<Question> listAll();
}
