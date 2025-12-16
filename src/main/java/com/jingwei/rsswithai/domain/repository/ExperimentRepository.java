package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.Experiment;
import com.jingwei.rsswithai.domain.model.ExperimentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExperimentRepository extends JpaRepository<Experiment, Long> {
    Page<Experiment> findByStatus(ExperimentStatus status, Pageable pageable);
}
