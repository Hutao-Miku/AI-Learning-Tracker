package com.example.ailearning.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "video_records")
public class VideoRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** B站视频 BV 号，用于无感追踪时按视频去重 */
    @Column(length = 32)
    private String bvid;

    @Column(name = "episode_number")
    private Integer episodeNumber;

    @Column(length = 255)
    private String title;

    /** 视频总时长（秒） */
    @Column
    private Integer duration;

    /** 观看进度（百分比 0-100），>=100 视为已看完 */
    @Column(name = "watch_progress")
    private Integer watchProgress;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_time")
    private LocalDateTime createdTime;

    public VideoRecord() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getBvid() {
        return bvid;
    }

    public void setBvid(String bvid) {
        this.bvid = bvid;
    }

    public Integer getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(Integer episodeNumber) {
        this.episodeNumber = episodeNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Integer getWatchProgress() {
        return watchProgress;
    }

    public void setWatchProgress(Integer watchProgress) {
        this.watchProgress = watchProgress;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }
}
