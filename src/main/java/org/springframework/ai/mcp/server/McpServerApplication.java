package org.springframework.ai.mcp.server;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpServerApplication {

    /**
     * 애플리케이션의 진입점(시작점)입니다.
     * Java 프로그램이 실행될 때 가장 먼저 호출되는 메서드입니다.
     *
     * @param args 명령줄에서 전달받은 인수들 (예: java -jar app.jar arg1 arg2)
     */
    public static void main(String[] args) {
        // Spring Boot 애플리케이션을 시작합니다
        // 이 한 줄로 웹 서버 시작, 의존성 주입, 자동 설정 등이 모두 실행됩니다
        SpringApplication.run(McpServerApplication.class, args);
    }

    /**
     * 날씨 서비스의 메서드들을 AI 도구로 자동 등록하는 Bean을 생성합니다.
     *
     * @Bean: 이 어노테이션이 붙은 메서드의 반환값을 Spring 컨테이너가 관리하는 객체(Bean)로 등록합니다
     * @param weatherService Spring이 자동으로 주입해주는 WeatherService 객체
     * @return ToolCallbackProvider - AI가 사용할 수 있는 도구들의 모음
     */
    @Bean
    public ToolCallbackProvider weatherTools(WeatherService weatherService) {
        // WeatherService의 @Tool 어노테이션이 붙은 메서드들을 자동으로 찾아서
        // AI가 호출할 수 있는 도구로 등록합니다
        return MethodToolCallbackProvider.builder()
                .toolObjects(weatherService)  // WeatherService 객체를 도구 소스로 등록
                .build();
    }

    /**
     * 텍스트 입력을 받기 위한 데이터 클래스입니다.
     *
     * Record는 Java 17의 새로운 기능으로, 다음을 자동으로 생성해줍니다:
     * - private final String input; (필드)
     * - public TextInput(String input) {} (생성자)
     * - public String input() {} (getter 메서드)
     * - equals(), hashCode(), toString() 메서드들
     *
     * 사용 예시:
     * TextInput input = new TextInput("hello");
     * String text = input.input(); // "hello" 반환
     */
    public record TextInput(String input) {
        // Record는 내용이 비어있어도 자동으로 필요한 메서드들을 생성합니다
    }

    /**
     * 문자열을 대문자로 변환하는 AI 도구를 생성합니다.
     *
     * @Bean: Spring 컨테이너에 도구로 등록
     * @return ToolCallback - AI가 호출할 수 있는 단일 함수 도구
     */
    @Bean
    public ToolCallback toUpperCase() {
        // FunctionToolCallback.builder()로 함수형 도구를 만듭니다
        return FunctionToolCallback.builder(
                        "toUpperCase",  // 도구의 이름 (AI가 이 이름으로 호출합니다)
                        // 실제 실행될 함수: TextInput을 받아서 대문자로 변환된 문자열을 반환
                        // (TextInput input) -> input.input().toUpperCase()
                        // 예: TextInput("hello") -> "HELLO"
                        (TextInput input) -> input.input().toUpperCase()
                )
                .inputType(TextInput.class)  // 입력 타입을 명시 (JSON 변환에 사용)
                .description("Put the text to upper case")  // AI에게 보여줄 도구 설명
                .build();  // 도구 객체 생성 완료
    }
}
