# 🌸 IAM SHIELD - CLOUD IDENTITY & ACCESS MANAGEMENT SERVICE 2
> **Hệ thống Quản lý Định danh & Phân quyền Truy cập Toàn diện (Dual-IDP: Keycloak & Self-IDP)**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21%20%2F%2027-orange.svg)](https://www.oracle.com/java/)
[![Keycloak](https://img.shields.io/badge/Keycloak-26.8.2-blue.svg)](https://www.keycloak.org/)
[![SQL Server](https://img.shields.io/badge/SQL%20Server-2022-red.svg)](https://www.microsoft.com/sql-server)
[![Redis](https://img.shields.io/badge/Redis-7.0-darkred.svg)](https://redis.io/)
[![Swagger](https://img.shields.io/badge/Swagger-OpenAPI%203.0-green.svg)](http://localhost:8080/swagger-ui/index.html)

---

## 📖 1. Giới Thiệu Dự Án
**IAM Shield Service 2** là giải pháp quản lý định danh và phân quyền truy cập thế hệ mới, đáp ứng đầy đủ và chuẩn xác 100% các tiêu chuẩn an toàn bảo mật cấp doanh nghiệp theo đề bài **"Xây dựng IAM Service 2"**.

Hệ thống cung cấp giải pháp **Dual Identity Provider (Dual-IDP)** mạnh mẽ, cho phép chuyển đổi mượt mà giữa máy chủ định danh tập trung **Keycloak 26** và cơ chế xác thực nội bộ **Self-IDP**, kết hợp cùng mô hình phân quyền **RBAC (Role-Based Access Control)** với `@PreAuthorize("hasPermission(...)")`, kiểm toán dữ liệu **AuditorAware**, cơ chế **xóa mềm (Soft Delete)** toàn diện, ghi nhật ký **Rolling Daily Logging**, và giao diện **Angular Dashboard Thơ Mộng (Poetic Glassmorphism UI)**.

---

## 🛠️ 2. Công Nghệ Sử Dụng (Tech Stack)

### Backend (Spring Boot Ecosystem)
* **Core Framework**: `Spring Boot 3.4.3`, `Spring Web`, `Spring Data JPA`
* **Security & Auth**: `Spring Security 6`, `Nimbus OAuth2 / JWT (RS256 & HS256)`, `BCrypt Password Encoder`
* **Identity Server**: `Keycloak 26.8.2` (OpenID Connect, OAuth2, Keycloak REST Admin API, OIDC JWKS)
* **Database**: `Microsoft SQL Server 2022` (Dialect `SQLServerDialect`, Filtered Indexes)
* **Cache & Session**: `Redis 7` (Jedis/Lettuce, Token Blacklist, 2FA OTP Storage)
* **API Documentation**: `SpringDoc OpenAPI 3.0` (`springdoc-openapi-starter-webmvc-ui 2.8.5`)
* **Logging System**: `Logback`, `SLF4J`, `RollingFileAppender`, HTTP Request/Response Masking Filter
* **Validation & Utilities**: `Jakarta Validation`, `Lombok`, `Apache Commons IO`

### Frontend
* `Angular 19+` (Standalone Components, Signals, Reactive Forms, RxJS)
* `SCSS Modern Styling` (Glassmorphism Neon, Poetic Sakura Animation, Lucide Icons)

---

## ✨ 3. Các Chức Năng Nổi Bật & Tâm Đắc

### 🔄 1. Kiến Trúc Linh Hoạt Dual-IDP (Keycloak vs Self-IDP)
* Bật/tắt linh hoạt qua cấu hình `app.idp.provider: keycloak` hoặc `self` trong `application.properties`.
* **Chế độ Keycloak**:
  * Khi gọi `/api/v1/auth/login`, hệ thống trả về thông báo kèm đường dẫn đăng nhập chuẩn OIDC của Keycloak và hỗ trợ direct grant.
  * Access Token (RS256) được xác thực với Keycloak JWKS, đồng thời thực hiện **Dual Verification** (kiểm tra tài khoản tồn tại trong `IAM_DB` nội bộ, `deleted = 0`, `is_active = 1`).
  * Đăng ký tài khoản (`/register`) tạo đồng thời trên Keycloak Realm và đồng bộ vào `IAM_DB`.
  * Đổi mật khẩu (`/change-password`) và Đặt lại mật khẩu (`/reset-password`) cập nhật trực tiếp lên Keycloak qua Admin API.
  * Đăng xuất (`/logout`) và Làm mới token (`/refresh-token`) giao tiếp trực tiếp với Keycloak Token & Revocation endpoints.
* **Chế độ Self-IDP**:
  * Xác thực hoàn toàn bằng database nội bộ, mã hóa mật khẩu BCrypt, hỗ trợ 2FA Email OTP và Redis JWT Token Blacklist.

### 🛡️ 2. Phân Quyền Nâng Cao RBAC với `@PreAuthorize("hasPermission(...)")`
* Thiết kế `CustomPermissionEvaluator` cài đặt giao diện `PermissionEvaluator` của Spring Security.
* Phân quyền tới từng endpoint với biểu thức chuẩn: `@PreAuthorize("hasPermission(null, 'USER_READ')")`.
* Quản lý CRUD Quyền, CRUD Vai trò, Gán quyền cho vai trò, Gán vai trò cho người dùng.
* Khởi tạo tự động 2 vai trò hệ thống mặc định:
  * **Quản lý người dùng (`USER_MANAGER`)**: Quyền quản trị tài khoản, khóa/mở khóa, reset mật khẩu, xóa mềm user.
  * **Quản trị hệ thống (`SYSTEM_ADMIN`)**: Toàn quyền quản trị danh mục quyền và vai trò trong hệ thống.

### 🍃 3. Cơ Chế Xóa Mềm (Soft Delete) Toàn Diện
* Áp dụng trên toàn bộ thực thể chính: `users`, `roles`, `permissions`.
* Tích hợp sâu Hibernate `@SQLDelete(sql = "UPDATE ... SET deleted = 1 ...")` và `@SQLRestriction("deleted = 0")`.
* Mọi thao tác truy vấn, phân trang, đăng nhập đều tự động loại trừ các bản ghi đã bị xóa mềm.

### 📝 4. Kiểm Toán Tự Động (JPA AuditorAware)
* Tự động lưu vết người tạo (`created_by`), người sửa đổi cuối cùng (`updated_by`), thời gian tạo (`created_at`), thời gian cập nhật (`updated_at`).
* Tích hợp tự nhiên với `SecurityContextHolder`, ghi nhận chính xác email của người thao tác trong từng request.

### 📄 5. Phân Trang Chuẩn RESTful (Pagination)
* Toàn bộ các API danh sách (`users`, `roles`, `permissions`) hỗ trợ tham số `page`, `size`, `sortBy`, `sortDir` và `search`.
* Trả về cấu trúc `PaginatedResponse<T>` gồm `content`, `currentPage`, `pageSize`, `totalElements`, `totalPages`, `last`.

### 📜 6. Nhật Ký Hệ Thống An Toàn (Rolling Daily Logging & Password Masking)
* Tự động xoay log hàng ngày theo mẫu: `logs/iam-%d{yyyy-MM-dd}.log`.
* `RequestResponseLoggingFilter` ghi vết chi tiết method, URL, status, execution time (ms), request payload, response body.
* **Bảo mật tuyệt đối**: Thuật toán Regex tự động che giấu (masking) các trường nhạy cảm như `password`, `oldPassword`, `newPassword`, `secret` thành `******`.

### 🌸 7. Giao Diện Người Dùng Thơ Mộng (Poetic Dashboard)
* Không gian giao diện huyền ảo với phong cách Glassmorphism tím hồng dạ quang.
* Tích hợp trực tiếp màn hình Quản lý Người dùng, Màn hình Phân quyền RBAC, Xem chi tiết, Đổi mật khẩu, và Quản lý Avatar đa phiên bản.
* Header hiển thị trực quan trạng thái cấu hình IDP hiện tại (`Keycloak Mode` vs `Self-IDP`) cùng liên kết truy cập nhanh Swagger UI.

---

## 🚀 4. Hướng Dẫn Cài Đặt & Khởi Chạy

### 1. Chuẩn Bị Môi Trường
* **Java SDK**: Version 21 hoặc mới hơn
* **Node.js**: Version 18 hoặc 20
* **SQL Server**: Chạy tại `localhost:24333`, database `IAM_DB`
* **Redis Server**: Chạy tại `localhost:6379`
* **Keycloak**: Chạy tại `http://localhost:8180` (Realm: `iam-realm`)

### 2. Khởi Chạy Backend
```bash
cd IAM_Project
mvn clean package -DskipTests
java -jar target/IAM_Project-0.0.1-SNAPSHOT.jar
```
Backend sẽ khởi động tại: `http://localhost:8080`.
* **Swagger UI Tài Liệu API**: `http://localhost:8080/swagger-ui/index.html`
* **OpenAPI JSON Spec**: `http://localhost:8080/v3/api-docs`

### 3. Khởi Chạy Frontend
```bash
cd IAM-frontend
npm install
npm start
```
Frontend sẽ khởi động tại: `http://localhost:4200`.

---

## 📋 5. Danh Mục API Chính (Swagger Summary)

| Nhóm API | Phương Thức | Endpoint | Mô Tả | Quyền Yêu Cầu |
| :--- | :--- | :--- | :--- | :--- |
| **Auth** | POST | `/api/v1/auth/login` | Đăng nhập hệ thống (Dual IDP) | Public |
| | POST | `/api/v1/auth/register` | Đăng ký tài khoản mới (Đồng bộ Keycloak) | Public |
| | POST | `/api/v1/auth/logout` | Đăng xuất (Blacklist & Keycloak Logout) | Authenticated |
| | POST | `/api/v1/auth/refresh-token` | Làm mới access token | Public |
| | GET | `/api/v1/auth/keycloak-config` | Lấy cấu hình OIDC Keycloak | Public |
| **User Mgmt** | GET | `/api/v1/users/admin/users` | Xem danh sách user (Tìm kiếm & Phân trang) | `USER_READ` |
| | POST | `/api/v1/users/admin/users` | Tạo người dùng mới (Keycloak + DB) | `USER_CREATE` |
| | GET | `/api/v1/users/admin/users/{id}` | Xem chi tiết người dùng | `USER_READ` |
| | DELETE | `/api/v1/users/admin/users/{id}` | Xóa mềm người dùng | `USER_DELETE` |
| | PUT | `/api/v1/users/admin/users/{id}/status` | Khóa / Mở khóa tài khoản | `USER_STATUS` |
| | POST | `/api/v1/users/admin/users/{id}/reset-password` | Đặt lại mật khẩu người dùng | `USER_STATUS` |
| | POST | `/api/v1/users/admin/users/{id}/roles` | Gán vai trò cho người dùng | `USER_ROLE_ASSIGN` |
| **RBAC Roles** | GET | `/api/v1/roles` | Lấy danh sách vai trò (Phân trang) | `ROLE_READ` |
| | POST | `/api/v1/roles` | Tạo vai trò mới | `ROLE_CREATE` |
| | PUT | `/api/v1/roles/{id}` | Cập nhật vai trò | `ROLE_UPDATE` |
| | DELETE | `/api/v1/roles/{id}` | Xóa mềm vai trò | `ROLE_DELETE` |
| | POST | `/api/v1/roles/{id}/permissions` | Gán danh sách quyền cho vai trò | `ROLE_PERMISSION_ASSIGN` |
| **RBAC Perms** | GET | `/api/v1/permissions` | Lấy danh sách quyền (Phân trang) | `PERMISSION_READ` |
| | POST | `/api/v1/permissions` | Tạo quyền mới | `PERMISSION_CREATE` |
| | PUT | `/api/v1/permissions/{id}` | Cập nhật thông tin quyền | `PERMISSION_UPDATE` |
| | DELETE | `/api/v1/permissions/{id}` | Xóa mềm quyền | `PERMISSION_DELETE` |

---

## 👥 6. Thông Tin Tác Giả & Đồ Án
* **Đề Tài**: Xây dựng IAM Service 2 (Cloud Identity & Access Management)
* **Quy Trình Git**: Phát triển nhánh `dev`, tích hợp và báo cáo trên nhánh `master`.
"# IAM_Backend_New" 
