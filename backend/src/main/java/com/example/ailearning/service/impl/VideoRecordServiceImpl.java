package com.example.ailearning.service.impl;

import com.example.ailearning.dto.BatchAddRequest;
import com.example.ailearning.entity.Course;
import com.example.ailearning.entity.VideoRecord;
import com.example.ailearning.repository.CourseRepository;
import com.example.ailearning.repository.VideoRecordRepository;
import com.example.ailearning.service.VideoRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VideoRecordServiceImpl implements VideoRecordService {

    private final VideoRecordRepository videoRecordRepository;
    private final CourseRepository courseRepository;

    public VideoRecordServiceImpl(VideoRecordRepository videoRecordRepository,
                                  CourseRepository courseRepository) {
        this.videoRecordRepository = videoRecordRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    public List<VideoRecord> listByCourse(Long courseId) {
        return videoRecordRepository.findByCourseIdOrderByEpisodeNumberAscIdAsc(courseId);
    }

    @Override
    @Transactional
    public VideoRecord add(VideoRecord record) {
        if (record.getCreatedTime() == null) {
            record.setCreatedTime(LocalDateTime.now());
        }
        VideoRecord saved = videoRecordRepository.saveAndFlush(record);
        refreshCourseCompleted(saved.getCourseId());
        return saved;
    }

    @Override
    @Transactional
    public VideoRecord update(VideoRecord record) {
        VideoRecord existing = videoRecordRepository.findById(record.getId())
                .orElseThrow(() -> new IllegalArgumentException("视频记录不存在: id=" + record.getId()));
        Long oldCourseId = existing.getCourseId();

        existing.setCourseId(record.getCourseId());
        existing.setEpisodeNumber(record.getEpisodeNumber());
        existing.setTitle(record.getTitle());
        existing.setDuration(record.getDuration());
        existing.setWatchProgress(record.getWatchProgress());
        existing.setNotes(record.getNotes());

        VideoRecord saved = videoRecordRepository.saveAndFlush(existing);
        if (oldCourseId != null && !oldCourseId.equals(saved.getCourseId())) {
            refreshCourseCompleted(oldCourseId);
        }
        refreshCourseCompleted(saved.getCourseId());
        return saved;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        videoRecordRepository.findById(id).ifPresent(existing -> {
            Long courseId = existing.getCourseId();
            videoRecordRepository.deleteById(id);
            videoRecordRepository.flush();
            refreshCourseCompleted(courseId);
        });
    }

    @Override
    @Transactional
    public List<VideoRecord> batchAdd(BatchAddRequest req) {
        Long courseId = req.getCourseId();
        Integer start = req.getStartEpisode();
        Integer end = req.getEndEpisode();
        if (courseId == null || start == null || end == null) {
            throw new IllegalArgumentException("课程、起始集数、结束集数均不能为空");
        }
        if (start < 1 || end < start) {
            throw new IllegalArgumentException("集数区间不合法：起始集数需 >=1 且 <= 结束集数");
        }
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在: id=" + courseId));

        int duration = req.getDuration() != null ? Math.max(0, req.getDuration()) : 0;
        int progress = clampProgress(req.getWatchProgress());

        // 区间内已有的记录，用于去重（存在则更新，不存在则新建）
        List<VideoRecord> existing = videoRecordRepository
                .findByCourseIdAndEpisodeNumberBetween(courseId, start, end);
        Map<Integer, VideoRecord> byEpisode = existing.stream()
                .collect(Collectors.toMap(VideoRecord::getEpisodeNumber, v -> v, (a, b) -> a));

        List<VideoRecord> toSave = new ArrayList<>();
        for (int ep = start; ep <= end; ep++) {
            VideoRecord rec = byEpisode.get(ep);
            if (rec == null) {
                rec = new VideoRecord();
                rec.setCourseId(courseId);
                rec.setEpisodeNumber(ep);
                rec.setTitle("第" + ep + "集");
                rec.setCreatedTime(LocalDateTime.now());
            }
            rec.setDuration(duration);
            rec.setWatchProgress(progress);
            toSave.add(rec);
        }

        List<VideoRecord> saved = videoRecordRepository.saveAllAndFlush(toSave);
        refreshCourseCompleted(courseId);
        return saved;
    }

    private int clampProgress(Integer progress) {
        if (progress == null) return 0;
        if (progress < 0) return 0;
        if (progress > 100) return 100;
        return progress;
    }

    /**
     * 重新计算课程已完成集数 = 已看完（进度>=100）的不同集数数量，封顶到总集数。
     * 在新增 / 更新 / 删除视频记录后调用，保证 Course.completedEpisodes 与视频进度一致。
     */
    private void refreshCourseCompleted(Long courseId) {
        if (courseId == null) return;
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null) return;
        videoRecordRepository.flush();
        int finished = videoRecordRepository.countFinishedEpisodes(courseId);
        int total = course.getTotalEpisodes() != null ? course.getTotalEpisodes() : 0;
        course.setCompletedEpisodes(Math.min(finished, total));
        courseRepository.saveAndFlush(course);
    }
}
