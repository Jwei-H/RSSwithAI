package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.model.SourceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RSS源仓储接口
 */
@Repository
public interface RssSourceRepository extends JpaRepository<RssSource, Long> {

    /**
     * 根据状态查找RSS源列表
     */
    List<RssSource> findByStatus(SourceStatus status);

    /**
     * 获取所有启用的RSS源
     */
    default List<RssSource> findAllEnabled() {
        return findByStatus(SourceStatus.ENABLED);
    }

    @Query("SELECT s.lastFetchStatus, COUNT(s) FROM RssSource s GROUP BY s.lastFetchStatus")
    List<Object[]> countByLastFetchStatus();

    @Transactional
    @Modifying
    @Query("UPDATE RssSource s SET s.lastFetchStatus = 'FETCHING', s.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE s.id = :id AND s.lastFetchStatus != 'FETCHING'")
    int compareAndSetFetching(@Param("id") Long id);
}