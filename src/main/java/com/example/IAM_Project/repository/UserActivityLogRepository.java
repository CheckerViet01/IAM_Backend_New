package com.example.IAM_Project.repository;

import com.example.IAM_Project.entity.User;
import com.example.IAM_Project.entity.UserActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {
    List<UserActivityLog> findByUserOrderByCreatedAtDesc(User user);
    Page<UserActivityLog> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    Page<UserActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
