package com.example.ailearning.repository;

import com.example.ailearning.entity.VideoRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VideoRecordRepository extends JpaRepository<VideoRecord, Long> {

    List<VideoRecord> findByCourseIdOrderByEpisodeNumberAscIdAsc(Long courseId);

    VideoRecord findByBvid(String bvid);

    /** 该课程下已看完（进度 >= 100）的不同集数数量 */
    @Query("SELECT COUNT(DISTINCT v.episodeNumber) FROM VideoRecord v " +
           "WHERE v.courseId = :courseId AND v.watchProgress >= 100")
    int countFinishedEpisodes(@Param("courseId") Long courseId);
}
