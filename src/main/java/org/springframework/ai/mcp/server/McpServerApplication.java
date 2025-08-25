package org.springframework.ai.mcp.server;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.List;

@SpringBootApplication
@ComponentScan(basePackages = {"org.springframework.ai.mcp"})
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider toolCallbacks(ApplicationContext ctx) {
        // 모든 빈 중 @Tool 메서드를 1개 이상 가진 빈 자동 수집
        List<Object> toolBeans = ctx.getBeansOfType(Object.class).values().stream()
                .filter(this::hasToolAnnotatedMethod)
                .toList();

        return MethodToolCallbackProvider.builder()
                .toolObjects(toolBeans.toArray(new Object[0]))
                .build();
    }

    private boolean hasToolAnnotatedMethod(Object bean) {
        Class<?> targetClass = ClassUtils.getUserClass(bean);
        for (Method m : targetClass.getMethods()) {
            if (m.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class)) {
                return true;
            }
        }
        return false;
    }

    public record TextInput(String input) {}
}
