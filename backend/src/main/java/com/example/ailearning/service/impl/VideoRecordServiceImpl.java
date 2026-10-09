package com.example.ailearning.service.impl;

import com.example.ailearning.entity.Course;
import com.example.ailearning.entity.VideoRecord;
import com.example.ailearning.repository.CourseRepository;
import com.example.ailearning.repository.VideoRecordRepository;
import com.example.ailearning.service.VideoRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
