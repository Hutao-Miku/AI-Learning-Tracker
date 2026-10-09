package com.example.ailearning.dto;

/**
 * 浏览器插件上报的 B站视频数据。
 */
public class GenerateFromBiliRequest {

    /** B站视频 BV 号，用于按视频去重 */
    private String bvid;

    /** 视频标题 */
    private String title;

    /** 抓取到的素材：优先 AI 视频总结，退而求其次为简介/弹幕/评论 */
    private String text;

    /** 素材来源标识（AI视频总结 / 视频简介 / 热门弹幕 / 评论），便于排查 */
    private String source;

    public String getBvid() {
        return bvid;
    }

    public void setBvid(String bvid) {
        this.bvid = bvid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
