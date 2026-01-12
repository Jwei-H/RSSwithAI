package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.Subscription;
import com.jingwei.rsswithai.domain.model.SubscriptionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByUserId(Long userId);

    @Query("select s from Subscription s left join fetch s.source left join fetch s.topic where s.userId = :userId")
    List<Subscription> findByUserIdWithDetails(@Param("userId") Long userId);

    List<Subscription> findByUserIdAndType(Long userId, SubscriptionType type);

    @Query("select s from Subscription s left join fetch s.source where s.userId = :userId and s.type = :type")
    List<Subscription> findByUserIdAndTypeWithSource(@Param("userId") Long userId, @Param("type") SubscriptionType type);

    Optional<Subscription> findByIdAndUserId(Long id, Long userId);

    Optional<Subscription> findByUserIdAndTypeAndSource_Id(Long userId, SubscriptionType type, Long sourceId);

    Optional<Subscription> findByUserIdAndTypeAndTopic_Id(Long userId, SubscriptionType type, Long topicId);

    long countByUserId(Long userId);
}
