# Spring AI를 사용한 MCP 서버

Spring AI 프레임워크를 사용하여 구현한 MCP(Model Context Protocol) 서버로, 날씨 정보 조회와 Git 커밋 메시지 자동 생성을 위한 지능형 도구를 제공합니다.

## 주요 기능

- **날씨 서비스**: 외부 API를 통한 날씨 정보 조회
- **커밋 메시지 생성기**: Git 변경 사항을 AI가 분석하여 자동으로 커밋 메시지 생성
- **다중 AI 제공자 지원**: Ollama와 OpenAI 모두 지원

## 사전 요구사항

- Java 17 이상
- Gradle
- Ollama (로컬 AI 모델 사용 시) 또는 OpenAI API 키

## 빠른 시작

1. **저장소 클론**
   ```bash
   git clone <저장소-URL>
   cd mcp-server-use-spring
   ```

2. **AI 제공자 설정**
   
   `src/main/resources/application.yml` 파일을 편집:
   
   **Ollama 사용 시 (기본 설정):**
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
   
   **OpenAI 사용 시:**
   ```yaml
   spring:
     ai:
       provider: openai
       openai:
         api-key: 실제-OpenAI-API-키-입력
         chat:
           options:
             model: gpt-4o-mini
   ```

3. **애플리케이션 실행**
   ```bash
   ./gradlew bootRun
   ```

## 사용 가능한 도구

### 날씨 서비스
- 현재 날씨 조건 조회
- 날씨 예보
- 날씨 경보

### 커밋 메시지 생성기
- Git 변경 사항 자동 분석
- 상황에 맞는 커밋 메시지 생성
- 다양한 변경 유형 지원 (기능 추가, 버그 수정, 리팩토링 등)

## 프로젝트 구조

```
src/main/java/org/springframework/ai/mcp/
├── config/          # 설정 클래스
├── controller/      # REST 컨트롤러
├── dto/            # 데이터 전송 객체
├── server/         # 메인 애플리케이션 클래스
├── service/        # 비즈니스 로직 서비스
└── util/           # 유틸리티 클래스
```

## 개발

### 빌드
```bash
./gradlew build
```

### 테스트
```bash
./gradlew test
```

### JAR 파일 생성 및 MCP 서버 등록

1. **실행 가능한 JAR 파일 생성**
   ```bash
   ./gradlew bootJar
   ```
   생성된 JAR 파일 위치: `build/libs/mcp-server-use-spring-0.0.1-SNAPSHOT.jar`

2. **Claude Desktop에서 MCP 서버 등록**
   
   Claude Desktop 설정 파일을 편집합니다:
   - **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
   - **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

   ```json
   {
     "mcpServers": {
       "spring-mcp-server": {
         "command": "java",
         "args": [
           "-jar",
           "/절대/경로/to/mcp-server-use-spring/build/libs/mcp-server-use-spring-0.0.1-SNAPSHOT.jar"
         ],
         "env": {
           "SPRING_PROFILES_ACTIVE": "production"
         }
       }
     }
   }
   ```

3. **환경별 설정 (선택사항)**
   
   프로덕션 환경용 설정 파일 생성: `src/main/resources/application-production.yml`
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

4. **Claude Desktop 재시작**
   
   설정을 적용하려면 Claude Desktop을 완전히 종료하고 다시 시작합니다.

5. **연결 확인**
   
   Claude에서 다음과 같이 도구 사용 가능 여부를 확인할 수 있습니다:
   - 날씨 정보 조회
   - Git 커밋 메시지 생성

## 설정

애플리케이션은 Spring Boot의 자동 구성을 사용합니다. 주요 설정 파일:

- `application.yml` - 메인 애플리케이션 설정
- `build.gradle` - 의존성 및 빌드 설정

## 의존성

- Spring Boot 3.4.5
- Spring AI 1.0.1 (MCP Server WebMVC, OpenAI, Ollama)
- Lombok (보일러플레이트 코드 감소)