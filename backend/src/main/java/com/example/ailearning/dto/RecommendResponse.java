package com.example.ailearning.dto;

import java.util.List;

/**
 * 薄弱知识点 → 真实竞赛真题推荐响应。
 * 绝不自己编题：优先返回 Codeforces 正赛真题，没有匹配则返回知名题库搜索链接兜底。
 */
public class RecommendResponse {

    private String knowledgePoint;
    /** 来源：codeforces（找到真题） / fallback（仅返回题库搜索链接） */
    private String source;
    /** Codeforces 真实真题列表 */
    private List<ProblemItem> problems;
    /** 兜底：洛谷 / 蓝桥杯 / 牛客网 等知名题库的搜索链接 */
    private List<FallbackLink> fallbackLinks;

    public String getKnowledgePoint() {
        return knowledgePoint;
    }

    public void setKnowledgePoint(String knowledgePoint) {
        this.knowledgePoint = knowledgePoint;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<ProblemItem> getProblems() {
        return problems;
    }

    public void setProblems(List<ProblemItem> problems) {
        this.problems = problems;
    }

    public List<FallbackLink> getFallbackLinks() {
        return fallbackLinks;
    }

    public void setFallbackLinks(List<FallbackLink> fallbackLinks) {
        this.fallbackLinks = fallbackLinks;
    }

    /** Codeforces 题目 */
    public static class ProblemItem {
        private String name;
        private Integer rating;     // 难度 rating
        private String url;         // 官方题目链接
        private List<String> tags;  // CF 标签

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getRating() {
            return rating;
        }

        public void setRating(Integer rating) {
            this.rating = rating;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    /** 兜底题库搜索链接 */
    public static class FallbackLink {
        private String name;
        private String url;

        public FallbackLink() {
        }

        public FallbackLink(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
