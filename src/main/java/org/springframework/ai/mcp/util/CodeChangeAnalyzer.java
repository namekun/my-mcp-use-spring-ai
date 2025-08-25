package org.springframework.ai.mcp.util;

import lombok.Getter;
import org.springframework.ai.mcp.dto.commitMessageSuggester.CodeChange;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * 파싱된 코드 변경사항들을 분석해서 의미를 추출하는 클래스
 */
@Component
public class CodeChangeAnalyzer {


    /**
     * 변경사항 분석 결과
     */
    @Getter
    public static class AnalysisResult {
        // Getters
        private final String primaryIntent;     // 주요 의도
        private final String scope;             // 영향 범위
        private final List<String> keyChanges;  // 핵심 변경사항들
        private final int complexity;           // 복잡도 (1-10)
        private final Map<String, Object> metadata;

        public AnalysisResult(String primaryIntent, String scope, List<String> keyChanges, int complexity, Map<String, Object> metadata) {
            this.primaryIntent = primaryIntent;
            this.scope = scope;
            this.keyChanges = keyChanges != null ? keyChanges : new ArrayList<>();
            this.complexity = complexity;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }

    }

    /**
     * 코드 변경사항들을 분석해서 전체적인 의미 파악
     */
    public AnalysisResult analyzeChanges(List<CodeChange> changes) {
        if (changes.isEmpty()) {
            return new AnalysisResult("no_changes", "none", List.of(), 0, Map.of());
        }

        // 1. 변경 패턴 분석
        ChangePattern pattern = identifyChangePattern(changes);

        // 2. 주요 의도 추론
        String intent = inferIntent(changes, pattern);

        // 3. 스코프 결정
        String scope = determineScope(changes);

        // 4. 핵심 변경사항 추출
        List<String> keyChanges = extractKeyChanges(changes);

        // 5. 복잡도 계산
        int complexity = calculateComplexity(changes);

        // 6. 메타데이터 구성
        Map<String, Object> metadata = buildMetadata(changes, pattern);

        return new AnalysisResult(intent, scope, keyChanges, complexity, metadata);
    }

    /**
     * 변경 패턴 식별 - 어떤 종류의 변경인지 파악
     */
    private ChangePattern identifyChangePattern(List<CodeChange> changes) {
        // 추가된 것들과 삭제된 것들 분리
        long addedMethods = changes.stream().filter(c -> c.getType() == CodeChange.ChangeType.METHOD_ADDED).count();
        long addedAnnotations = changes.stream().filter(c -> c.getType() == CodeChange.ChangeType.ANNOTATION_ADDED).count();
        long addedImports = changes.stream().filter(c -> c.getType() == CodeChange.ChangeType.IMPORT_ADDED).count();
        long addedConfigs = changes.stream().filter(c -> c.getType() == CodeChange.ChangeType.CONFIGURATION_ADDED).count();

        // 패턴 판단
        if (addedConfigs > 0 && addedMethods > 0) {
            return ChangePattern.FEATURE_WITH_CONFIG;
        }
        if (addedAnnotations > 0 && addedMethods > 0) {
            return ChangePattern.ANNOTATION_DRIVEN_FEATURE;
        }
        if (addedMethods > 0) {
            return ChangePattern.NEW_FUNCTIONALITY;
        }
        if (addedConfigs > 0) {
            return ChangePattern.CONFIGURATION_CHANGE;
        }

        return ChangePattern.MISC_CHANGES;
    }

    /**
     * 의도 추론 - 실제로 무엇을 하려고 한 변경인지 파악
     */
    private String inferIntent(List<CodeChange> changes, ChangePattern pattern) {
        // 변경사항의 실제 내용을 보고 의도 추론
        List<String> allTargets = changes.stream()
                .map(CodeChange::getTarget)
                .collect(Collectors.toList());

        String allContent = String.join(" ", allTargets);

        // 실제 코드 내용 기반 의도 추론
        if (allContent.contains("Tool") && allContent.contains("ApplicationContext")) {
            return "tool_auto_discovery"; // 툴 자동 발견 기능
        }

        if (allContent.contains("scan") && allContent.contains("Bean")) {
            return "component_auto_registration"; // 컴포넌트 자동 등록
        }

        if (allContent.contains("Provider") && allContent.contains("Callback")) {
            return "provider_configuration"; // 제공자 구성
        }

        if (pattern == ChangePattern.ANNOTATION_DRIVEN_FEATURE) {
            return "annotation_based_feature"; // 어노테이션 기반 기능
        }

        return "general_improvement";
    }

