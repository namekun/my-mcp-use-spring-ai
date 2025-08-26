package org.springframework.ai.mcp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.service.LLMCommitMessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class controller {
    private final LLMCommitMessageService llmCommitMessageService;

    @GetMapping("/statusCheck")
    public ResponseEntity<String> statusCheck() {
        String checked = llmCommitMessageService.checkLLMConnection();
        return ResponseEntity.ok(checked);
    }

    @GetMapping("/commit")
    public ResponseEntity<LLMCommitMessageService.CommitSuggestionResponse> commit() {
        LLMCommitMessageService.CommitSuggestionRequest commitSuggestionRequest
                = new LLMCommitMessageService.CommitSuggestionRequest(9, false);
        return ResponseEntity.ok(llmCommitMessageService.generateCommitMessage(commitSuggestionRequest));
    }
}
