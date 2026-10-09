package com.example.ailearning.controller;

import com.example.ailearning.common.Result;
import com.example.ailearning.dto.ExamPaperView;
import com.example.ailearning.dto.ExamResult;
import com.example.ailearning.dto.SubmitExamRequest;
import com.example.ailearning.service.ExamService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exam")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    /**
     * 生成（或读取已存在的同周期）定期考核试卷。
     * period = weekly（周测）/ monthly（月测）。
     */
    @PostMapping("/generate")
    public Result<ExamPaperView> generate(@RequestParam String period) {
        try {
            return Result.success("ok", examService.generate(period));
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 提交批改试卷，返回统一打分与错题解析。 */
    @PostMapping("/submit")
    public Result<ExamResult> submit(@RequestBody SubmitExamRequest req) {
        try {
            return Result.success("ok", examService.submit(req));
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }
}