    /**
     * 스코프 결정 - 실제 파일과 변경 내용을 보고 결정
     */
    private String determineScope(List<CodeChange> changes) {
        Set<String> files = changes.stream()
                .map(CodeChange::getFile)
                .collect(Collectors.toSet());

        // 파일 패턴 분석
        if (files.stream().anyMatch(f -> f.contains("mcp"))) {
            return "mcp";
        }

        // 변경 내용 분석
        boolean hasTool = changes.stream().anyMatch(c -> c.getTarget().contains("Tool"));
        boolean hasApplication = changes.stream().anyMatch(c -> c.getFile().contains("Application"));

        if (hasTool) {
            return "tool";
        }
        if (hasApplication) {
            return "app";
        }

        return "core";
    }

    /**
     * 핵심 변경사항 추출 - 가장 중요한 변경들만 뽑아냄
     */
    private List<String> extractKeyChanges(List<CodeChange> changes) {
        return changes.stream()
                .filter(this::isSignificantChange)  // 중요한 변경만 필터링
                .map(this::summarizeChange)         // 간단히 요약
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * 중요한 변경인지 판단
     */
    private boolean isSignificantChange(CodeChange change) {
        // 메서드 추가, 설정 추가, 어노테이션 추가는 중요
        return change.getType() == CodeChange.ChangeType.METHOD_ADDED ||
                change.getType() == CodeChange.ChangeType.CONFIGURATION_ADDED ||
                (change.getType() == CodeChange.ChangeType.ANNOTATION_ADDED &&
                        !change.getTarget().contains("Override"));  // @Override는 제외
    }

    /**
     * 변경사항을 한 줄로 요약
     */
    private String summarizeChange(CodeChange change) {
        return switch (change.getType()) {
            case METHOD_ADDED -> "메서드 추가: " + change.getTarget();
            case CONFIGURATION_ADDED -> "설정 추가: " + change.getTarget();
            case ANNOTATION_ADDED -> "어노테이션 적용: " + change.getTarget();
            default -> change.getType().name().toLowerCase() + ": " + change.getTarget();
        };
    }

    /**
     * 변경사항의 복잡도 계산 (1-10)
     */
    private int calculateComplexity(List<CodeChange> changes) {
        int complexity = 1;

        // 변경 개수에 따른 복잡도
        complexity += Math.min(changes.size(), 5);

        // 변경 타입에 따른 가중치
        for (CodeChange change : changes) {
            switch (change.getType()) {
                case METHOD_ADDED:
                case CLASS_ADDED:
                    complexity += 2;
                    break;
                case CONFIGURATION_ADDED:
                    complexity += 3;
                    break;
                case ANNOTATION_ADDED:
                    complexity += 1;
                    break;
                default:
                    complexity += 1;
            }
        }

        // 최대 10으로 제한
        return Math.min(complexity, 10);
    }

    /**
     * 메타데이터 구성
     */
    private Map<String, Object> buildMetadata(List<CodeChange> changes, ChangePattern pattern) {
        Map<String, Object> metadata = new HashMap<>();

        // 기본 통계
        metadata.put("totalChanges", changes.size());
        metadata.put("pattern", pattern.name());

        // 변경 타입별 개수
        Map<CodeChange.ChangeType, Long> typeCount = changes.stream()
                .collect(Collectors.groupingBy(CodeChange::getType, Collectors.counting()));
        metadata.put("changesByType", typeCount);

        // 파일별 변경 개수
        Map<String, Long> fileCount = changes.stream()
                .collect(Collectors.groupingBy(CodeChange::getFile, Collectors.counting()));
        metadata.put("changesByFile", fileCount);

        // 주요 키워드 추출
        List<String> keywords = changes.stream()
                .flatMap(c -> extractKeywords(c.getTarget()).stream())
                .distinct()
                .collect(Collectors.toList());
        metadata.put("keywords", keywords);

        return metadata;
    }

    /**
     * 대상에서 키워드 추출
     */
    private List<String> extractKeywords(String target) {
        List<String> keywords = new ArrayList<>();

        // 대문자로 시작하는 단어들 (클래스명, 어노테이션 등)
        Pattern pattern = Pattern.compile("\\b[A-Z]\\w+");
        Matcher matcher = pattern.matcher(target);

        while (matcher.find()) {
            String keyword = matcher.group().toLowerCase();
            keywords.add(keyword);
        }

        // 특정 키워드들 추가
        if (target.toLowerCase().contains("tool")) keywords.add("tool");
        if (target.toLowerCase().contains("callback")) keywords.add("callback");
        if (target.toLowerCase().contains("provider")) keywords.add("provider");
        if (target.toLowerCase().contains("config")) keywords.add("config");
        if (target.toLowerCase().contains("bean")) keywords.add("bean");

        return keywords;
    }

    // 기타 유틸리티...
    private enum ChangePattern {
        FEATURE_WITH_CONFIG,
        ANNOTATION_DRIVEN_FEATURE,
        NEW_FUNCTIONALITY,
        CONFIGURATION_CHANGE,
        MISC_CHANGES
    }
}