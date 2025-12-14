package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.PromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromptVersionRepository extends JpaRepository<PromptVersion, Long> {
    Optional<PromptVersion> findByTemplateIdAndVersion(Long templateId, Integer version);
}
