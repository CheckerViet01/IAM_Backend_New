package com.example.IAM_Project.service;

import com.example.IAM_Project.entity.EmailNotification;
import com.example.IAM_Project.entity.User;
import com.example.IAM_Project.repository.EmailNotificationRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MailService {

    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final EmailNotificationRepository emailNotificationRepository;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private String getDreamyTemplate(String title, String subtitle, String contentHtml) {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset='UTF-8'>" +
                "<style>" +
                "body { margin: 0; padding: 0; background-color: #f7f5fb; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; }" +
                ".wrapper { max-width: 580px; margin: 30px auto; background: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 10px 30px rgba(142, 90, 247, 0.1); border: 1px solid rgba(220, 205, 255, 0.4); }" +
                ".header { background: linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%); padding: 40px 30px; text-align: center; color: #ffffff; }" +
                ".header h1 { margin: 0; font-size: 26px; font-weight: 700; letter-spacing: 0.5px; text-shadow: 0 2px 4px rgba(0,0,0,0.1); }" +
                ".header p { margin: 8px 0 0; font-size: 14px; opacity: 0.95; }" +
                ".content { padding: 35px 30px; color: #4a4458; line-height: 1.7; font-size: 15px; }" +
                ".otp-badge { display: inline-block; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #ffffff !important; font-size: 32px; font-weight: 800; letter-spacing: 8px; padding: 14px 28px; border-radius: 14px; margin: 20px 0; box-shadow: 0 6px 18px rgba(118, 75, 162, 0.25); text-align: center; }" +
                ".footer { background: #faf7ff; padding: 20px 30px; text-align: center; font-size: 12px; color: #9c95a8; border-top: 1px solid #f0eafc; }" +
                ".card { background: #faf8ff; border: 1px dashed #d1c4e9; border-radius: 12px; padding: 18px; margin: 20px 0; }" +
                "</style></head><body>" +
                "<div class='wrapper'>" +
                "<div class='header'>" +
                "  <h1>" + title + "</h1>" +
                "  <p>" + subtitle + "</p>" +
                "</div>" +
                "<div class='content'>" +
                contentHtml +
                "</div>" +
                "<div class='footer'>" +
                "  <p>Hệ thống Xác thực & Quản lý Danh tính IAM Service • Bảo mật tối đa</p>" +
                "  <p>Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email hoặc đổi mật khẩu ngay lập tức.</p>" +
                "</div>" +
                "</div></body></html>";
    }

    public void sendEmail(User user, String toEmail, String type, String subject, String contentHtml) {
        EmailNotification notification = EmailNotification.builder()
                .user(user)
                .recipientEmail(toEmail)
                .notificationType(type)
                .subject(subject)
                .status("PENDING")
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();
        notification = emailNotificationRepository.save(notification);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "IAM Security Team");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(contentHtml, true);

            mailSender.send(message);

            notification.setStatus("SENT");
            notification.setSentAt(LocalDateTime.now());
            emailNotificationRepository.save(notification);
            logger.info("Email [{}] sent successfully to {}", type, toEmail);
        } catch (Exception e) {
            logger.error("Failed to send email [{}] to {}: {}", type, toEmail, e.getMessage());
            notification.setStatus("FAILED");
            notification.setErrorMessage(e.getMessage() != null && e.getMessage().length() > 1990 ? e.getMessage().substring(0, 1990) : e.getMessage());
            emailNotificationRepository.save(notification);
        }
    }

    public void sendRegistrationConfirmation(User user) {
        String title = "Chào Mừng Đến Với IAM";
        String subtitle = "Tài khoản của bạn đã được khởi tạo thành công ✨";
        String content = "<p>Xin chào <strong>" + (user.getFullName() != null ? user.getFullName() : user.getEmail()) + "</strong>,</p>" +
                "<p>Chúc mừng bạn đã tạo thành công tài khoản trên hệ thống Dịch vụ Quản lý Danh tính & Xác thực (IAM Service).</p>" +
                "<div class='card'>" +
                "  <p style='margin:0;'><strong>Email đăng ký:</strong> " + user.getEmail() + "</p>" +
                "  <p style='margin:5px 0 0;'><strong>Thời gian khởi tạo:</strong> " + LocalDateTime.now() + "</p>" +
                "</div>" +
                "<p>Bây giờ bạn có thể đăng nhập, tùy chỉnh hồ sơ cá nhân và kích hoạt các tính năng bảo mật nâng cao như 2FA.</p>";
        String html = getDreamyTemplate(title, subtitle, content);
        sendEmail(user, user.getEmail(), "REGISTER_CONFIRM", "Chào mừng bạn đến với IAM Service ✨", html);
    }

    public void sendOtpEmail(User user, String toEmail, String otpCode, String purpose, String notifType) {
        String title = "Mã Xác Thực OTP";
        String subtitle = "Bảo mật tài khoản của bạn với mã xác nhận một lần";
        String content = "<p>Xin chào,</p>" +
                "<p>Bạn vừa có yêu cầu thực hiện hành động: <strong>" + purpose + "</strong>.</p>" +
                "<p>Vui lòng sử dụng mã xác thực OTP bên dưới để hoàn tất xác minh:</p>" +
                "<div style='text-align: center;'><span class='otp-badge'>" + otpCode + "</span></div>" +
                "<div class='card'>" +
                "  <p style='margin: 0; color: #e53935; font-size: 13px;'>⏳ <strong>Lưu ý quan trọng:</strong> Mã xác thực có hiệu lực trong vòng <strong>10 phút</strong> và chỉ sử dụng được 1 lần duy nhất. Tuyệt đối không chia sẻ mã này cho bất kỳ ai.</p>" +
                "</div>";
        String html = getDreamyTemplate(title, subtitle, content);
        sendEmail(user, toEmail, notifType, "Mã xác thực OTP (" + otpCode + ") - IAM Service", html);
    }

    public void sendPasswordChangedAlert(User user) {
        String title = "Mật Khẩu Đã Thay Đổi";
        String subtitle = "Cảnh báo bảo mật tài khoản";
        String content = "<p>Xin chào <strong>" + (user.getFullName() != null ? user.getFullName() : user.getEmail()) + "</strong>,</p>" +
                "<p>Mật khẩu tài khoản IAM của bạn vừa được thay đổi thành công vào lúc <strong>" + LocalDateTime.now() + "</strong>.</p>" +
                "<div class='card'>" +
                "  <p style='margin: 0;'>Nếu chính bạn thực hiện thao tác này, bạn có thể hoàn toàn yên tâm.</p>" +
                "  <p style='margin: 8px 0 0; color: #d32f2f;'>Nếu không phải bạn thực hiện, vui lòng dùng chức năng <strong>Quên mật khẩu</strong> để đặt lại ngay lập tức hoặc liên hệ quản trị viên.</p>" +
                "</div>";
        String html = getDreamyTemplate(title, subtitle, content);
        sendEmail(user, user.getEmail(), "PASSWORD_CHANGED", "Cảnh báo: Mật khẩu tài khoản IAM của bạn đã thay đổi", html);
    }

    public void sendProfileUpdatedAlert(User user) {
        String title = "Hồ Sơ Đã Được Cập Nhật";
        String subtitle = "Thông tin cá nhân vừa được điều chỉnh";
        String content = "<p>Xin chào <strong>" + (user.getFullName() != null ? user.getFullName() : user.getEmail()) + "</strong>,</p>" +
                "<p>Thông tin hồ sơ cá nhân của bạn vừa được cập nhật thành công trên hệ thống IAM.</p>" +
                "<div class='card'>" +
                "  <p style='margin:0;'><strong>Họ và tên:</strong> " + (user.getFullName() != null ? user.getFullName() : "Chưa cập nhật") + "</p>" +
                "  <p style='margin:5px 0 0;'><strong>Số điện thoại:</strong> " + (user.getPhoneNumber() != null ? user.getPhoneNumber() : "Chưa cập nhật") + "</p>" +
                "  <p style='margin:5px 0 0;'><strong>Thời gian cập nhật:</strong> " + LocalDateTime.now() + "</p>" +
                "</div>";
        String html = getDreamyTemplate(title, subtitle, content);
        sendEmail(user, user.getEmail(), "PROFILE_UPDATED", "Thông báo: Cập nhật hồ sơ cá nhân thành công", html);
    }
}
