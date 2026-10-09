package com.example.ailearning.controller;

import com.example.ailearning.common.Result;
import com.example.ailearning.dto.GenerateFromBiliRequest;
import com.example.ailearning.dto.GenerateRequest;
import com.example.ailearning.entity.Question;
import com.example.ailearning.service.QuestionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final QuestionService questionService;

    public AIController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping("/generate")
    public Result<List<Question>> generate(@RequestBody GenerateRequest req) {
        try {
            List<Question> list = questionService.generateFromVideo(req.getVideoRecordId(), req.getText());
            return Result.success("生成成功", list);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/questions")
    public Result<List<Question>> list(@RequestParam Long videoRecordId) {
        return Result.success(questionService.listByVideoRecord(videoRecordId));
    }

    /**
     * 浏览器插件无感追踪入口：接收 B站视频的 BV 号、标题与抓取到的素材，
     * 由后端调用智谱生成题目入库并返回「出题成功」信号。
     */
    @PostMapping("/generateFromBili")
    public Result<List<Question>> generateFromBili(@RequestBody GenerateFromBiliRequest req) {
        try {
            List<Question> list = questionService.generateFromBili(
                    req.getBvid(), req.getTitle(), req.getText());
            return Result.success("出题成功", list);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 全局作答页 /quiz 拉取全部题目 */
    @GetMapping("/allQuestions")
    public Result<List<Question>> allQuestions() {
        return Result.success(questionService.listAll());
    }
}
