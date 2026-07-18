# Hướng dẫn nạp Firmware cho ESP32 (Hệ thống Điểm danh CCCD)

Tài liệu này hướng dẫn cách nạp mã nguồn từ file door_relay_real.ino vào bo mạch ESP32.

### 1. Chuẩn bị Phần mềm
- **Arduino IDE:** Tải và cài đặt phiên bản mới nhất (2.x) từ [arduino.cc](https://www.arduino.cc/en/software).
- **Cài đặt Board ESP32:**
  1. Mở Arduino IDE -> File -> Preferences.
  2. Tại mục Additional Boards Manager URLs, dán link:
     https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
  3. Vào Tools -> Board -> Boards Manager..., tìm kiếm esp32 và nhấn **Install**.

### 2. Cài đặt các Thư viện cần thiết
Vào Tools -> Manage Libraries... (hoặc Ctrl+Shift+I) và cài đặt chính xác các thư viện sau:
1. **PubSubClient** (bởi Nick O'Leary) - Dùng cho kết nối MQTT.
2. **ArduinoJson** (bởi Benoit Blanchon) - Dùng để xử lý dữ liệu JSON (phiên bản 7.x).

### 3. Cấu hình thông số trong Code
Mở file d:\Ex\Prj3\esp32_firmware\door_relay_real\door_relay_real.ino và chỉnh sửa các dòng sau cho phù hợp với môi trường của bạn:

`cpp
// -- WiFi --
#define WIFI_SSID       "Tên_WiFi_Của_Bạn"
#define WIFI_PASSWORD   "Mật_Khẩu_WiFi"

// -- MQTT (Dữ liệu đã được cấu hình sẵn cho HiveMQ Cloud) --
#define MQTT_HOST  "d6d92a4a5dda42df88c5e1a584a37bca.s1.eu.hivemq.cloud"
#define MQTT_USER  "NgocAnh"
#define MQTT_PASS  "i9NEe#ufX4r.yxx"

// -- Mã thiết bị (Phải khớp với Database) --
#define DEVICE_CODE "DEV01"
`

### 4. Thiết lập Arduino IDE để nạp (Upload)
Kết nối ESP32 vào máy tính qua cáp USB và chọn các thông số trong menu Tools:
- **Board:** ESP32 Dev Module
- **Partition Scheme:** Default 4MB with spiffs (Quan trọng để chạy được BLE).
- **Upload Speed:** 921600 (hoặc hạ xuống 115200 nếu nạp lỗi).
- **Port:** Chọn cổng COM tương ứng với ESP32 (Ví dụ: COM3, COM4...).

### 5. Tiến hành nạp
1. Nhấn nút **Verify** (biểu tượng dấu tích) để kiểm tra lỗi code.
2. Nhấn nút **Upload** (biểu tượng mũi tên sang phải) để nạp code.
3. **Lưu ý:** Nếu Arduino IDE hiện thông báo Connecting........_____, hãy **nhấn và giữ nút BOOT** trên bo mạch ESP32 cho đến khi thấy thông báo bắt đầu nạp (Writing at 0x00010000...) thì nhả tay ra.

### 6. Kiểm tra
- Mở Tools -> Serial Monitor.
- Chọn tốc độ baud là **115200**.
- Nếu thấy dòng [WiFi] Connected! và [MQTT] Connected! là thiết bị đã sẵn sàng hoạt động.