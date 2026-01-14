package com.jingwei.rsswithai.interfaces.front;

import com.jingwei.rsswithai.application.dto.HotEventDTO;
import com.jingwei.rsswithai.application.dto.WordCloudItemDTO;
import com.jingwei.rsswithai.application.service.TrendsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/front/v1/trends")
@RequiredArgsConstructor
public class FrontTrendsController {

    private final TrendsService trendsService;

    @GetMapping("/wordcloud")
    public ResponseEntity<List<WordCloudItemDTO>> getWordCloud(@RequestParam(required = false) Long sourceId) {
        long userId = UserContext.requireUserId();
        List<WordCloudItemDTO> result = trendsService.getWordCloud(sourceId, userId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/hotevents")
    public ResponseEntity<List<HotEventDTO>> getHotEvents() {
        List<HotEventDTO> result = trendsService.getHotEvents();
        return ResponseEntity.ok(result);
    }
}