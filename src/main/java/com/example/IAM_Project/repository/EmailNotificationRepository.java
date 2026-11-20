package com.example.IAM_Project.repository;

import com.example.IAM_Project.entity.EmailNotification;
import com.example.IAM_Project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailNotificationRepository extends JpaRepository<EmailNotification, Long> {
    List<EmailNotification> findByUserOrderByCreatedAtDesc(User user);
}
