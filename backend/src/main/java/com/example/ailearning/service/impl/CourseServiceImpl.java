package com.example.ailearning.service.impl;

import com.example.ailearning.entity.Course;
import com.example.ailearning.repository.CourseRepository;
import com.example.ailearning.service.CourseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;

    public CourseServiceImpl(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public List<Course> list() {
        return courseRepository.findAll();
    }

    @Override
    public Course getById(Long id) {
        return courseRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public Course add(Course course) {
        if (course.getCreatedTime() == null) {
            course.setCreatedTime(LocalDateTime.now());
        }
        return courseRepository.save(course);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        courseRepository.deleteById(id);
    }
}
