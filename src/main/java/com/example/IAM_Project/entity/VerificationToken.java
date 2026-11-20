package com.example.IAM_Project.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "verification_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_code", nullable = false, length = 255)
    private String tokenCode;

    @Column(name = "token_type", nullable = false, length = 30)
    private String tokenType;

    @Column(name = "delivery_channel", nullable = false, length = 20)
    @Builder.Default
    private String deliveryChannel = "EMAIL";

    @Column(name = "destination", nullable = false, length = 255)
    private String destination;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 5;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (attempts == null) {
            attempts = 0;
        }
        if (maxAttempts == null) {
            maxAttempts = 5;
        }
        if (isUsed == null) {
            isUsed = false;
        }
        if (deliveryChannel == null) {
            deliveryChannel = "EMAIL";
        }
    }
}
