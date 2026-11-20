package com.example.IAM_Project.repository;

import com.example.IAM_Project.entity.User;
import com.example.IAM_Project.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findFirstByUserAndTokenTypeAndIsUsedFalseOrderByCreatedAtDesc(User user, String tokenType);
    Optional<VerificationToken> findFirstByDestinationAndTokenTypeAndIsUsedFalseOrderByCreatedAtDesc(String destination, String tokenType);
    List<VerificationToken> findByUserAndTokenTypeAndIsUsedFalse(User user, String tokenType);
}
