package org.springframework.ai.mcp.dto.commitMessageSuggester;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 변경사항 분석 결과
 */
@Getter
public class AnalysisResult {
    private final String primaryIntent;     // 주요 의도
    private final String scope;             // 영향 범위
    private final List<String> keyChanges;  // 핵심 변경사항들
    private final int complexity;           // 복잡도 (1-10)
    private final Map<String, Object> metadata;

    // constructors, getters...
    public AnalysisResult(String primaryIntent, String scope, List<String> keyChanges, int complexity, Map<String, Object> metadata) {
        this.primaryIntent = primaryIntent;
        this.scope = scope;
        this.keyChanges = keyChanges;
        this.complexity = complexity;
        this.metadata = metadata;
    }

}
