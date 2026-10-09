package com.example.ailearning.service;

import com.example.ailearning.dto.ExamPaperView;
import com.example.ailearning.dto.ExamResult;
import com.example.ailearning.dto.SubmitExamRequest;

public interface ExamService {

    /**
     * 生成（或读取已存在的）定期考核试卷。
     * period 仅接受 weekly / monthly；同一周期命中缓存则直接返回，不重复消耗外部接口。
     */
    ExamPaperView generate(String period);

    /** 提交批改试卷：记录对错、把客观题错题回灌 AnswerRecord 触发 SM-2 掌握度更新。 */
    ExamResult submit(SubmitExamRequest req);
}
