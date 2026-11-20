package com.example.IAM_Project.service;

import com.example.IAM_Project.entity.User;
import com.example.IAM_Project.entity.UserAvatar;
import com.example.IAM_Project.exception.BadRequestException;
import com.example.IAM_Project.repository.UserAvatarRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    private final UserAvatarRepository userAvatarRepository;

    @Value("${app.upload.avatar-dir:uploads/avatars}")
    private String avatarDir;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    @Transactional
    public String storeAvatar(MultipartFile file, User user) {
        if (file.isEmpty()) {
            throw new BadRequestException("Tệp tải lên không được để trống");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("Kích thước tệp vượt quá 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_EXTENSIONS.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Chỉ chấp nhận tệp ảnh có định dạng JPEG, PNG, WEBP hoặc GIF");
        }

        String rawOriginalName = file.getOriginalFilename();
        String originalFileName = rawOriginalName != null ? StringUtils.cleanPath(rawOriginalName) : "avatar.jpg";
        String extension = "";
        int dotIndex = originalFileName.lastIndexOf(".");
        if (dotIndex > 0) {
            extension = originalFileName.substring(dotIndex);
        }

        String generatedFileName = UUID.randomUUID().toString() + extension;
        Path targetDir = Paths.get(avatarDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(generatedFileName);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Đặt tất cả avatar cũ của user thành is_current = false trực tiếp trong database
            userAvatarRepository.resetCurrentAvatars(user);

            String publicUrl = "/api/v1/files/avatars/" + generatedFileName;

            UserAvatar userAvatar = UserAvatar.builder()
                    .user(user)
                    .fileName(generatedFileName)
                    .originalFileName(originalFileName)
                    .storageProvider("LOCAL")
                    .filePathOrUrl(publicUrl)
                    .contentType(contentType)
                    .fileSizeBytes(file.getSize())
                    .isCurrent(true)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            userAvatarRepository.saveAndFlush(userAvatar);

            return publicUrl;
        } catch (IOException e) {
            logger.error("Failed to store file: {}", e.getMessage());
            throw new BadRequestException("Lỗi trong quá trình lưu trữ tệp ảnh: " + e.getMessage());
        }
    }
}
