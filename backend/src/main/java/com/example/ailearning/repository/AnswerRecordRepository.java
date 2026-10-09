package com.example.ailearning.repository;

import com.example.ailearning.entity.AnswerRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface AnswerRecordRepository extends JpaRepository<AnswerRecord, Long> {

    List<AnswerRecord> findByQuestionIdOrderByAnsweredTimeDesc(Long questionId);

    List<AnswerRecord> findByAnsweredTimeBetween(LocalDateTime start, LocalDateTime end);

    /** 分页历史查询：避免一次性把所有答题记录加载进内存（用于「数据保养 / 历史浏览」） */
    Page<AnswerRecord> findByQuestionId(Long questionId, Pageable pageable);

    Page<AnswerRecord> findAllByOrderByAnsweredTimeDesc(Pageable pageable);

    /** 清理指定时间之前的原始答题记录（保留汇总由前端/其他接口另行统计） */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AnswerRecord a WHERE a.answeredTime < :before")
    int deleteBefore(@Param("before") LocalDateTime before);
}
