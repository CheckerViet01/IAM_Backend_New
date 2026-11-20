package com.example.IAM_Project.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @Size(max = 300, message = "Họ tên không vượt quá 300 ký tự")
    private String fullName;

    @Pattern(regexp = "(^$|[0-9]{10,15})", message = "Số điện thoại phải từ 10 đến 15 chữ số")
    private String phoneNumber;

    @Size(max = 1000, message = "Địa chỉ không vượt quá 1000 ký tự")
    private String address;

    private LocalDate dateOfBirth;

    @Pattern(regexp = "(^$|MALE|FEMALE|OTHER)", message = "Giới tính phải là MALE, FEMALE hoặc OTHER")
    private String gender;
}
