package com.example.ailearning.controller;

import com.example.ailearning.common.Result;
import com.example.ailearning.dto.DashboardResponse;
import com.example.ailearning.dto.RecommendResponse;
import com.example.ailearning.dto.WeeklyResponse;
import com.example.ailearning.service.MasteryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mastery")
public class MasteryController {

    private final MasteryService masteryService;

    public MasteryController(MasteryService masteryService) {
        this.masteryService = masteryService;
    }

    @GetMapping("/dashboard")
    public Result<DashboardResponse> dashboard() {
        try {
            return Result.success("ok", masteryService.getDashboard());
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/weekly")
    public Result<WeeklyResponse> weekly() {
        try {
            return Result.success("ok", masteryService.getWeekly());
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/recommend")
    public Result<RecommendResponse> recommend(@RequestParam String knowledgePoint) {
        try {
            return Result.success("ok", masteryService.recommend(knowledgePoint));
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }
}
