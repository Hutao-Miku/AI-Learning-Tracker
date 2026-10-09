package com.example.ailearning.dto;

import java.util.List;

/** 批量添加/更新视频记录的请求参数 */
public class BatchAddRequest {

    /** 所属课程 id */
    private Long courseId;
    /** 起始集数（含） */
    private Integer startEpisode;
    /** 结束集数（含） */
    private Integer endEpisode;
    /** 单集时长（秒） */
    private Integer duration;
    /** 观看进度（百分比 0-100） */
    private Integer watchProgress;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Integer getStartEpisode() {
        return startEpisode;
    }

    public void setStartEpisode(Integer startEpisode) {
        this.startEpisode = startEpisode;
    }

    public Integer getEndEpisode() {
        return endEpisode;
    }

    public void setEndEpisode(Integer endEpisode) {
        this.endEpisode = endEpisode;
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
}
