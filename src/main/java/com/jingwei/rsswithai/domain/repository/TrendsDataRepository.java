package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.TrendsData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrendsDataRepository extends JpaRepository<TrendsData, Long> {
    Optional<TrendsData> findBySourceIdAndType(Long sourceId, String type);

    List<TrendsData> findBySourceIdInAndType(List<Long> sourceIds, String type);
}