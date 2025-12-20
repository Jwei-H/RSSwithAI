package com.jingwei.rsswithai.interfaces;

import com.jingwei.rsswithai.application.dto.CreateModelConfigRequest;
import com.jingwei.rsswithai.application.dto.ModelConfigDTO;
import com.jingwei.rsswithai.application.dto.UpdateModelConfigRequest;
import com.jingwei.rsswithai.application.service.ModelConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/model-configs")
@RequiredArgsConstructor
@Slf4j
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @GetMapping
    public ResponseEntity<Page<ModelConfigDTO>> getAllConfigs(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(modelConfigService.getAllConfigs(pageable));
    }

    @PostMapping
    public ResponseEntity<ModelConfigDTO> createConfig(@Valid @RequestBody CreateModelConfigRequest request) {
        log.info("Creating new model config with name: {}", request.getName());
        return ResponseEntity.ok(modelConfigService.createConfig(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModelConfigDTO> getConfig(@PathVariable Long id) {
        return ResponseEntity.ok(modelConfigService.getConfig(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ModelConfigDTO> updateConfig(@PathVariable Long id, @RequestBody UpdateModelConfigRequest request) {
        log.info("Updating model config with id: {}", id);
        return ResponseEntity.ok(modelConfigService.updateConfig(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        log.info("Deleting model config with id: {}", id);
        modelConfigService.deleteConfig(id);
        return ResponseEntity.noContent().build();
    }
}