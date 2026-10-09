package com.example.ailearning.controller;

import com.example.ailearning.common.Result;
import com.example.ailearning.entity.Course;
import com.example.ailearning.service.CourseService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/course")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/list")
    public Result<List<Course>> list() {
        return Result.success(courseService.list());
    }

    @GetMapping("/{id}")
    public Result<Course> getById(@PathVariable Long id) {
        return Result.success(courseService.getById(id));
    }

    @PostMapping("/add")
    public Result<Course> add(@RequestBody Course course) {
        return Result.success("新增成功", courseService.add(course));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return Result.success("删除成功", null);
    }
}
