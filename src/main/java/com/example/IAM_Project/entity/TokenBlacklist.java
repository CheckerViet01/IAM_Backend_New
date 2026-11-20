package com.example.IAM_Project.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "token_blacklist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jwt_id", nullable = false, length = 100)
    private String jwtId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "token_hash", length = 255)
    private String tokenHash;

    @Column(name = "token_type", nullable = false, length = 20)
    @Builder.Default
    private String tokenType = "ACCESS";

    @Column(name = "reason", nullable = false, length = 50)
    private String reason;

    @Column(name = "revoked_at", nullable = false)
    private LocalDateTime revokedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    public void prePersist() {
        if (revokedAt == null) {
            revokedAt = LocalDateTime.now();
        }
        if (tokenType == null) {
            tokenType = "ACCESS";
        }
    }
}
