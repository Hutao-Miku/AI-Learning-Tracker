package com.example.ailearning.controller;

import com.example.ailearning.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统控制：供浏览器插件在用户关闭最后一个前端标签页时，触发后端优雅退出。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 优雅退出接口。
     *
     * 安全限制：仅接受来自浏览器扩展的请求——
     *   1) Origin 以 "chrome-extension://" 开头（扩展 Service Worker 发起的跨域请求正常携带此 Origin）；
     *   2) 兜底：Origin 为 null 且携带专属令牌头 "X-StudyTrace: 1"
     *      （极少数 Chrome 版本 Service Worker 的 fetch 不携带 Origin，用令牌兜底；
     *       普通网页跨域请求的 Origin 是它自己的域名，绝不会是 null，因此无法绕过）。
     * 任意网页直接调用会被 403 拒绝。
     *
     * 行为：先返回成功响应，再延迟 1 秒执行退出，确保响应先完整送达插件，
     * 避免插件侧因连接被立即切断而报网络错误。退出使用 SpringApplication.exit，
     * 让 Spring 容器优雅关闭，自动释放 H2 数据库文件锁与 8080 端口。
     */
    @PostMapping("/shutdown")
    public Result<String> shutdown(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        String token = request.getHeader("X-StudyTrace");

        boolean fromExtension = origin != null && origin.startsWith("chrome-extension://");
        boolean fromExtensionSw = origin == null && "1".equals(token);

        if (!fromExtension && !fromExtensionSw) {
            return Result.error(403, "拒绝访问：仅允许浏览器扩展调用本接口");
        }

        // 先让响应顺利返回，再延迟 1 秒优雅退出
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            SpringApplication.exit(applicationContext);
        }, "shutdown-hook-thread").start();

        return Result.success("正在关闭后端服务…");
    }
}
