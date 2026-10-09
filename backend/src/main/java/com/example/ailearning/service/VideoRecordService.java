package com.example.ailearning.service;

import com.example.ailearning.entity.VideoRecord;

import java.util.List;

public interface VideoRecordService {

    List<VideoRecord> listByCourse(Long courseId);

    VideoRecord add(VideoRecord record);

    VideoRecord update(VideoRecord record);

    void delete(Long id);
}
