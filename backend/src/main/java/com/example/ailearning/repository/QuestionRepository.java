package com.example.ailearning.repository;

import com.example.ailearning.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByVideoRecordId(Long videoRecordId);

    /** 企业真题题库去重用：同一知识点 + 题干只保留一条 Question 记录，使错题可稳定回灌 SM-2 */
    Question findByKnowledgePointAndStem(String knowledgePoint, String stem);
}
