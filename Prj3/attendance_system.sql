-- =============================================================
-- HỆ THỐNG ĐIỂM DANH THÔNG MINH (CCCD + FACE RECOGNITION)
-- FILE SQL ĐÃ CẬP NHẬT THEO MÃ NGUỒN APP FLUTTER
-- =============================================================

CREATE DATABASE IF NOT EXISTS attendance_db 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE attendance_db;

-- -------------------------------------------------------------
-- 1. PHẦN QUẢN TRỊ NGƯỜI DÙNG WEB (ADMIN/MANAGER)
-- -------------------------------------------------------------

CREATE TABLE IF NOT EXISTS roles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE COMMENT 'ROLE_ADMIN, ROLE_MANAGER'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL COMMENT 'Bcrypt Hash',
    full_name VARCHAR(100),
    email VARCHAR(100) NOT NULL UNIQUE,
    is_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id INT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS verification_otps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    otp_code VARCHAR(6) NOT NULL,
    type ENUM('REGISTRATION', 'FORGOT_PASSWORD', 'CHANGE_PASSWORD') NOT NULL,
    expiry_time DATETIME NOT NULL,
    is_used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_otp_lookup (user_id, otp_code)
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 2. PHẦN NHÂN SỰ & THIẾT BỊ ĐẦU CUỐI (KHỚP VỚI APP FLUTTER)
-- -------------------------------------------------------------

-- Bảng Nhân viên (Map cccd_number -> cccd theo App)
CREATE TABLE IF NOT EXISTS employees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cccd VARCHAR(12) NOT NULL UNIQUE COMMENT 'Khớp biến cccd trong Flutter',
    full_name VARCHAR(100) NOT NULL COMMENT 'Tên đầy đủ từ file Excel',
    birthday DATE,
    gender VARCHAR(10),
    image_raw_url TEXT COMMENT 'Ảnh chip lấy từ DG2 gửi lên lần đầu',
    face_vector JSON COMMENT 'Dữ liệu vector khuôn mặt để so sánh Cách 2',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cccd_search (cccd)
) ENGINE=InnoDB;

-- Bảng Thiết bị (Map device_code từ ConfigService)
CREATE TABLE IF NOT EXISTS devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_code VARCHAR(50) NOT NULL UNIQUE COMMENT 'Khớp deviceCode từ App',
    location_name VARCHAR(100) NOT NULL,
    is_online BOOLEAN DEFAULT FALSE,
    last_sync TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 3. PHẦN LOGIC PHÂN QUYỀN & LỊCH SỬ
-- -------------------------------------------------------------

-- Ma trận Phân quyền
CREATE TABLE IF NOT EXISTS access_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL DEFAULT '2099-12-31',
    start_time TIME NOT NULL DEFAULT '06:00:00',
    end_time TIME NOT NULL DEFAULT '22:00:00',
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    UNIQUE KEY unique_access (employee_id, device_id)
) ENGINE=InnoDB;

-- Nhật ký điểm danh (Map cccd, capturedName, imageLive từ App)
CREATE TABLE IF NOT EXISTS attendance_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cccd VARCHAR(12) COMMENT 'Số CCCD gửi từ App',
    captured_name VARCHAR(100) COMMENT 'Khớp capturedName từ App',
    device_code VARCHAR(50) COMMENT 'Khớp deviceCode từ App',
    scan_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    image_live_url TEXT COMMENT 'Khớp imageLive (selfie) từ App',
    score DOUBLE COMMENT 'Khớp score từ App',
    method ENUM('APP_OFFLINE', 'SERVER_SIDE') NOT NULL,
    status ENUM('SUCCESS', 'FAILED', 'WRONG_LOCATION', 'OUT_OF_TIME', 'STRANGER') NOT NULL,
    notes VARCHAR(255),
    INDEX idx_attendance_cccd (cccd),
    INDEX idx_attendance_time (scan_time)
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 4. DỮ LIỆU MẪU (SEED DATA)
-- -------------------------------------------------------------

INSERT INTO roles (role_name) VALUES ('ROLE_ADMIN'), ('ROLE_MANAGER');

-- Tài khoản Admin mặc định: admin / admin123
-- !!! CẢNH BÁO PRODUCTION: Thay mật khẩu ngay sau khi deploy bằng endpoint PUT /api/users/{id}/password !!!
-- Hash mới có thể generate tại: https://bcrypt-generator.com (rounds=10)
INSERT INTO users (username, password, full_name, email, is_verified) 
VALUES ('admin', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.7uSyLnS', 'Admin Manager', 'admin@example.com', TRUE);

INSERT INTO user_roles (user_id, role_id) VALUES (1, 1);

-- Thiết bị mặc định khớp với App Flutter của bạn
INSERT INTO devices (device_code, location_name) VALUES ('GATE_01', 'Cổng Chính');