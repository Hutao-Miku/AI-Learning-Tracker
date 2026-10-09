package com.example.ailearning.controller;

import com.example.ailearning.common.Result;
import com.example.ailearning.dto.SubmitAnswerRequest;
import com.example.ailearning.entity.AnswerRecord;
import com.example.ailearning.service.AnswerRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/answer")
public class AnswerController {

    private final AnswerRecordService answerRecordService;

    public AnswerController(AnswerRecordService answerRecordService) {
        this.answerRecordService = answerRecordService;
    }

    @PostMapping("/submit")
    public Result<AnswerRecord> submit(@RequestBody SubmitAnswerRequest req) {
        try {
            AnswerRecord record = answerRecordService.submit(req.getQuestionId(), req.getUserAnswer(), req.getDurationSeconds());
            return Result.success("提交成功", record);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<List<AnswerRecord>> list(@RequestParam Long questionId) {
        return Result.success(answerRecordService.listByQuestion(questionId));
    }
}
