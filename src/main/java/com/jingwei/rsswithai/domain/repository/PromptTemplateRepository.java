package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {
}
