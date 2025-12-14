package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {
}
