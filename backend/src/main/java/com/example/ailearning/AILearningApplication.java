package com.example.ailearning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.PortInUseException;

@SpringBootApplication
public class AILearningApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(AILearningApplication.class, args);
        } catch (Exception ex) {
            PortInUseException portEx = findCause(ex, PortInUseException.class);
            if (portEx != null) {
                System.err.println();
                System.err.println("========================================================");
                System.err.println("  启动失败：端口 " + portEx.getPort() + " 已被占用，应用无法启动。");
                System.err.println("  端口被占用，请先关闭旧进程（如旧的后端窗口按 Ctrl+C），再重新启动。");
                System.err.println("========================================================");
                System.err.println();
                System.exit(1);
            }
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        while (t != null) {
            if (type.isInstance(t)) {
                return (T) t;
            }
            t = t.getCause();
        }
        return null;
    }
}
