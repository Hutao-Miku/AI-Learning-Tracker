package com.example.ailearning.controller;

import com.example.ailearning.common.Result;
import com.example.ailearning.entity.AnswerRecord;
import com.example.ailearning.repository.AnswerRecordRepository;
import com.example.ailearning.repository.QuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 数据保养与防膨胀：导出 CSV 备份、清理过期原始答题记录。
 */
@RestController
@RequestMapping("/api/data")
public class DataController {

    private final AnswerRecordRepository answerRecordRepository;
    private final QuestionRepository questionRepository;

    @Autowired
    public DataController(AnswerRecordRepository answerRecordRepository,
                          QuestionRepository questionRepository) {
        this.answerRecordRepository = answerRecordRepository;
        this.questionRepository = questionRepository;
    }

    /**
     * 导出答题记录 + 错题为 CSV 文件下载（UTF-8 BOM，Excel 可直接打开）。
     */
    @GetMapping("/export")
    public ResponseEntity<org.springframework.core.io.ByteArrayResource> export() {
        List<AnswerRecord> records = answerRecordRepository.findAll();
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // UTF-8 BOM，避免 Excel 中文乱码
        sb.append("答题时间,题目ID,知识点,题干,你的答案,正确答案,是否答对,耗时(秒)\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (AnswerRecord r : records) {
            String kp = "";
            String stem = "";
            String correct = "";
            try {
                var q = questionRepository.findById(r.getQuestionId()).orElse(null);
                if (q != null) {
                    kp = q.getKnowledgePoint() == null ? "" : q.getKnowledgePoint();
                    stem = q.getStem() == null ? "" : q.getStem();
                    correct = q.getAnswer() == null ? "" : q.getAnswer();
                }
            } catch (Exception ignored) {
                // 题目已被清理时不阻断导出
            }
            String isCorrect = r.getIsCorrect() == null ? "挑战题"
                    : (Boolean.TRUE.equals(r.getIsCorrect()) ? "是" : "否");
            sb.append(csvCell(r.getAnsweredTime() == null ? "" : r.getAnsweredTime().format(fmt))).append(',')
              .append(csvCell(String.valueOf(r.getQuestionId() == null ? "" : r.getQuestionId()))).append(',')
              .append(csvCell(kp)).append(',')
              .append(csvCell(stem)).append(',')
              .append(csvCell(r.getUserAnswer() == null ? "" : r.getUserAnswer())).append(',')
              .append(csvCell(correct)).append(',')
              .append(csvCell(isCorrect)).append(',')
              .append(csvCell(String.valueOf(r.getDurationSeconds() == null ? "" : r.getDurationSeconds())))
              .append('\n');
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        org.springframework.core.io.ByteArrayResource resource =
                new org.springframework.core.io.ByteArrayResource(bytes);
        String filename = "answer_records_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .contentLength(bytes.length)
                .body(resource);
    }

    /**
     * 清理 days 天前的原始答题记录（默认 180）。保留汇总统计由前端/接口另行计算。
     * 返回被删除的记录数。
     */
    @DeleteMapping("/cleanup")
    public Result<Integer> cleanup(@RequestParam(required = false, defaultValue = "180") int days) {
        if (days <= 0) {
            return Result.error(400, "days 必须是正整数");
        }
        try {
            LocalDateTime before = LocalDateTime.now().minusDays(days);
            int deleted = answerRecordRepository.deleteBefore(before);
            return Result.success("已清理 " + days + " 天前的 " + deleted + " 条原始答题记录", deleted);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    private String csvCell(String s) {
        if (s == null) return "";
        // 含逗号/引号/换行时用双引号包裹并转义
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
