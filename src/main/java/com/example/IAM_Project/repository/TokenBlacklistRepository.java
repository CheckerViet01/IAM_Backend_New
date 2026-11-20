package com.example.IAM_Project.repository;

import com.example.IAM_Project.entity.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, Long> {
    Optional<TokenBlacklist> findByJwtId(String jwtId);
    boolean existsByJwtId(String jwtId);
}
