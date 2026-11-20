package com.example.IAM_Project.repository;

import com.example.IAM_Project.entity.User;
import com.example.IAM_Project.entity.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {
    List<UserAvatar> findByUserOrderByUploadedAtDesc(User user);
    Optional<UserAvatar> findByUserAndIsCurrentTrue(User user);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserAvatar a SET a.isCurrent = false WHERE a.user = :user AND a.isCurrent = true")
    void resetCurrentAvatars(@Param("user") User user);
}

