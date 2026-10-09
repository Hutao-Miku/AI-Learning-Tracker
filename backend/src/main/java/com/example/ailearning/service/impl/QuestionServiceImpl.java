package com.example.ailearning.service.impl;

import com.example.ailearning.config.ZhipuProperties;
import com.example.ailearning.entity.Course;
import com.example.ailearning.entity.Question;
import com.example.ailearning.entity.VideoRecord;
import com.example.ailearning.repository.CourseRepository;
import com.example.ailearning.repository.QuestionRepository;
import com.example.ailearning.repository.VideoRecordRepository;
import com.example.ailearning.service.QuestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class QuestionServiceImpl implements QuestionService {

    private static final String BILI_COURSE_NAME = "B站自动追踪";

    /** 429 限流 / 服务端临时故障时的重试次数（首次请求之外最多再试 3 次） */
    private static final int MAX_RETRIES = 3;
    /** 指数退避间隔：第 1/2/3 次重试前分别等待 2s、4s、8s */
    private static final long[] RETRY_DELAYS_MS = {2000, 4000, 8000};

    private final ZhipuProperties zhipuProperties;
    private final RestTemplate restTemplate;
    private final VideoRecordRepository videoRecordRepository;
    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QuestionServiceImpl(ZhipuProperties zhipuProperties,
                               RestTemplate restTemplate,
                               VideoRecordRepository videoRecordRepository,
                               QuestionRepository questionRepository,
                               CourseRepository courseRepository) {
        this.zhipuProperties = zhipuProperties;
        this.restTemplate = restTemplate;
        this.videoRecordRepository = videoRecordRepository;
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional
    public List<Question> generateFromVideo(Long videoRecordId, String text) {
        VideoRecord vr = videoRecordRepository.findById(videoRecordId).orElse(null);
        if (vr == null) {
            throw new RuntimeException("视频记录不存在: id=" + videoRecordId);
        }
        // 优先使用手动粘贴的内容；为空时回退到该视频记录的 notes
        String content;
        if (text != null && !text.trim().isEmpty()) {
            content = text.trim();
        } else {
            content = vr.getNotes();
        }
        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("缺少题目生成素材：请在页面粘贴B站AI总结/字幕/简介，或确保该视频记录含有笔记");
        }

        String prompt = buildPrompt(content);
        String aiContent = callZhipu(prompt);
        List<Map<String, Object>> items = parseQuestions(aiContent);

        List<Question> questions = new ArrayList<>();
        for (Map<String, Object> m : items) {
            questions.add(toQuestion(m, videoRecordId));
        }
        if (questions.isEmpty()) {
            throw new RuntimeException("大模型未返回任何题目，请重试");
        }
        return questionRepository.saveAll(questions);
    }

    @Override
    public List<Question> listByVideoRecord(Long videoRecordId) {
        return questionRepository.findByVideoRecordId(videoRecordId);
    }

    @Override
    @Transactional
    public List<Question> generateFromBili(String bvid, String title, String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new RuntimeException("抓取到的视频内容为空，无法出题（B站页面可能尚未加载出简介/总结）");
        }
        text = text.trim();

        // 1) 按 BV 号去重：已存在视频记录则复用，避免重复生成
        VideoRecord vr = videoRecordRepository.findByBvid(bvid);
        if (vr == null) {
            Course course = findOrCreateBiliCourse();
            vr = new VideoRecord();
            vr.setCourseId(course.getId());
            vr.setBvid(bvid);
            vr.setEpisodeNumber(1);
            vr.setWatchProgress(100);
            vr.setCreatedTime(LocalDateTime.now());
        }
        vr.setTitle(title);
        vr.setNotes(text);
        vr = videoRecordRepository.save(vr);

        // 2) 幂等：该视频已有题目则直接返回，不再调用智谱，避免重复消耗额度
        List<Question> existing = questionRepository.findByVideoRecordId(vr.getId());
        if (!existing.isEmpty()) {
            return existing;
        }

        // 3) 调用智谱生成题目并入库
        String prompt = buildPrompt(text);
        String aiContent = callZhipu(prompt);
        List<Map<String, Object>> items = parseQuestions(aiContent);

        List<Question> questions = new ArrayList<>();
        for (Map<String, Object> m : items) {
            questions.add(toQuestion(m, vr.getId()));
        }
        if (questions.isEmpty()) {
            throw new RuntimeException("大模型未返回任何题目，请重试");
        }
        return questionRepository.saveAll(questions);
    }

    @Override
    public List<Question> listAll() {
        return questionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdTime"));
    }

    private Course findOrCreateBiliCourse() {
        for (Course c : courseRepository.findAll()) {
            if (BILI_COURSE_NAME.equals(c.getName())) {
                return c;
            }
        }
        Course c = new Course(1L, BILI_COURSE_NAME, "Bilibili", 9999, 0);
        return courseRepository.save(c);
    }


    private String buildPrompt(String notes) {
        return "你是一位严谨的学科老师。下面是一段课程视频的学习笔记：\n\"\"\"\n"
                + notes
                + "\n\"\"\"\n请根据笔记内容生成 3 道考查理解程度的题目，题型须同时包含「选择题」和「简答题」。\n"
                + "只返回一个 JSON 对象，结构为：{\"questions\":[ ... ]}，不要输出任何多余文字或 markdown 代码块。\n"
                + "每道题结构：{\"knowledgePoint\":\"知识点\",\"type\":\"选择题|简答题\",\"stem\":\"题干\","
                + "\"options\":[\"A. ...\",\"B. ...\",\"C. ...\",\"D. ...\"],\"answer\":\"答案\",\"explanation\":\"解析\"}。\n"
                + "要求：选择题 options 为 4 个字符串、answer 为选项字母（如 \"B\"）；简答题 options 为空数组 []、answer 为要点文字；knowledgePoint 需具体。";
    }

    private String callZhipu(String prompt) {
        String apiKey = zhipuProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("未配置智谱 API Key（请设置环境变量 ZHIPU_API_KEY）");
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", zhipuProperties.getModel());
        body.put("messages", List.of(message));
        body.put("temperature", 0.7);
        body.put("response_format", Map.of("type", "json_object"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        final HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String url = zhipuProperties.getBaseUrl() + "/chat/completions";

        // 指数退避重试：429 限流 / 5xx / 超时都视为「暂时性失败」，等待后重试
        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                long delay = RETRY_DELAYS_MS[Math.min(attempt - 1, RETRY_DELAYS_MS.length - 1)];
                logRetry(attempt, delay);
                sleep(delay);
            }
            try {
                Map<String, Object> resp = restTemplate.postForObject(url, request, Map.class);
                if (resp == null || !resp.containsKey("choices")) {
                    // 返回体异常一般是模型侧临时问题，同样进入重试
                    lastError = new RuntimeException("智谱接口返回异常，请稍后重试");
                    continue;
                }
                List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
                Map<String, Object> messageOut = (Map<String, Object>) choices.get(0).get("message");
                return (String) messageOut.get("content");
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                String respBody = safeBody(e);
                if (isTransientFailure(status, respBody)) {
                    lastError = new RuntimeException(BUSY_MSG);
                    continue; // 429 / 5xx / 明确的限流文案 → 退避后重试
                }
                if (status == 401 || status == 403) {
                    throw new RuntimeException("智谱 API Key 无效或无权限，请检查环境变量 ZHIPU_API_KEY");
                }
                throw new RuntimeException("调用智谱接口失败（HTTP " + status + "），请稍后重试");
            } catch (ResourceAccessException e) {
                // 超时 / 网络抖动 → 也重试
                Throwable root = rootCause(e);
                if (root instanceof SocketTimeoutException) {
                    lastError = new RuntimeException("AI 接口响应超时，请稍后重试");
                } else {
                    lastError = new RuntimeException("连接智谱服务器失败，请检查网络后重试");
                }
                continue;
            } catch (Exception e) {
                throw new RuntimeException("调用智谱接口失败，请稍后重试");
            }
        }
        // 重试 3 次仍失败：不再把英文报错抛给用户
        throw lastError != null ? lastError : new RuntimeException(BUSY_MSG);
    }

    private static final String BUSY_MSG = "AI 服务器当前繁忙，请稍后重试";

    /** 是否为「暂时性失败」：429 限流、5xx 服务端故障、或返回体含限流标志 */
    private boolean isTransientFailure(int status, String respBody) {
        if (status == 429 || status == 500 || status == 502 || status == 503 || status == 504) {
            return true;
        }
        return respBody != null && (respBody.contains("1305")
                || respBody.contains("Too Many Requests")
                || respBody.contains("访问量过大"));
    }

    private String safeBody(HttpStatusCodeException e) {
        try {
            return e.getResponseBodyAsString();
        } catch (Exception ex) {
            return null;
        }
    }

    private Throwable rootCause(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private void logRetry(int attempt, long delayMs) {
        System.out.println("[智谱AI] 请求暂时失败（限流/繁忙），" + (delayMs / 1000)
                + " 秒后进行第 " + attempt + "/" + MAX_RETRIES + " 次重试...");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private List<Map<String, Object>> parseQuestions(String content) {
        if (content == null) {
            throw new RuntimeException("模型返回内容为空");
        }
        String text = content.trim();
        // 兜底：去掉 ```json ... ``` 代码块包裹
        if (text.startsWith("```")) {
            int end = text.lastIndexOf("```");
            int start = text.indexOf('\n');
            if (start < 0) start = 2;
            text = text.substring(start + 1, end >= 0 ? end : text.length());
        }
        text = text.trim();
        // 兜底：若返回的是数组（非对象），截取 [ ... ]
        int a = text.indexOf('[');
        int b = text.lastIndexOf(']');
        if (a != -1 && b != -1 && b > a) {
            text = text.substring(a, b + 1);
        }
        Object parsed;
        try {
            parsed = objectMapper.readValue(text, Object.class);
        } catch (Exception e) {
            throw new RuntimeException("题目 JSON 解析失败: " + e.getMessage());
        }
        if (parsed instanceof List) {
            return (List<Map<String, Object>>) parsed;
        }
        if (parsed instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) parsed;
            for (String key : new String[]{"questions", "data", "items", "list"}) {
                Object v = m.get(key);
                if (v instanceof List) {
                    return (List<Map<String, Object>>) v;
                }
            }
        }
        throw new RuntimeException("无法从模型返回内容解析出题目列表");
    }

    private Question toQuestion(Map<String, Object> m, Long videoRecordId) {
        Question q = new Question();
        q.setVideoRecordId(videoRecordId);
        q.setKnowledgePoint(str(m.get("knowledgePoint")));
        q.setType(str(m.get("type")));
        q.setStem(str(m.get("stem")));
        q.setOptions(optionsToJson(m.get("options")));
        q.setAnswer(str(m.get("answer")));
        q.setExplanation(str(m.get("explanation")));
        q.setCreatedTime(LocalDateTime.now());
        return q;
    }

    private String optionsToJson(Object opts) {
        if (opts == null) {
            return "[]";
        }
        if (opts instanceof List) {
            try {
                return objectMapper.writeValueAsString(opts);
            } catch (Exception e) {
                return "[]";
            }
        }
        return str(opts);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
