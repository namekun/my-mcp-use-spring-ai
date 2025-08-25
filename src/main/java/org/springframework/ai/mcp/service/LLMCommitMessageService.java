package org.springframework.ai.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.util.AutoGitExecutor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class LLMCommitMessageService {
    
    private final OllamaChatModel chatModel;
    private final GitExecutor git;
    private final String llmProvider;
    private final String modelName;
    
    public LLMCommitMessageService(OllamaChatModel chatModel,
                                  @Value("${spring.ai.provider}") String llmProvider,
                                  @Value("${spring.ai.ollama.chat.options.model}") String ollamaModel) {
        this.chatModel = chatModel;
        this.git = new AutoGitAdapter();
        this.llmProvider = llmProvider;
        this.modelName = ollamaModel;
        
        // 디버깅 정보 출력
        System.out.println("=== LLMCommitMessageService 디버깅 ===");
        System.out.println("ChatModel 주입됨: " + (chatModel != null));
        if (chatModel != null) {
            System.out.println("ChatModel 클래스: " + chatModel.getClass().getName());
        }
        System.out.println("LLM 제공자: " + llmProvider);
        System.out.println("모델명: " + modelName);
        System.out.println("=====================================");
        
        String status = chatModel == null ? "사용 가능" : "사용 불가 (fallback 모드)";
        log.info("[LLMCommitMessageService] 초기화됨 - 제공자: {}, 모델: {}, 상태: {}", llmProvider, modelName, status);
    }
    
    @Tool(description = "현재 LLM 연결 상태를 확인합니다")
    public String checkLLMConnection() {
        if (!chatModel.getDefaultOptions().getModel().equals(modelName)) {
            return "❌ LLM 모델이 연결되지 않음 - fallback 모드에서 실행 중\n" +
                   "설정된 제공자: " + llmProvider + "\n" +
                   "설정된 모델: " + modelName + "\n" +
                   "해결방법: application.yml에서 올바른 LLM 서버 설정을 확인하세요.";
        }

        ChatOptions opts = chatModel.getDefaultOptions();
        String configuredModel = (opts != null && opts.getModel() != null) ? opts.getModel() : "(미설정)";
        log.info("configuerModel: {}", configuredModel);

        try {
            // 간단한 테스트 메시지로 연결 확인
            String response = callWithRetry(() ->
                            chatModel.call(new Prompt("Hello, respond with just 'OK'"))
                                    .getResult().getOutput().toString(),
                    3, Duration.ofMillis(400)
            );

            return "✅ LLM 연결 성공!\n" +
                   "제공자: " + llmProvider + "\n" +
                   "모델: " + modelName + "\n" +
                   "테스트 응답: " + response.trim();
        } catch (Exception e) {
            return "❌ LLM 연결 실패\n" +
                   "제공자: " + llmProvider + "\n" +
                   "모델: " + modelName + "\n" +
                   "오류: " + e + "\n" +
                   "해결방법: LLM 서버가 실행 중인지, 네트워크 연결이 가능한지 확인하세요.";
        }
    }

    @Tool(description = "LLM이 git diff를 분석하여 적절한 커밋 메시지를 생성합니다")
    public CommitSuggestionResponse generateCommitMessage(CommitSuggestionRequest request) {
        long t0 = System.nanoTime(); // 측정 시작

        boolean stagedFirst = request != null ? request.stagedFirst() : true;
        int maxSuggestions = request != null ? request.maxSuggestions() : 9;
        
        // Git diff 수집
        String diff = collectDiff(stagedFirst);
        List<String> files = collectChangedFiles(stagedFirst);
        
        if (diff == null || diff.isBlank()) {
            return new CommitSuggestionResponse(List.of(), "변경사항이 없습니다.");
        }
        
        // ChatModel이 없으면 fallback 메시지 반환
        if (chatModel == null) {
            log.warn("ChatModel이 주입되지 않았습니다. fallback 모드로 실행됩니다.");
            List<String> fallbackMessages = List.of(
                "feat(core): 코드 변경사항 반영",
                "refactor(core): 코드 개선 및 정리",
                "chore(core): 파일 업데이트"
            );
            return new CommitSuggestionResponse(fallbackMessages, 
                "LLM 서비스가 설정되지 않아 기본 메시지를 생성했습니다. " +
                "OPENAI_API_KEY 또는 Ollama 서버를 설정해주세요.");
        }
        
        log.info("ChatModel이 주입됨: {}", chatModel.getClass().getSimpleName());
        
        try {
            // LLM에게 커밋 메시지 생성 요청
            String promptText = buildPrompt(diff, files, maxSuggestions);
            Prompt prompt = new Prompt(promptText);

            log.info("[LLMCommitMessageService] {} ({})로 커밋 메시지 생성 중...", llmProvider, modelName);

            String response = chatModel.call(prompt).getResult().getOutput().toString();

            // 영어로만 되어있는 커밋 메세지 금지
            if (looksEnglishDominant(response)) {
                Prompt retryPrompt = new Prompt(
                        List.of(
                                new SystemMessage("이전 출력은 규칙 위반이다. 이번에는 반드시 100% 한국어로만, 지정 형식만 출력하라."),
                                new UserMessage(buildPrompt(diff, files, maxSuggestions))
                        )
                );
                response = chatModel.call(retryPrompt).getResult().getOutput().toString();
            }

            // 응답 파싱
            List<String> suggestions = parseCommitMessages(response);

            Duration elapsed = Duration.ofNanos(System.nanoTime() - t0); // 측정 종료
            log.info("[LLMCommitMessageService] 커밋 메시지 생성 완료 - 소요시간: {} ms ({} s), provider={}, model={}, suggestions={}",
                    elapsed.toMillis(), toSeconds(elapsed), llmProvider, modelName, suggestions.size());

            String resultMessage = String.format("%s (%s)로 %d개 메시지 생성됨", 
                llmProvider.toUpperCase(), modelName, suggestions.size());
            
            return new CommitSuggestionResponse(suggestions, resultMessage);
        } catch (Exception e) {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - t0); // 실패 시에도 소요 시간 로깅
            log.error("[LLMCommitMessageService] 커밋 메시지 생성 실패 - 소요시간: {} ms ({} s), provider={}, model={}, 원인={}",
                    elapsed.toMillis(), toSeconds(elapsed), llmProvider, modelName, summarize(e));

            String errorMessage = String.format("LLM 호출 실패 (%s): %s", llmProvider, e.getMessage());
            System.err.println("[LLMCommitMessageService] " + errorMessage);
            return new CommitSuggestionResponse(List.of(), errorMessage);
        }
    }
    
    @Tool(description = "생성된 커밋 메시지로 실제 git commit을 수행합니다")
    public String commitWithLLMMessage(CommitExecutionRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return "실패: 커밋 메시지가 필요합니다.";
        }
        
        try {
            File tmp = Files.createTempFile("llm-commit-msg-", ".txt").toFile();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                w.write(request.message());
            }
            
            int exit = git.exec(List.of("commit", "-F", tmp.getAbsolutePath()));
            tmp.delete();
            
            if (exit == 0) {
                return "성공: 커밋이 완료되었습니다.";
            }
            return "실패: git commit 명령이 실패했습니다.";
        } catch (Exception e) {
            return "실패: " + e.getMessage();
        }
    }

    private String buildPrompt(String diff, List<String> files, int maxSuggestions) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("당신은 뛰어난 개발자이자 Git 커밋 메시지 전문가입니다. ")
                .append("아래 git diff 내역을 분석해 Conventional Commits 규칙을 따르는 커밋 메시지 후보를 생성하십시오.\n\n");

        prompt.append("아래 지시를 100% 준수해야만 합니다.\n");
        prompt.append("### 반드시 지켜야 할 규칙\n")
                .append("- 형식: type(scope?): description\n")
                .append("- type: feat, fix, docs, style, refactor, test, chore 중에서만 선택\n")
                .append("- description은 100% 한국어, 명령형/현재 시제, 12~60자, 마침표(.) 금지\n")
                .append("- 한국어가 아닌 출력은 무효로 간주하고 즉시 한국어로만 다시 작성\n")
                .append("- 정확히 ").append(maxSuggestions).append("개만 출력, 번호 목록 외 추가 텍스트 금지\n\n");

        prompt.append("### 출력 예시 (형식만 참고)\n")
                .append("1. feat(core): 설정 자동 로딩 지원 추가\n")
                .append("2. fix(api): 잘못된 상태 코드 매핑 수정\n\n");

        prompt.append("### 변경된 파일\n");
        if (files.isEmpty()) {
            prompt.append("- (파일 정보 없음)\n");
        } else {
            for (String f : files) prompt.append("- ").append(f).append("\n");
        }
        prompt.append("\n");

        prompt.append("### Git Diff\n")
                .append("```\n").append(diff).append("\n```\n\n");

        prompt.append("### 최종 출력 템플릿 (정확히 이 형식으로만 답변)\n");
        for (int i = 1; i <= maxSuggestions; i++) {
            prompt.append(i).append(". [커밋메시지]\n");
        }

        return prompt.toString();
    }


    private List<String> parseCommitMessages(String response) {
        List<String> messages = new ArrayList<>();
        
        String[] lines = response.split("\n");
        for (String line : lines) {
            line = line.trim();
            // 숫자로 시작하는 줄에서 커밋 메시지 추출
            if (line.matches("^\\d+\\.\\s+.*")) {
                String message = line.replaceFirst("^\\d+\\.\\s+", "");
                if (!message.isBlank()) {
                    messages.add(message);
                }
            }
        }
        
        // 파싱에 실패한 경우 전체 응답을 하나의 메시지로 사용
        if (messages.isEmpty() && !response.isBlank()) {
            messages.add(response.trim());
        }
        
        return messages;
    }
    
    private String collectDiff(boolean stagedFirst) {
        try {
            if (stagedFirst) {
                String staged = git.execCapture(List.of("diff", "--cached"));
                if (staged != null && !staged.isBlank()) return staged;
                return git.execCapture(List.of("diff"));
            } else {
                String all = git.execCapture(List.of("diff"));
                if (all != null && !all.isBlank()) return all;
                return git.execCapture(List.of("diff", "--cached"));
            }
        } catch (Exception e) {
            return "";
        }
    }
    
    private List<String> collectChangedFiles(boolean stagedFirst) {
        try {
            if (stagedFirst) {
                List<String> staged = toLines(git.execCapture(List.of("diff", "--name-only", "--cached")));
                if (!staged.isEmpty()) return staged;
                return toLines(git.execCapture(List.of("diff", "--name-only")));
            } else {
                List<String> all = toLines(git.execCapture(List.of("diff", "--name-only")));
                if (!all.isEmpty()) return all;
                return toLines(git.execCapture(List.of("diff", "--name-only", "--cached")));
            }
        } catch (Exception e) {
            return List.of();
        }
    }
    
    private List<String> toLines(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split("\\R"))
            .map(String::trim)
            .filter(x -> !x.isBlank())
            .toList();
    }
    
    // Git 실행 인터페이스
    interface GitExecutor {
        String execCapture(List<String> args) throws IOException, InterruptedException;
        int exec(List<String> args) throws IOException, InterruptedException;
    }
    
    // AutoGitExecutor 어댑터
    static class AutoGitAdapter implements GitExecutor {
        private final AutoGitExecutor delegate = new AutoGitExecutor();
        
        @Override
        public String execCapture(List<String> args) throws IOException, InterruptedException {
            return delegate.execCapture(args);
        }
        
        @Override
        public int exec(List<String> args) throws IOException, InterruptedException {
            return delegate.exec(args);
        }
    }
    
    // DTO 클래스들
    public record CommitSuggestionRequest(Integer maxSuggestions, Boolean stagedFirst) {}
    
    public record CommitSuggestionResponse(List<String> suggestions, String message) {}
    
    public record CommitExecutionRequest(String message) {}


    private interface ThrowingSupplier<T> { T get() throws Exception; }

    private <T> T callWithRetry(ThrowingSupplier<T> supplier, int maxAttempts, Duration backoff) throws Exception {
        int attempt = 0;
        Exception last = null;
        while (attempt < maxAttempts) {
            try {
                return supplier.get();
            } catch (Exception e) {
                last = e;
                Throwable root = rootCause(e);
                // 연결 거부/타임아웃 류만 재시도, 그 외는 바로 전파
                if (!(root instanceof ConnectException || root instanceof SocketTimeoutException)) {
                    throw e;
                }
                attempt++;
                if (attempt >= maxAttempts) break;
                try { Thread.sleep(backoff.toMillis() * (1L << (attempt - 1))); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }

    private String diagnose(Exception e) {
        Throwable root = rootCause(e);
        if (root instanceof ConnectException) {
            return "원인 추정: 서버에 연결할 수 없습니다(연결 거부). " +
                    "서버 기동 및 포트(11434) 확인, 동일 머신에서 /api/tags 호출 확인을 권장합니다.";
        } else if (root instanceof SocketTimeoutException) {
            return "원인 추정: 서버 응답 지연(타임아웃). " +
                    "모델 콜드 로딩/서버 부하 가능성이 있으니 타임아웃 상향 또는 재시도를 적용하세요.";
        } else if (root instanceof UnknownHostException) {
            return "원인 추정: 호스트 해석 실패. base-url 호스트명이 올바른지 확인하세요.";
        }
        String msg = root != null ? String.valueOf(root.getMessage()) : "";
        if (msg.contains("not found")) {
            return "원인 추정: 요청한 모델이 서버에 없습니다. 서버에 모델을 준비하거나 존재하는 모델명으로 변경하세요.";
        }
        return "추가 확인: 서버/애플리케이션 로그를 확인해 상세 원인을 파악하세요.";
    }

    private String summarize(Exception e) {
        Throwable root = rootCause(e);
        return (root != null ? root.getClass().getSimpleName() + ": " + root.getMessage() : e.toString());
    }

    private Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private static String toSeconds(Duration d) {
        // 소수점 3자리까지 초 단위 문자열로 반환
        double sec = d.toNanos() / 1_000_000_000.0;
        return String.format("%.3f", sec);
    }

    private boolean looksEnglishDominant(String text) {
        long lines = Arrays.stream(text.split("\\R")).filter(s -> !s.isBlank()).count();
        long enOnly = Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .filter(s -> !s.matches(".*[가-힣].*")) // 한글 미포함
                .count();
        return lines > 0 && ((double) enOnly / lines) > 0.6; // 60% 이상 영어
    }

}