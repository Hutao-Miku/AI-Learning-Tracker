package com.example.ailearning.service;

import com.example.ailearning.entity.Course;

import java.util.List;

public interface CourseService {

    List<Course> list();

    Course getById(Long id);

    Course add(Course course);

    void delete(Long id);
}
