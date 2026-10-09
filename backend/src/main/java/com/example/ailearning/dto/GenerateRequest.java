package com.example.ailearning.dto;

public class GenerateRequest {

    private Long videoRecordId;

    // 选填：手动粘贴的视频内容（B站AI总结/字幕/简介）。
    // 若提供则优先用它生成题目，否则回退到该视频记录的 notes。
    private String text;

    public Long getVideoRecordId() {
        return videoRecordId;
    }

    public void setVideoRecordId(Long videoRecordId) {
        this.videoRecordId = videoRecordId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
