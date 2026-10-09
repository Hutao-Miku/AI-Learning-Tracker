package com.example.ailearning.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智谱 AI 对话客户端（通用文本对话，非 JSON 模式）。
 * 内置与题目生成一致的指数退避重试机制：429 限流 / 5xx / 超时 自动重试（2s、4s、8s，共 3 次），
 * 最终失败返回友好中文错误，绝不把英文堆栈抛给用户。
 */
@Component
public class ZhipuAiClient {

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {2000, 4000, 8000};
    private static final String BUSY_MSG = "AI 服务器当前繁忙，请稍后重试";

    private final ZhipuProperties zhipuProperties;
    private final RestTemplate restTemplate;

    @Autowired
    public ZhipuAiClient(ZhipuProperties zhipuProperties, RestTemplate restTemplate) {
        this.zhipuProperties = zhipuProperties;
        this.restTemplate = restTemplate;
    }

    /**
     * 发送一段提示词，返回模型生成的纯文本（用于学情分析报告等自由文本场景）。
     */
    public String chat(String prompt) {
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
        body.put("temperature", 0.8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String url = zhipuProperties.getBaseUrl() + "/chat/completions";

        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                long delay = RETRY_DELAYS_MS[Math.min(attempt - 1, RETRY_DELAYS_MS.length - 1)];
                System.out.println("[智谱AI] 请求暂时失败（限流/繁忙），" + (delay / 1000)
                        + " 秒后进行第 " + attempt + "/" + MAX_RETRIES + " 次重试...");
                sleep(delay);
            }
            try {
                Map<String, Object> resp = restTemplate.postForObject(url, request, Map.class);
                if (resp == null || !resp.containsKey("choices")) {
                    lastError = new RuntimeException(BUSY_MSG);
                    continue;
                }
                List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
                Map<String, Object> messageOut = (Map<String, Object>) choices.get(0).get("message");
                Object content = messageOut.get("content");
                return content == null ? "" : content.toString();
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                String respBody = safeBody(e);
                if (isTransientFailure(status, respBody)) {
                    lastError = new RuntimeException(BUSY_MSG);
                    continue;
                }
                if (status == 401 || status == 403) {
                    throw new RuntimeException("智谱 API Key 无效或无权限，请检查环境变量 ZHIPU_API_KEY");
                }
                throw new RuntimeException("调用智谱接口失败（HTTP " + status + "），请稍后重试");
            } catch (ResourceAccessException e) {
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
        throw lastError != null ? lastError : new RuntimeException(BUSY_MSG);
    }

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

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
