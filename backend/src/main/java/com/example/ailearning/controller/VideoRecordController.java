package com.example.ailearning.controller;

import com.example.ailearning.common.Result;
import com.example.ailearning.dto.BatchAddRequest;
import com.example.ailearning.entity.VideoRecord;
import com.example.ailearning.service.VideoRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/video")
public class VideoRecordController {

    private final VideoRecordService videoRecordService;

    public VideoRecordController(VideoRecordService videoRecordService) {
        this.videoRecordService = videoRecordService;
    }

    @GetMapping("/list/{courseId}")
    public Result<List<VideoRecord>> list(@PathVariable Long courseId) {
        return Result.success(videoRecordService.listByCourse(courseId));
    }

    @PostMapping("/batch")
    public Result<List<VideoRecord>> batch(@RequestBody BatchAddRequest request) {
        try {
            List<VideoRecord> list = videoRecordService.batchAdd(request);
            return Result.success("批量记录成功，共 " + list.size() + " 条", list);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/add")
    public Result<VideoRecord> add(@RequestBody VideoRecord record) {
        return Result.success("添加成功", videoRecordService.add(record));
    }

    @PutMapping("/update")
    public Result<VideoRecord> update(@RequestBody VideoRecord record) {
        return Result.success("更新成功", videoRecordService.update(record));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        videoRecordService.delete(id);
        return Result.success("删除成功", null);
    }
}
