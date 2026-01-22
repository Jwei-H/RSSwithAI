package com.jingwei.rsswithai.interfaces.front;

import com.jingwei.rsswithai.application.dto.*;
import com.jingwei.rsswithai.application.service.SubscriptionService;
import com.jingwei.rsswithai.domain.model.SourceCategory;
import com.jingwei.rsswithai.interfaces.context.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/front/v1")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/rss-sources")
    public ResponseEntity<Page<UserRssSourceDTO>> getRssSources(@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
                                                               @RequestParam(value = "category", required = false) SourceCategory category) {
        Long userId = UserContext.currentUserId();
        return ResponseEntity.ok(subscriptionService.listRssSources(userId, category, pageable));
    }

    @PostMapping("/topics")
    public ResponseEntity<TopicDTO> createTopic(@Valid @RequestBody CreateTopicRequest request) {
        TopicDTO topic = subscriptionService.createTopic(request);
        return ResponseEntity.ok(topic);
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<SubscriptionDTO> createSubscription(@Valid @RequestBody CreateSubscriptionRequest request) {
        Long userId = UserContext.currentUserId();
        SubscriptionDTO subscription = subscriptionService.createSubscription(userId, request);
        return ResponseEntity.ok(subscription);
    }

    @DeleteMapping("/subscriptions/{id}")
    public ResponseEntity<Void> deleteSubscription(@PathVariable("id") Long id) {
        Long userId = UserContext.currentUserId();
        subscriptionService.deleteSubscription(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<SubscriptionDTO>> listSubscriptions() {
        Long userId = UserContext.currentUserId();
        return ResponseEntity.ok(subscriptionService.listSubscriptions(userId));
    }

    @GetMapping("/articles/feed")
    public ResponseEntity<List<ArticleFeedDTO>> getFeed(@RequestParam(value = "subscriptionId", required = false) Long subscriptionId,
                                                        @RequestParam(value = "cursor", required = false) String cursor,
                                                        @RequestParam(value = "size", required = false) Integer size) {
        Long userId = UserContext.currentUserId();
        return ResponseEntity.ok(subscriptionService.getFeed(userId, subscriptionId, cursor, size));
    }
}