package com.jingwei.rsswithai.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "model_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "model_id", nullable = false)
    private String modelId;

    private Double temperature;

    @Column(name = "top_p")
    private Double topP;

    @Column(name = "top_k")
    private Integer topK;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    private Long seed;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
