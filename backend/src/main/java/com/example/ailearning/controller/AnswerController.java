package com.example.ailearning.controller;

import com.example.ailearning.common.Result;
import com.example.ailearning.dto.SubmitAnswerRequest;
import com.example.ailearning.entity.AnswerRecord;
import com.example.ailearning.service.AnswerRecordService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 分页历史查询：避免一次性把所有答题记录加载进内存（用于历史浏览 / 数据保养）。
     * page 从 0 开始，size 默认 20。
     */
    @GetMapping("/history")
    public Result<Map<String, Object>> history(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "answeredTime"));
        Page<AnswerRecord> p = answerRecordService.pageHistory(pageable);
        Map<String, Object> body = new HashMap<>();
        body.put("content", p.getContent());
        body.put("page", p.getNumber());
        body.put("size", p.getSize());
        body.put("totalElements", p.getTotalElements());
        body.put("totalPages", p.getTotalPages());
        return Result.success(body);
    }
}
