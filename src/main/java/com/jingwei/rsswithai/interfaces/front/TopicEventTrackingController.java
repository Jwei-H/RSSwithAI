package com.jingwei.rsswithai.interfaces.front;

import com.jingwei.rsswithai.application.dto.TopicEventTrackingDTO;
import com.jingwei.rsswithai.application.service.TopicEventTrackingService;
import com.jingwei.rsswithai.interfaces.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/front/v1")
@RequiredArgsConstructor
public class TopicEventTrackingController {

    private final TopicEventTrackingService topicEventTrackingService;

    @GetMapping("/subscriptions/{subscriptionId}/event-tracking")
    public ResponseEntity<TopicEventTrackingDTO> getLatest(@PathVariable Long subscriptionId) {
        Long userId = UserContext.currentUserId();
        return ResponseEntity.ok(topicEventTrackingService.getLatest(userId, subscriptionId));
    }

    @PostMapping(value = "/subscriptions/{subscriptionId}/event-tracking/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@PathVariable Long subscriptionId) {
        Long userId = UserContext.currentUserId();
        return topicEventTrackingService.generate(userId, subscriptionId);
    }

    @GetMapping("/topics/{topicId}/event-tracking")
    public ResponseEntity<TopicEventTrackingDTO> getLatestByTopic(@PathVariable Long topicId) {
        return ResponseEntity.ok(topicEventTrackingService.getLatestByTopicId(topicId));
    }

    @PostMapping(value = "/topics/{topicId}/event-tracking/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateByTopic(@PathVariable Long topicId) {
        UserContext.currentUserId();
        return topicEventTrackingService.generateByTopicId(topicId);
    }
}
