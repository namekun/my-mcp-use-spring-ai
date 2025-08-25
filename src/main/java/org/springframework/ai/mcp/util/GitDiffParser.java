package org.springframework.ai.mcp.util;

import org.springframework.ai.mcp.dto.commitMessageSuggester.CodeChange;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class GitDiffParser {
    /**
     * Git diff를 파싱해서 실제 코드 변경사항들을 추출
     */
    public List<CodeChange> parseChanges(String diff) {
        List<CodeChange> changes = new ArrayList<>();

        String[] lines = diff.split("\\n");
        String currentFile = null;
        List<String> contextLines = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // 파일 헤더 파싱
            if (line.startsWith("diff --git")) {
                currentFile = extractFilePath(line);
                contextLines.clear();
                continue;
            }

            // 실제 변경 라인 분석
            if (line.startsWith("+") && !line.startsWith("+++")) {
                String addedLine = line.substring(1).trim();

                // 추가된 라인에서 의미있는 변경사항 추출
                CodeChange change = analyzeAddedLine(addedLine, currentFile, getContext(lines, i));
                if (change != null) {
                    changes.add(change);
                }
            }

            if (line.startsWith("-") && !line.startsWith("---")) {
                String removedLine = line.substring(1).trim();

                // 삭제된 라인 분석
                CodeChange change = analyzeRemovedLine(removedLine, currentFile, getContext(lines, i));
                if (change != null) {
                    changes.add(change);
                }
            }

            // 컨텍스트 유지 (변경되지 않은 라인들)
            if (!line.startsWith("+") && !line.startsWith("-") && !line.startsWith("@")) {
                contextLines.add(line);
                if (contextLines.size() > 10) {
                    contextLines.remove(0); // 최대 10줄만 유지
                }
            }
        }

        return changes;
    }

    /**
     * 추가된 라인을 분석해서 의미있는 변경사항 추출
     */
    private CodeChange analyzeAddedLine(String line, String file, List<String> context) {
        // 메서드 추가 감지
        if (isMethodSignature(line)) {
            String methodName = extractMethodName(line);
            List<String> annotations = extractAnnotationsFromContext(context);

            return new CodeChange(
                    CodeChange.ChangeType.METHOD_ADDED,
                    methodName,
                    file,
                    createMethodDetails(line, annotations),
                    null,
                    line
            );
        }

        // 어노테이션 추가 감지
        if (line.trim().startsWith("@")) {
            String annotation = extractAnnotation(line);
            String target = findAnnotationTarget(context);

            return new CodeChange(
                    CodeChange.ChangeType.ANNOTATION_ADDED,
                    annotation + " on " + target,
                    file,
                    Arrays.asList("Added " + annotation + " to " + target),
                    null,
                    line
            );
        }

        // 클래스/인터페이스 추가
        if (line.contains("class ") || line.contains("interface ") || line.contains("enum ")) {
            String className = extractClassName(line);

            return new CodeChange(
                    CodeChange.ChangeType.CLASS_ADDED,
                    className,
                    file,
                    Arrays.asList("Added " + (line.contains("class") ? "class" : "interface") + " " + className),
                    null,
                    line
            );
        }

        // Import 추가
        if (line.trim().startsWith("import ")) {
            String importName = extractImportName(line);

            return new CodeChange(
                    CodeChange.ChangeType.IMPORT_ADDED,
                    importName,
                    file,
                    Arrays.asList("Added import: " + importName),
                    null,
                    line
            );
        }

        // Bean 구성 추가
        if (line.contains("@Bean") || (line.contains("return") && line.contains("Provider"))) {
            String beanType = extractBeanType(line, context);

            return new CodeChange(
                    CodeChange.ChangeType.CONFIGURATION_ADDED,
                    beanType + " Bean",
                    file,
                    Arrays.asList("Added Bean configuration for " + beanType),
                    null,
                    line
            );
        }

        return null; // 의미있는 변경사항이 아님
    }

    /**
     * 삭제된 라인을 분석해서 의미있는 변경사항 추출
     */
    private CodeChange analyzeRemovedLine(String line, String file, List<String> context) {
        // 메서드 삭제 감지
        if (isMethodSignature(line)) {
            String methodName = extractMethodName(line);
            List<String> annotations = extractAnnotationsFromContext(context);

            return new CodeChange(
                    CodeChange.ChangeType.METHOD_REMOVED,
                    methodName,
                    file,
                    createMethodDetails(line, annotations),
                    line,
                    null
            );
        }

        // 어노테이션 삭제 감지
        if (line.trim().startsWith("@")) {
            String annotation = extractAnnotation(line);
            String target = findAnnotationTarget(context);

            return new CodeChange(
                    CodeChange.ChangeType.ANNOTATION_REMOVED,
                    annotation + " from " + target,
                    file,
                    Arrays.asList("Removed " + annotation + " from " + target),
                    line,
                    null
            );
        }

        // 클래스/인터페이스 삭제
        if (line.contains("class ") || line.contains("interface ") || line.contains("enum ")) {
            String className = extractClassName(line);

            return new CodeChange(
                    CodeChange.ChangeType.CLASS_REMOVED,
                    className,
                    file,
                    Arrays.asList("Removed " + (line.contains("class") ? "class" : "interface") + " " + className),
                    line,
                    null
            );
        }

        // Import 삭제
        if (line.trim().startsWith("import ")) {
            String importName = extractImportName(line);

            return new CodeChange(
                    CodeChange.ChangeType.IMPORT_REMOVED,
                    importName,
                    file,
                    Arrays.asList("Removed import: " + importName),
                    line,
                    null
            );
        }

        // 필드 삭제 감지 (private/public field 패턴)
        if (isFieldDeclaration(line)) {
            String fieldName = extractFieldName(line);

            return new CodeChange(
                    CodeChange.ChangeType.FIELD_REMOVED,
                    fieldName,
                    file,
                    Arrays.asList("Removed field: " + fieldName),
                    line,
                    null
            );
        }

        return null; // 의미있는 변경사항이 아님
    }

    /**
     * 필드 선언인지 판단
     */
    private boolean isFieldDeclaration(String line) {
        String trimmed = line.trim();
        // 간단한 휴리스틱: 접근제어자 + 타입 + 변수명; 패턴
        return trimmed.matches(".*(private|public|protected)\\s+\\w+\\s+\\w+.*;.*") ||
                trimmed.matches(".*\\w+\\s+\\w+\\s*=.*;.*"); // 초기화 포함
    }

    /**
     * 필드명 추출
     */
    private String extractFieldName(String line) {
        // 정규식으로 필드명 추출
        Pattern pattern = Pattern.compile("\\b(private|public|protected)?\\s*\\w+\\s+(\\w+)\\s*[=;]");
        Matcher matcher = pattern.matcher(line.trim());

        if (matcher.find()) {
            return matcher.group(2);
        }

        // 단순 패턴으로 재시도
        String[] parts = line.trim().split("\\s+");
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i + 1].contains(";") || parts[i + 1].contains("=")) {
                return parts[i + 1].replaceAll("[=;].*", "");
            }
        }

        return "unknown";
    }

    /**
     * 메서드 시그니처인지 판단
     */
    private boolean isMethodSignature(String line) {
        // 간단한 휴리스틱: public/private + 메서드명 + 괄호
        return line.matches(".*\\b(public|private|protected)\\s+.*\\w+\\s*\\([^)]*\\).*") ||
                line.matches(".*\\w+\\s*\\([^)]*\\)\\s*\\{?.*"); // 접근제어자 없는 경우도
    }

    /**
     * 메서드명 추출
     */
    private String extractMethodName(String line) {
        // 정규식으로 메서드명 추출
        Pattern pattern = Pattern.compile("\\b(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(line);

        // 마지막으로 매칭되는 것이 메서드명 (반환타입이 앞에 있을 수 있음)
        String methodName = "unknown";
        while (matcher.find()) {
            methodName = matcher.group(1);
        }

        return methodName;
    }

    /**
     * 어노테이션 추출
     */
    private String extractAnnotation(String line) {
        Pattern pattern = Pattern.compile("@(\\w+)");
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            return "@" + matcher.group(1);
        }

        return line.trim();
    }

    /**
     * 컨텍스트에서 어노테이션들 추출
     */
    private List<String> extractAnnotationsFromContext(List<String> context) {
        return context.stream()
                .filter(line -> line.trim().startsWith("@"))
                .map(this::extractAnnotation)
                .collect(Collectors.toList());
    }

    /**
     * 어노테이션이 적용되는 대상 찾기 (다음 줄의 메서드나 클래스)
     */
    private String findAnnotationTarget(List<String> context) {
        // 어노테이션 다음에 오는 첫 번째 의미있는 코드를 찾음
        for (String line : context) {
            if (isMethodSignature(line)) {
                return "method " + extractMethodName(line);
            }
            if (line.contains("class ")) {
                return "class " + extractClassName(line);
            }
        }
        return "unknown";
    }

    /**
     * 특정 라인 주변의 컨텍스트 가져오기 (앞뒤 n줄)
     */
    private String getLineContext(String[] lines, int currentIndex, int contextSize) {
        StringBuilder context = new StringBuilder();

        int start = Math.max(0, currentIndex - contextSize);
        int end = Math.min(lines.length, currentIndex + contextSize + 1);

        for (int i = start; i < end; i++) {
            if (i != currentIndex) {
                context.append(lines[i]).append("\n");
            }
        }

        return context.toString().trim();
    }

    /**
     * Git diff에서 파일 경로 추출
     * 예: "diff --git a/src/main/java/App.java b/src/main/java/App.java" → "src/main/java/App.java"
     */
    private String extractFilePath(String diffLine) {
        // diff --git a/경로 b/경로 형식에서 파일 경로 추출
        if (diffLine.startsWith("diff --git")) {
            String[] parts = diffLine.split(" ");
            if (parts.length >= 4) {
                String bPath = parts[3]; // b/경로
                return bPath.startsWith("b/") ? bPath.substring(2) : bPath;
            }
        }
        return "unknown";
    }

    /**
     * 특정 라인 주변의 컨텍스트(앞뒤 라인들) 추출
     */
    private List<String> getContext(String[] lines, int currentIndex) {
        List<String> context = new ArrayList<>();

        // 현재 라인 기준으로 앞뒤 5줄씩 가져오기
        int start = Math.max(0, currentIndex - 5);
        int end = Math.min(lines.length, currentIndex + 6);

        for (int i = start; i < end; i++) {
            if (i != currentIndex) { // 현재 라인은 제외
                context.add(lines[i]);
            }
        }

        return context;
    }

    /**
     * 메서드 상세 정보 생성
     */
    private List<String> createMethodDetails(String methodLine, List<String> annotations) {
        List<String> details = new ArrayList<>();

        // 메서드 시그니처 분석
        String methodName = extractMethodName(methodLine);
        details.add("메서드명: " + methodName);

        // 접근 제어자 추출
        if (methodLine.contains("public")) {
            details.add("접근성: public");
        } else if (methodLine.contains("private")) {
            details.add("접근성: private");
        } else if (methodLine.contains("protected")) {
            details.add("접근성: protected");
        }

        // 반환 타입 추출
        String returnType = extractReturnType(methodLine);
        if (!returnType.equals("unknown")) {
            details.add("반환타입: " + returnType);
        }

        // 매개변수 추출
        String parameters = extractParameters(methodLine);
        if (!parameters.isEmpty()) {
            details.add("매개변수: " + parameters);
        }

        // 어노테이션 정보 추가
        if (!annotations.isEmpty()) {
            details.add("어노테이션: " + String.join(", ", annotations));
        }

        return details;
    }

    /**
     * 메서드 시그니처에서 반환 타입 추출
     */
    private String extractReturnType(String methodLine) {
        // 정규식으로 반환 타입 추출
        // 예: "public String getName()" → "String"
        Pattern pattern = Pattern.compile("\\b(public|private|protected)?\\s*(static)?\\s*([\\w<>\\[\\]]+)\\s+\\w+\\s*\\(");
        Matcher matcher = pattern.matcher(methodLine.trim());

        if (matcher.find()) {
            String returnType = matcher.group(3);
            // 생성자인 경우 제외
            if (!returnType.equals("void") && !Character.isUpperCase(returnType.charAt(0))) {
                return "unknown";
            }
            return returnType;
        }

        return "unknown";
    }

    /**
     * 메서드 매개변수 추출
     */
    private String extractParameters(String methodLine) {
        // 괄호 안의 매개변수 추출
        int start = methodLine.indexOf('(');
        int end = methodLine.lastIndexOf(')');

        if (start != -1 && end != -1 && start < end) {
            String params = methodLine.substring(start + 1, end).trim();
            if (params.isEmpty()) {
                return "없음";
            }

            // 매개변수 간단화 (타입만 추출)
            return Arrays.stream(params.split(","))
                    .map(param -> param.trim().split("\\s+")[0]) // 첫 번째 단어(타입)만
                    .collect(Collectors.joining(", "));
        }

        return "";
    }

    // 기타 유틸리티 메서드들...
    private String extractClassName(String line) {
        Pattern pattern = Pattern.compile("\\b(class|interface|enum)\\s+(\\w+)");
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(2) : "unknown";
    }

    private String extractImportName(String line) {
        return line.replace("import", "").replace(";", "").trim();
    }

    private String extractBeanType(String line, List<String> context) {
        // Bean 반환 타입이나 ToolCallbackProvider 등을 추출
        if (line.contains("ToolCallbackProvider")) return "ToolCallbackProvider";
        if (line.contains("Provider")) return "Provider";

        // 반환 타입에서 추출 시도
        String returnType = extractReturnType(line);
        if (!returnType.equals("unknown")) {
            return returnType;
        }

        return "Bean";
    }
}

