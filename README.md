# Spring AI MCP Weather Server 샘플 (WebMVC Starter)

이 샘플 프로젝트는 Spring AI MCP Server Boot Starter와 WebMVC 전송을 사용하여 MCP 서버를 만드는 방법을 보여줍니다. 국립기상청 API를 사용하여 날씨 정보를 검색하는 도구를 제공하는 날씨 서비스를 구현합니다.

자세한 정보는 [MCP Server Boot Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) 참조 문서를 확인하세요.

## 개요

이 샘플은 다음 기능을 보여줍니다:
- `spring-ai-mcp-server-webmvc-spring-boot-starter`와의 통합
- SSE(Server-Sent Events)와 STDIO 전송 방식 모두 지원
- Spring AI의 `@Tool` 어노테이션을 사용한 자동 도구 등록
- 두 가지 날씨 관련 도구:
    - 위치별 날씨 예보 조회 (위도/경도)
    - 미국 주별 날씨 경보 조회

## 의존성

프로젝트에는 Spring AI MCP Server WebMVC Boot Starter가 필요합니다:

```gradle
dependencies {
    implementation 'org.springframework.ai:spring-ai-mcp-server-webmvc-spring-boot-starter'
}
```

이 스타터는 다음을 제공합니다:
- Spring MVC를 사용한 HTTP 기반 전송 (`WebMvcSseServerTransport`)
- 자동 구성된 SSE 엔드포인트
- 선택적 STDIO 전송
- `spring-boot-starter-web`과 `mcp-spring-webmvc` 의존성 포함

## 프로젝트 빌드

Gradle을 사용하여 프로젝트를 빌드합니다:
```bash
./gradlew clean build -x test
```

## 서버 실행

서버는 두 가지 전송 모드를 지원합니다:

### WebMVC SSE 모드 (기본값)
```bash
java -jar build/libs/mcp-weather-starter-webmvc-server-0.0.1-SNAPSHOT.jar
```

### STDIO 모드
STDIO 전송을 활성화하려면 적절한 속성을 설정하세요:
```bash
java -Dspring.ai.mcp.server.stdio=true -Dspring.main.web-application-type=none -jar build/libs/mcp-weather-starter-webmvc-server-0.0.1-SNAPSHOT.jar
```

## 설정

`application.yml`을 통해 서버를 구성합니다:

```yaml
spring:
  ai:
    mcp:
      server:
        name: my-weather-server
        version: 0.0.1
        type: SYNC
        stdio: false
        sse-message-endpoint: /mcp/message
        resource-change-notification: true
        tool-change-notification: true
        prompt-change-notification: true
  main:
    banner-mode: off

logging:
  file:
    name: ./build/starter-webmvc-server.log
```

## 사용 가능한 도구

### 날씨 예보 도구
- 이름: `getWeatherForecastByLocation`
- 설명: 특정 위도/경도의 날씨 예보 조회
- 매개변수:
    - `latitude`: double - 위도 좌표
    - `longitude`: double - 경도 좌표

### 날씨 경보 도구
- 이름: `getAlerts`
- 설명: 미국 주의 날씨 경보 조회
- 매개변수:
    - `state`: String - 미국 주 코드 2글자 (예: CA, NY)

## 서버 구현

서버는 Spring Boot와 Spring AI의 도구 어노테이션을 사용하여 자동 도구 등록을 수행합니다:

```java
@SpringBootApplication
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider weatherTools(WeatherService weatherService){
      return MethodToolCallbackProvider.builder().toolObjects(weatherService).build();
    }
}
```

`WeatherService`는 `@Tool` 어노테이션을 사용하여 날씨 도구를 구현합니다:

```java
@Service
public class WeatherService {
    @Tool(description = "특정 위도/경도의 날씨 예보를 조회합니다")
    public String getWeatherForecastByLocation(double latitude, double longitude) {
        // weather.gov API를 사용한 구현
    }

    @Tool(description = "미국 주의 날씨 경보를 조회합니다. 입력값은 미국 주 코드 2글자입니다 (예: CA, NY)")
    public String getAlerts(String state) {
        // weather.gov API를 사용한 구현
    }
}
```

## MCP 클라이언트

STDIO 또는 SSE 전송을 사용하여 날씨 서버에 연결할 수 있습니다:

### 수동 클라이언트

#### WebMVC SSE 클라이언트

SSE 전송을 사용하는 서버의 경우:

```java
var transport = HttpClientSseClientTransport.builder("http://localhost:8080").build();
var client = McpClient.sync(transport).build();
```

#### STDIO 클라이언트

STDIO 전송을 사용하는 서버의 경우:

```java
var stdioParams = ServerParameters.builder("java")
    .args("-Dspring.ai.mcp.server.stdio=true",
          "-Dspring.main.web-application-type=none",
          "-Dspring.main.banner-mode=off",
          "-Dlogging.pattern.console=",
          "-jar",
          "build/libs/mcp-weather-starter-webmvc-server-0.0.1-SNAPSHOT.jar")
    .build();

var transport = new StdioClientTransport(stdioParams);
var client = McpClient.sync(transport).build();
```

