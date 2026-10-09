package com.example.ailearning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, Object> hello() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "欢迎使用 AI 学习追踪网站！");
        result.put("status", "ok");
        result.put("service", "ai-learning-backend");
        result.put("timestamp", LocalDateTime.now().toString());
        return result;
    }
}
