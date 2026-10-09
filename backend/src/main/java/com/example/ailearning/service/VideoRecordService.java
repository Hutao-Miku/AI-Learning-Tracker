package com.example.ailearning.service;

import com.example.ailearning.dto.BatchAddRequest;
import com.example.ailearning.entity.VideoRecord;

import java.util.List;

public interface VideoRecordService {

    List<VideoRecord> listByCourse(Long courseId);

    VideoRecord add(VideoRecord record);

    VideoRecord update(VideoRecord record);

    void delete(Long id);

    /** 批量添加/更新区间内（[startEpisode, endEpisode]）的视频记录，存在则更新、不存在则插入，并触发课程已完成集数重算 */
    List<VideoRecord> batchAdd(BatchAddRequest request);
}
