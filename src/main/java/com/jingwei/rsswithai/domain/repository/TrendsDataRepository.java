package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.TrendsData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrendsDataRepository extends JpaRepository<TrendsData, Long> {
    Optional<TrendsData> findFirstBySourceIdAndTypeOrderByCreatedAtDescIdDesc(Long sourceId, String type);

    @Query(value = "SELECT DISTINCT ON (source_id) * FROM trends_data " +
            "WHERE source_id IN (:sourceIds) AND type = :type " +
            "ORDER BY source_id, created_at DESC, id DESC", nativeQuery = true)
    List<TrendsData> findLatestBySourceIdsAndType(@Param("sourceIds") List<Long> sourceIds,
            @Param("type") String type);
}