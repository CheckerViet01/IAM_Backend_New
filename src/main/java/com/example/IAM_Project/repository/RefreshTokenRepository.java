package com.example.IAM_Project.repository;

import com.example.IAM_Project.entity.RefreshToken;
import com.example.IAM_Project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    Optional<RefreshToken> findByJwtId(String jwtId);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.isRevoked = true, r.revokedAt = :revokedAt, r.revokedReason = :reason WHERE r.user = :user AND r.isRevoked = false")
    int revokeAllUserTokens(@Param("user") User user, @Param("revokedAt") LocalDateTime revokedAt, @Param("reason") String reason);
}
