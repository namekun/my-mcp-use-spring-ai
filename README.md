# Spring AI 기반 MCP 서버 (한국어 가이드)

이 저장소는 Spring AI 프레임워크로 구현한 MCP(Model Context Protocol) 서버 예제입니다. 날씨 정보 조회와 Git 변경 내역을 기반으로 한 커밋 메시지 자동 생성 도구를 제공합니다.

## 주요 기능

- 날씨 서비스: 미국 국립 기상청 API를 사용한 예보/경보 조회
- 커밋 메시지 생성기: Git diff를 분석하여 상황에 맞는 커밋 메시지 자동 생성
- 다중 AI 제공자: Ollama(로컬)와 OpenAI 모두 지원

## 사전 요구사항

- Java 17 이상
- Gradle
- Ollama(로컬 AI 모델을 사용할 경우) 또는 OpenAI API 키

## 빠른 시작

1. 저장소 클론
   ```bash
   git clone <REPO_URL>
   cd my-mcp-use-spring-ai
   ```

2. AI 제공자 설정
   
   `src/main/resources/application.yml` 파일을 편집합니다.
   
   Ollama 사용 시(기본 예시):
   ```yaml
   spring:
     ai:
       provider: ollama
       ollama:
         base-url: http://localhost:11434
         chat:
           options:
             model: gemma3:12b-it-qat
   ```
   
   OpenAI 사용 시:
   ```yaml
   spring:
     ai:
       provider: openai
       openai:
         api-key: <YOUR_OPENAI_API_KEY>
         chat:
           options:
             model: gpt-4o-mini
   ```

3. 애플리케이션 실행
   ```bash
   ./gradlew bootRun
   ```

## 사용 가능한 도구

- 날씨 서비스
  - 단기 예보(periods) 조회
  - 주(州) 단위 기상 경보 조회
- 커밋 메시지 생성기
  - Git 변경 사항 자동 분석 및 메시지 제안
  - 기능 추가, 버그 수정, 리팩토링 등 유형 반영

## 프로젝트 구조

```
src/main/java/org/springframework/ai/mcp/
├── config/       # 설정 클래스
├── controller/   # REST 컨트롤러
├── server/       # 메인 애플리케이션 클래스
├── service/      # 비즈니스 로직 서비스(날씨, 커밋 메시지)
└── util/         # 유틸리티 클래스(Git 도구 등)
```

주의: 기존 문서에 표기된 dto 디렉터리는 현재 프로젝트에 존재하지 않습니다(정리 반영).

## 개발

빌드
```bash
./gradlew build
```

테스트
```bash
./gradlew test
```

### JAR 파일 생성 및 MCP 서버 등록

1) 실행 가능한 JAR 파일 생성
```bash
./gradlew bootJar
```
생성 경로 예시: `build/libs/mcp-weather-starter-webmvc-server-0.0.1-SNAPSHOT.jar` (settings.gradle의 rootProject.name 기준)

2) Claude Desktop에서 MCP 서버 등록

- 설정 파일 위치
  - macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`
  - Windows: `%APPDATA%/Claude/claude_desktop_config.json`

예시 설정
```json
{
  "mcpServers": {
    "spring-mcp-server": {
      "command": "java",
      "args": [
        "-jar",
        "/절대/경로/to/my-mcp-use-spring-ai/build/libs/mcp-weather-starter-webmvc-server-0.0.1-SNAPSHOT.jar"
      ],
      "env": {
        "SPRING_PROFILES_ACTIVE": "production"
      }
    }
  }
}
```

3) 환경별 설정(선택)

프로덕션 프로필용 예시: `src/main/resources/application-production.yml`
```yaml
spring:
  ai:
    provider: ollama
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: gemma3:12b-it-qat

logging:
  level:
    org.springframework.ai: INFO
    org.springframework.boot: INFO
  file:
    name: ./logs/mcp-server.log
```

4) Claude Desktop 재시작 후 도구 연결 확인
- 날씨 예보/경보 조회 가능 여부
- Git 커밋 메시지 생성 가능 여부

## 추가 설정 파일

- `application.yml` — 메인 애플리케이션 설정
- `mcp-servers-config.json` — MCP 서버 통합 예시(필요 시 참조)
- `build.gradle` — 의존성 및 빌드 설정

## 의존성 버전

- Spring Boot 3.4.5
- Spring AI 1.0.1 (MCP Server WebMVC, OpenAI, Ollama)
- Lombok (보일러플레이트 코드 감소)