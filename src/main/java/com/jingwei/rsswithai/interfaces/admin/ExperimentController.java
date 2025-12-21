package com.jingwei.rsswithai.interfaces.admin;

import com.jingwei.rsswithai.application.dto.CreateExperimentRequest;
import com.jingwei.rsswithai.application.dto.ExperimentDTO;
import com.jingwei.rsswithai.application.dto.ExperimentDetailDTO;
import com.jingwei.rsswithai.application.service.ExperimentService;
import com.jingwei.rsswithai.domain.model.ExperimentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/experiments")
@RequiredArgsConstructor
@Slf4j
public class ExperimentController {

    private final ExperimentService experimentService;

    @PostMapping
    public ResponseEntity<ExperimentDTO> createExperiment(@RequestBody CreateExperimentRequest request) {
        log.info("Received request to create experiment: {}", request);
        return ResponseEntity.ok(experimentService.createExperiment(request));
    }

    @GetMapping
    public ResponseEntity<Page<ExperimentDTO>> getExperiments(
            @RequestParam(required = false) ExperimentStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(experimentService.getExperiments(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExperimentDetailDTO> getExperiment(@PathVariable Long id) {
        return ResponseEntity.ok(experimentService.getExperimentDetail(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExperiment(@PathVariable Long id) {
        experimentService.deleteExperiment(id);
        return ResponseEntity.noContent().build();
    }
}