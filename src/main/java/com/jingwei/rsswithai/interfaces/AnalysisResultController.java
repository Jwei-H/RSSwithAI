package com.jingwei.rsswithai.interfaces;

import com.jingwei.rsswithai.application.dto.AnalysisResultDetailDTO;
import com.jingwei.rsswithai.application.dto.AnalysisResultWithExperimentDTO;
import com.jingwei.rsswithai.application.service.ExperimentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analysis-results")
@RequiredArgsConstructor
public class AnalysisResultController {

    private final ExperimentService experimentService;

    @GetMapping("/articles/{articleId}")
    public ResponseEntity<List<AnalysisResultWithExperimentDTO>> getAnalysisResultsByArticle(@PathVariable Long articleId) {
        return ResponseEntity.ok(experimentService.getAnalysisResultsByArticle(articleId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnalysisResultDetailDTO> getAnalysisResult(@PathVariable Long id) {
        return ResponseEntity.ok(experimentService.getAnalysisResultDetail(id));
    }
}