샘플 프로젝트에는 다음 클라이언트 구현 예제가 포함되어 있습니다:
- [SampleClient.java](src/test/java/org/springframework/ai/mcp/sample/client/SampleClient.java): 수동 MCP 클라이언트 구현
- [ClientStdio.java](src/test/java/org/springframework/ai/mcp/sample/client/ClientStdio.java): STDIO 전송 연결
- [ClientSse.java](src/test/java/org/springframework/ai/mcp/sample/client/ClientSse.java): SSE 전송 연결

더 나은 개발 경험을 위해 [MCP Client Boot Starters](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html) 사용을 고려해보세요. 이 스타터들은 MCP 서버에 대한 여러 STDIO 및/또는 SSE 연결의 자동 구성을 가능하게 합니다. 예제는 [starter-default-client](../../client-starter/starter-default-client) 프로젝트를 참조하세요.

### Boot Starter 클라이언트

[starter-default-client](../../client-starter/starter-default-client) 클라이언트를 사용하여 날씨 `starter-webmvc-server`에 연결해보겠습니다.

`starter-default-client` readme 지침에 따라 `mcp-starter-default-client-0.0.1-SNAPSHOT.jar` 클라이언트 애플리케이션을 빌드하세요.

#### STDIO 전송

1. 다음 내용으로 `mcp-servers-config.json` 구성 파일을 생성합니다:

```json
{
  "mcpServers": {
    "weather-starter-webmvc-server": {
      "command": "java",
      "args": [
        "-Dspring.ai.mcp.server.stdio=true",
        "-Dspring.main.web-application-type=none",
        "-Dlogging.pattern.console=",
        "-jar",
        "/absolute/path/to/mcp-weather-starter-webmvc-server-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

2. 구성 파일을 사용하여 클라이언트를 실행합니다:

```bash
java -Dspring.ai.mcp.client.stdio.servers-configuration=file:mcp-servers-config.json \
 -Dai.user.input='NY의 날씨는 어떤가요?' \
 -Dlogging.pattern.console= \
 -jar mcp-starter-default-client-0.0.1-SNAPSHOT.jar
```

#### SSE (WebMVC) 전송

1. `mcp-weather-starter-webmvc-server`를 시작합니다:

```bash
java -jar build/libs/mcp-weather-starter-webmvc-server-0.0.1-SNAPSHOT.jar
```

8080 포트에서 MCP 서버가 시작됩니다.

2. 다른 콘솔에서 SSE 전송으로 구성된 클라이언트를 시작합니다:

```bash
java -Dspring.ai.mcp.client.sse.connections.weather-server.url=http://localhost:8080 \
 -Dlogging.pattern.console= \
 -Dai.user.input='NY의 날씨는 어떤가요?' \
 -jar mcp-starter-default-client-0.0.1-SNAPSHOT.jar
```

## Claude Desktop에서 사용하기

Claude Desktop에서 이 MCP 서버를 사용하려면 `mcp.json` 파일을 구성해야 합니다.

### JAR 파일로 실행

Claude Desktop 설정 폴더에서 `mcp.json` 파일을 생성하거나 수정합니다:

**macOS**: `~/Library/Application Support/Claude/mcp.json`  
**Windows**: `%APPDATA%\Claude\mcp.json`

```json
{
  "mcpServers": {
    "spring-weather-server": {
      "command": "java",
      "args": [
        "-Dspring.ai.mcp.server.stdio=true",
        "-Dspring.main.web-application-type=none",
        "-Dspring.main.banner-mode=off",
        "-Dlogging.pattern.console=",
        "-jar",
        "/절대경로/mcp-weather-starter-webmvc-server-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

### Docker로 실행

먼저 Dockerfile을 생성합니다:

```dockerfile
FROM openjdk:17-jre-slim

COPY build/libs/mcp-weather-starter-webmvc-server-0.0.1-SNAPSHOT.jar /app/app.jar

WORKDIR /app

ENTRYPOINT ["java", "-Dspring.ai.mcp.server.stdio=true", "-Dspring.main.web-application-type=none", "-Dspring.main.banner-mode=off", "-Dlogging.pattern.console=", "-jar", "app.jar"]
```

그리고 `mcp.json`에서 Docker를 사용하도록 구성합니다:

```json
{
  "mcpServers": {
    "spring-weather-server": {
      "command": "docker",
      "args": [
        "run",
        "--rm",
        "-i",
        "spring-weather-mcp:latest"
      ]
    }
  }
}
```

Docker 이미지 빌드:
```bash
./gradlew clean build -x test
docker build -t spring-weather-mcp:latest .
```

### 설정 완료 후

1. Claude Desktop을 재시작합니다
2. 새로운 대화에서 다음과 같이 질문할 수 있습니다:
    - "뉴욕의 날씨는 어떤가요?"
    - "캘리포니아주에 날씨 경보가 있나요?"
    - "위도 40.7128, 경도 -74.0060의 날씨 예보를 알려주세요"

## 추가 리소스

* [Spring AI 문서](https://docs.spring.io/spring-ai/reference/)
* [MCP Server Boot Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
* [MCP Client Boot Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html)
* [Model Context Protocol 사양](https://modelcontextprotocol.github.io/specification/)
* [Spring Boot 자동 구성](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration)