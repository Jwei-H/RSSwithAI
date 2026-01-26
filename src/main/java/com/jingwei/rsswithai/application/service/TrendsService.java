package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.HotEventDTO;
import com.jingwei.rsswithai.application.dto.WordCloudItemDTO;
import com.jingwei.rsswithai.domain.model.Subscription;
import com.jingwei.rsswithai.domain.model.Topic;
import com.jingwei.rsswithai.domain.repository.SubscriptionRepository;
import com.jingwei.rsswithai.domain.repository.TopicRepository;
import com.jingwei.rsswithai.domain.model.TrendsData;
import com.jingwei.rsswithai.domain.repository.TrendsDataRepository;
import com.jingwei.rsswithai.domain.model.SubscriptionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrendsService {

    private final TrendsDataRepository trendsDataRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TopicRepository topicRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<WordCloudItemDTO> getWordCloud(Long sourceId, Long userId) {
        if (sourceId != null) {
            // Direct query
            return trendsDataRepository.findBySourceIdAndType(sourceId, "WORD_CLOUD")
                    .map(this::parseWordCloudList)
                    .orElse(Collections.emptyList());
        } else if (userId != null) {
            // Aggregate user subscriptions
            List<Subscription> subs = subscriptionRepository.findByUserId(userId);
            List<Long> sourceIds = subs.stream()
                    .filter(s -> s.getSource() != null)
                    .map(s -> s.getSource().getId())
                    .toList();

            if (sourceIds.isEmpty())
                return Collections.emptyList();

            List<TrendsData> allTrends = trendsDataRepository.findBySourceIdInAndType(sourceIds, "WORD_CLOUD");
            return aggregateWordClouds(allTrends);
        }
        return Collections.emptyList();
    }

    @Transactional(readOnly = true)
    public List<HotEventDTO> getHotEvents(Long userId) {
        return trendsDataRepository.findBySourceIdAndType(0L, "HOT_EVENTS")
                .map(data -> parseHotEventList(data, userId))
                .orElse(Collections.emptyList());
    }

    private List<WordCloudItemDTO> parseWordCloudList(TrendsData trendsData) {
        try {
            List<Map<String, Object>> rawList = objectMapper.readValue(trendsData.getData(), new TypeReference<>() {
            });
            return rawList.stream()
                    .map(m -> new WordCloudItemDTO(
                            (String) m.get("text"),
                            ((Number) m.get("value")).intValue()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to parse word cloud data for id {}", trendsData.getId(), e);
            return Collections.emptyList();
        }
    }

    private List<HotEventDTO> parseHotEventList(TrendsData trendsData, Long userId) {
        try {
            List<Map<String, Object>> rawList = objectMapper.readValue(trendsData.getData(), new TypeReference<>() {
            });
            return rawList.stream()
                    .map(m -> new HotEventDTO(
                            (String) m.get("event"),
                            ((Number) m.get("score")).intValue(),
                            isSubscribed((String) m.get("event"), userId)))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to parse hot events data for id {}", trendsData.getId(), e);
            return Collections.emptyList();
        }
    }

    private boolean isSubscribed(String event, Long userId) {
        if (userId == null || event == null || event.isBlank()) {
            return false;
        }
        Optional<Topic> topic = topicRepository.findByContent(event);
        if (topic.isEmpty()) {
            return false;
        }
        return subscriptionRepository
                .findByUserIdAndTypeAndTopic_Id(userId, SubscriptionType.TOPIC, topic.get().getId())
                .isPresent();
    }

    private List<WordCloudItemDTO> aggregateWordClouds(List<TrendsData> trendsDataList) {
        Map<String, Integer> merged = new HashMap<>();

        for (TrendsData td : trendsDataList) {
            try {
                List<Map<String, Object>> list = objectMapper.readValue(td.getData(), new TypeReference<>() {
                });
                for (Map<String, Object> item : list) {
                    String text = (String) item.get("text");
                    Number value = (Number) item.get("value");
                    if (text != null && value != null) {
                        merged.merge(text, value.intValue(), Integer::sum);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse word cloud data during aggregation for id {}", td.getId(), e);
            }
        }

        return merged.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(50)
                .map(e -> new WordCloudItemDTO(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }
}