package com.jingwei.rsswithai.interfaces.admin;

import com.jingwei.rsswithai.application.dto.AnalysisResultDTO;
import com.jingwei.rsswithai.application.dto.AnalysisResultDetailDTO;
import com.jingwei.rsswithai.application.service.ExperimentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/analysis-results")
@RequiredArgsConstructor
public class AnalysisResultController {

    private final ExperimentService experimentService;

    @GetMapping("/articles/{articleId}")
    public ResponseEntity<Page<AnalysisResultDTO>> getAnalysisResultsByArticle(@PathVariable Long articleId,
                                                                               @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(experimentService.getAnalysisResultsByArticle(articleId, pageable));
    }

    @GetMapping("/experiments/{experimentId}")
    public ResponseEntity<Page<AnalysisResultDTO>> getAnalysisResultsByExperiment(@PathVariable Long experimentId,
                                                                                  @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(experimentService.getAnalysisResultsByExperiment(experimentId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnalysisResultDetailDTO> getAnalysisResult(@PathVariable Long id) {
        return ResponseEntity.ok(experimentService.getAnalysisResultDetail(id));
    }

}