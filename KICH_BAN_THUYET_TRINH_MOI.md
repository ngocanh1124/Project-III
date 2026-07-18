# KỊCH BẢN THUYẾT TRÌNH BẢO VỆ ĐỒ ÁN TỐT NGHIỆP (15 PHÚT)
## Đề tài: Hệ thống điểm danh thông minh tích hợp CCCD gắn chip và AI nhận diện khuôn mặt

**Cấu trúc slides gợi ý:**
- **S1:** Tiêu đề & Thông tin sinh viên.
- **S2-S3:** Đặt vấn đề (Sự bất tiện của thẻ từ cũ, rò rỉ dữ liệu ảnh).
- **S4-S5:** Kiến trúc hệ thống (Spring Boot, Flutter, Python AI, ESP32).
- **S6-S7:** Kỹ thuật đọc chip NFC (DG1, DG2) & Giao diện app mới.
- **S8-S9:** Công nghệ AI (ArcFace) & Quá trình đối soát ảnh Chip vs Selfie.
- **S10-S11:** Logic Anti-passback & Phân quyền truy cập.
- **S12:** Điều khiển thiết bị qua MQTT & Web Dashboard.
- **S13:** Kết quả thực nghiệm & Định hướng phát triển.

---

## CHI TIẾT KỊCH BẢN (Dành cho 15 phút)

### 1. Giới thiệu (0 - 2 phút)
- Chào hội đồng. Em là [Tên], hôm nay em xin bảo vệ đồ án...
- Điểm khác biệt lớn nhất: Hệ thống của em không yêu cầu người dùng phải đăng ký khuôn mặt trước vào cơ sở dữ liệu. Mọi dữ liệu định danh và hình ảnh mẫu đều được trích xuất trực tiếp từ Card NFC (CCCD gắn chip) tại thời điểm quẹt thẻ. Điều này giải quyết triệt để bài toán bảo mật dữ liệu sinh trắc học tập trung.

### 2. Công nghệ và Kiến trúc (2 - 5 phút)
- Hệ thống sử dụng mô hình Microservices nhẹ: 
  - **Backend Spring Boot:** Xử lý ma trận phân quyền (Access Matrix).
  - **Flutter App:** Đóng vai trò là Terminal thông minh tại cửa.
  - **AI Server:** Chạy ArcFace (InsightFace) có độ chính xác cao >99%.
- Luồng dữ liệu: App Android bóc tách ảnh DG2 từ chip -> Gửi ảnh Selfie cùng ảnh DG2 lên AI Cloud -> Server trả kết quả -> Lệnh mở cửa gửi qua MQTT tới ESP32.

### 3. Tính năng cốt lõi & Demo (5 - 12 phút) - QUAN TRỌNG NHẤT
- **Giao diện App mới:** Em đã tối ưu hóa giao diện với 3 bước: Quét mã QR (lấy mã CAN) -> Đọc NFC (lấy thông tin Chip) -> Đối soát mặt. 
- **Công nghệ NFC:** Sử dụng thuật toán PACE/BAC để truy cập vùng dữ liệu bảo mật.
- **Logic Anti-passback:** Đây là tính năng nâng cao em đã tích hợp. Nếu nhân viên A đã quẹt thẻ "VÀO", hệ thống sẽ lưu trạng thái. Nếu nhân viên A tiếp tục quẹt "VÀO" lần nữa mà chưa có log "RA", hệ thống sẽ cảnh báo gian lận.
- **Điều khiển từ xa:** Demo tính năng Admin mở cửa từ xa qua Web Dashboard, lệnh được truyền đi dưới 500ms thông qua giao thức MQTT.

### 4. Kết luận (12 - 15 phút)
- Hệ thống đã vận hành ổn định trên thiết bị Android thực tế (Samsung S21/S23).
- Thời gian trung bình cho một lượt điểm danh là ~4 giây (bao gồm cả đọc thẻ và chạy AI).
- Hướng phát triển: Tích hợp thêm nhận diện Liveness (chống giả mạo bằng ảnh/video).

---
**Lưu ý khi trả lời câu hỏi:**
- Nếu được hỏi về **độ bảo mật**: Nhấn mạnh việc dữ liệu ảnh chip chỉ tồn tại trong bộ nhớ tạm (RAM) lúc đối soát, không lưu file ảnh thô xuống DB.
- Nếu được hỏi về **MQTT**: Giải thích cơ chế Publish/Subscribe giúp thiết bị IoT (ESP32) không cần mở port (mạng NAT vẫn chạy được).
