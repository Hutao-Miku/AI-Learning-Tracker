package com.example.ailearning.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String platform;

    @Column(name = "total_episodes", nullable = false)
    private Integer totalEpisodes;

    @Column(name = "completed_episodes", nullable = false)
    private Integer completedEpisodes;

    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    public Course() {
    }

    public Course(Long userId, String name, String platform,
                  Integer totalEpisodes, Integer completedEpisodes) {
        this.userId = userId;
        this.name = name;
        this.platform = platform;
        this.totalEpisodes = totalEpisodes;
        this.completedEpisodes = completedEpisodes;
        this.createdTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public Integer getTotalEpisodes() {
        return totalEpisodes;
    }

    public void setTotalEpisodes(Integer totalEpisodes) {
        this.totalEpisodes = totalEpisodes;
    }

    public Integer getCompletedEpisodes() {
        return completedEpisodes;
    }

    public void setCompletedEpisodes(Integer completedEpisodes) {
        this.completedEpisodes = completedEpisodes;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }
}
