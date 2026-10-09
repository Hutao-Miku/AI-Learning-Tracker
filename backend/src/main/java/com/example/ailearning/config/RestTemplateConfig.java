package com.example.ailearning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * 超时可配置（application.yml 的 zhipu.api.connect-timeout-seconds / read-timeout-seconds）。
     * 默认连接 10s、读取 30s：AI 生成超过 30 秒未响应即中断，配合 Service 层重试机制避免卡死。
     */
    @Bean
    public RestTemplate restTemplate(ZhipuProperties zhipuProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(zhipuProperties.getConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(zhipuProperties.getReadTimeoutSeconds()));
        return new RestTemplate(factory);
    }
}
