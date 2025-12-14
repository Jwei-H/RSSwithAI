package com.jingwei.rsswithai.interfaces;

import com.jingwei.rsswithai.application.dto.CreatePromptTemplateRequest;
import com.jingwei.rsswithai.application.dto.PromptTemplateDTO;
import com.jingwei.rsswithai.application.dto.PromptVersionDTO;
import com.jingwei.rsswithai.application.dto.UpdatePromptVersionRequest;
import com.jingwei.rsswithai.application.service.PromptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/prompts")
@RequiredArgsConstructor
@Slf4j
public class PromptController {

    private final PromptService promptService;

    @PostMapping
    public ResponseEntity<PromptTemplateDTO> createTemplate(@Valid @RequestBody CreatePromptTemplateRequest request) {
        log.info("Creating new prompt template with name: {}", request.getName());
        return ResponseEntity.ok(promptService.createTemplate(request));
    }

    @GetMapping
    public ResponseEntity<List<PromptTemplateDTO>> getAllTemplates() {
        return ResponseEntity.ok(promptService.getAllTemplates());
    }

    @DeleteMapping("/{tempId}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long tempId) {
        log.info("Deleting prompt template with id: {}", tempId);
        promptService.deleteTemplate(tempId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tempId}/versions/{versionNum}")
    public ResponseEntity<PromptVersionDTO> getVersion(@PathVariable Long tempId, @PathVariable Integer versionNum) {
        return ResponseEntity.ok(promptService.getVersion(tempId, versionNum));
    }

    @PutMapping("/{tempId}/versions/{versionNum}")
    public ResponseEntity<PromptVersionDTO> updateVersion(@PathVariable Long tempId, @PathVariable Integer versionNum, @Valid @RequestBody UpdatePromptVersionRequest request) {
        log.info("Updating prompt version: templateId={}, versionNum={}", tempId, versionNum);
        return ResponseEntity.ok(promptService.updateVersionContent(tempId, versionNum, request));
    }

    @PostMapping("/{tempId}/versions/{versionNum}/freeze")
    public ResponseEntity<Void> freezeVersion(@PathVariable Long tempId, @PathVariable Integer versionNum) {
        log.info("Freezing prompt version: templateId={}, versionNum={}", tempId, versionNum);
        promptService.freezeVersion(tempId, versionNum);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{tempId}/versions")
    public ResponseEntity<PromptVersionDTO> createNewVersion(@PathVariable Long tempId) {
        log.info("Creating new prompt version for templateId={}", tempId);
        return ResponseEntity.ok(promptService.createNewVersion(tempId));
    }
}
