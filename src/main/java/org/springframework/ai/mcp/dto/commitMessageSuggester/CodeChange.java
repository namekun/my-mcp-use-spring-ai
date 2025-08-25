package org.springframework.ai.mcp.dto.commitMessageSuggester;

import java.util.ArrayList;
import java.util.List;

/**
 * 실제 코드 변경사항을 나타내는 클래스
 */
public class CodeChange {
    public enum ChangeType {
        METHOD_ADDED, METHOD_REMOVED, METHOD_MODIFIED,
        CLASS_ADDED, CLASS_REMOVED, CLASS_MODIFIED,
        ANNOTATION_ADDED, ANNOTATION_REMOVED,
        IMPORT_ADDED, IMPORT_REMOVED,
        FIELD_ADDED, FIELD_REMOVED,
        CONFIGURATION_ADDED, CONFIGURATION_MODIFIED
    }

    private final ChangeType type;
    private final String target;          // 변경된 대상 (메서드명, 클래스명 등)
    private final String file;            // 파일 경로
    private final List<String> details;   // 상세 정보
    private final String beforeCode;      // 변경 전 코드 (있는 경우)
    private final String afterCode;       // 변경 후 코드

    public CodeChange(ChangeType type, String target, String file, List<String> details, String beforeCode, String afterCode) {
        this.type = type;
        this.target = target;
        this.file = file;
        this.details = details != null ? details : new ArrayList<>();
        this.beforeCode = beforeCode;
        this.afterCode = afterCode;
    }

    // Getters
    public ChangeType getType() { return type; }
    public String getTarget() { return target; }
    public String getFile() { return file; }
    public List<String> getDetails() { return details; }
    public String getBeforeCode() { return beforeCode; }
    public String getAfterCode() { return afterCode; }

    @Override
    public String toString() {
        return String.format("%s: %s in %s", type, target, file);
    }
}
