package com.example.ailearning.runner;

import com.example.ailearning.entity.Course;
import com.example.ailearning.repository.CourseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final CourseRepository courseRepository;

    public DataInitializer(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public void run(String... args) {
        if (courseRepository.count() == 0) {
            List<Course> courses = List.of(
                    new Course(1L, "Java基础", "慕课网", 20, 5),
                    new Course(1L, "Vue3入门", "Bilibili", 15, 3),
                    new Course(1L, "计算机网络", "Coursera", 30, 10)
            );
            courseRepository.saveAll(courses);
            log.info("已插入 {} 条课程测试数据: {}", courseRepository.count(),
                    courseRepository.findAll().stream().map(Course::getName).toList());
        } else {
            log.info("课程表已存在数据，跳过初始化。当前共 {} 条。", courseRepository.count());
        }
    }
}
