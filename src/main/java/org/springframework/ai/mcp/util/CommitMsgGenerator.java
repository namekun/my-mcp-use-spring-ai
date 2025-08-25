package org.springframework.ai.mcp.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CommitMsgGenerator {

    /**
     * 분석 결과를 바탕으로 간단한 한 줄 커밋 메시지 생성
     */
    public List<String> generateMessages(CodeChangeAnalyzer.AnalysisResult analysis) {

        // 간단한 한 줄 메시지만 생성
        List<String> messages = new ArrayList<>(generateSimpleMessages(analysis));

        return messages.stream()
                .distinct()
                .limit(3)
                .collect(Collectors.toList());
    }

    /**
     * 간단한 한 줄 커밋 메시지 생성
     */
    private List<String> generateSimpleMessages(CodeChangeAnalyzer.AnalysisResult analysis) {
        List<String> messages = new ArrayList<>();
        String type = guessSimpleType(analysis);
        String scope = analysis.getScope();
        
        // 핵심 변경사항 기반으로 간단한 메시지 생성
        List<String> keyChanges = analysis.getKeyChanges();
        
        if (!keyChanges.isEmpty()) {
            String primaryChange = keyChanges.get(0);
            String simpleDesc = simplifyChange(primaryChange);
            messages.add(String.format("%s(%s): %s", type, scope, simpleDesc));
        }
        
        // 의도 기반 간단 메시지
        String intent = analysis.getPrimaryIntent();
        if (intent != null && !intent.isEmpty()) {
            String intentDesc = simplifyIntent(intent);
            messages.add(String.format("%s(%s): %s", type, scope, intentDesc));
        }
        
        // 기본 메시지 (변경사항이 없는 경우)
        if (messages.isEmpty()) {
            messages.add(String.format("%s(%s): 코드 개선", type, scope));
        }
        
        return messages;
    }
    
    /**
     * 타입을 간단하게 결정
     */
    private String guessSimpleType(CodeChangeAnalyzer.AnalysisResult analysis) {
        if (analysis.getComplexity() > 6) return "feat";
        if (analysis.getPrimaryIntent().contains("fix")) return "fix";
        return "feat";
    }
    
    /**
     * 변경사항을 간단하게 표현
     */
    private String simplifyChange(String change) {
        if (change.contains("메서드 추가")) {
            return "메서드 추가";
        }
        if (change.contains("클래스 추가")) {
            return "클래스 추가";
        }
        if (change.contains("설정 추가")) {
            return "설정 추가";
        }
        if (change.contains("수정")) {
            return "코드 수정";
        }
        return "코드 개선";
    }
    
    /**
     * 의도를 간단하게 표현
     */
    private String simplifyIntent(String intent) {
        return switch (intent) {
            case "tool_auto_discovery" -> "도구 자동 검색 기능";
            case "component_auto_registration" -> "컴포넌트 자동 등록";
            case "provider_configuration" -> "프로바이더 설정";
            case "annotation_based_feature" -> "어노테이션 기반 기능";
            default -> "기능 개선";
        };
    }
}
