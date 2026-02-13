package com.jingwei.rsswithai.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "trends_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendsData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Source ID: 0 means global, non-zero means specific RSS source ID
     */
    @Column(name = "source_id")
    private Long sourceId;

    /**
     * Analysis type: WORD_CLOUD, HOT_EVENTS
     */
    @Column(nullable = false)
    private String type;

    /**
     * Core data, JSONB structure
     * WORD_CLOUD: [{"text": "AI", "value": 50}, ...]
      * HOT_EVENTS: [{"event": "Title", "score": 9}, ...]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB", nullable = false)
    private String data;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}