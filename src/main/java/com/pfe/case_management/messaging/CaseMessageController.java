package com.pfe.case_management.messaging;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CaseMessageController {

    private final CaseMessageService caseMessageService;

    @GetMapping("/cases/{caseId}/messages")
    public ResponseEntity<List<CaseMessageResponse>> getMessages(@PathVariable String caseId) {
        return ResponseEntity.ok(caseMessageService.getThread(caseId));
    }

    @PostMapping(value = "/cases/{caseId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CaseMessageResponse> sendMessage(
            @PathVariable String caseId,
            @Valid @RequestBody SendCaseMessageRequest request
    ) {
        return ResponseEntity.ok(caseMessageService.sendMessage(caseId, request.content(), null));
    }

    /** Multipart variant used when the message carries an optional photo attachment. */
    @PostMapping(value = "/cases/{caseId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CaseMessageResponse> sendMessageWithAttachment(
            @PathVariable String caseId,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        return ResponseEntity.ok(caseMessageService.sendMessage(caseId, content, file));
    }

    @PutMapping("/cases/{caseId}/messages/read")
    public ResponseEntity<MarkMessagesReadResponse> markThreadRead(@PathVariable String caseId) {
        return ResponseEntity.ok(caseMessageService.markThreadRead(caseId));
    }

    /** Unread badge feed for the current user (ZM/STT sidebar/topbar counter). */
    @GetMapping("/case-messages/unread-summary")
    public ResponseEntity<CaseMessageUnreadSummaryResponse> getUnreadSummary() {
        return ResponseEntity.ok(caseMessageService.getUnreadSummary());
    }
}
