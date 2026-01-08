package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    long countByRegistrationIpAndCreatedAtAfter(String registrationIp, LocalDateTime timestamp);
}
