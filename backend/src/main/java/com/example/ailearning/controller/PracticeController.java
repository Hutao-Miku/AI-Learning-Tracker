package com.example.ailearning.controller;

import com.example.ailearning.common.Result;
import com.example.ailearning.dto.AlgorithmStatement;
import com.example.ailearning.dto.PracticeSet;
import com.example.ailearning.dto.SubmitPracticeRequest;
import com.example.ailearning.dto.SubmitPracticeResponse;
import com.example.ailearning.service.PracticeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    /** 推荐真实真题：八股 MCQ + 算法真题（CF 引用或本地题面），全部在站内作答。 */
    @GetMapping("/recommend")
    public Result<PracticeSet> recommend(@RequestParam(required = false) String knowledgePoint) {
        try {
            return Result.success("ok", practiceService.recommend(knowledgePoint));
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 后端实时抓取 Codeforces 题目完整题面与样例（不可达时回退本地真实题）。 */
    @GetMapping("/algorithm/statement")
    public Result<AlgorithmStatement> statement(@RequestParam int contestId, @RequestParam String index) {
        try {
            return Result.success("ok", practiceService.algorithmStatement(contestId, index));
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 单题作答：八股后端判定；算法记录自报结果 + 代码。 */
    @PostMapping("/submit")
    public Result<SubmitPracticeResponse> submit(@RequestBody SubmitPracticeRequest req) {
        try {
            return Result.success("ok", practiceService.submit(req));
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }
}
