# KỊCH BẢN THUYẾT TRÌNH CHI TIẾT (PHIÊN BẢN BẢO VỆ ĐỒ ÁN - 15 PHÚT)
## ĐỀ TÀI: HỆ THỐNG ĐIỂM DANH THÔNG MINH TÍCH HỢP CCCD & AI NHẬN DIỆN KHUÔN MẶT ĐA LỚP
**Sinh viên:** Nguyễn Ngọc Anh
**Mục tiêu:** Chứng minh năng lực thực tế, tính ổn định và giải pháp quản trị an ninh toàn diện.

---

## I. MỞ ĐẦU & GIẢI PHÁP (1.5 phút) - [Slide 1-3]

**Chào hỏi:**
Kính thưa Hội Đồng, em là Nguyễn Ngọc Anh. Đồ án của em tập trung giải quyết bài toán kiểm soát an ninh ra vào sử dụng giải pháp **Xác thực đa thành tố (MFA)** dựa trên nền tảng thẻ CCCD gắn chip.

**Sự khác biệt cốt lõi:**
Hệ thống không chỉ là một công cụ "quét mặt". Điểm đột phá nằm ở việc **tự động hóa hoàn toàn quy trình Zero-Touch**: Từ việc bóc tách ảnh gốc từ chip NFC để đối soát (không cần đăng ký trước), đến việc tự động nhận diện chiều di chuyển (DIR Assignment) và kiểm soát logic **Anti-passback** thông minh để ngăn chặn gian lận ra vào.

---

## II. KIẾN TRÚC HỆ THỐNG & "SYNC PIPELINE" (2 phút) - [Slide 4-6]

**Hệ sinh thái liên nền tảng:**
- **Lõi xử lý (Backend):** Spring Boot 3.2 (Java 21) quản lý nghiệp vụ tập trung và phân quyền RBAC.
- **Dịch vụ AI (Centralized):** Python Flask sử dụng mô hình **ArcFace (buffalo_sc)** với ngưỡng (Threshold) 0.55 cho độ chính xác tối ưu.
- **Trạm đầu cuối (Android Client):** Thực thi bóc tách NFC BAC/PACE và lọc nhiễu bằng AI Lớp 1 (MobileFaceNet).
- **Điều khiển vật lý (IoT):** ESP32 Node nhận lệnh qua giao thức MQTT HiveMQ.

**Kỹ thuật nổi bật - "Sync Pipeline":**
Toàn bộ dữ liệu cấu hình thiết bị và nhân sự được đồng bộ tức thời. Khi quản trị viên thay đổi quyền truy cập trên Web, hệ thống sử dụng **Messaging Broker** để đẩy thay đổi xuống ứng dụng Android ngay lập tức mà không cần đồng bộ thủ công.

---

## III. DEMO PHẦN 1: QUẢN TRỊ VÀ VẬN HÀNH (4 phút) - [Slide 7-10]

**1. Quản lý Quy mô lớn (Bulk Operation):**
- Thao tác **Nhập dữ liệu từ Excel**: Cho phép nạp hàng nghìn hồ sơ nhân viên kèm khung giờ làm việc chỉ trong vài giây.
- Hệ thống tự động khởi tạo mã QR định danh cho từng nhân viên để bắt đầu quy trình Activation.

**2. Ma trận Phân quyền & Quản lý Thiết bị:**
- Demo cấu hình **Access Matrix**: Cài đặt quyền ra vào cho nhân viên theo từng Group hoặc từng Cửa (Gate) cụ thể.
- Quản trị thiết bị ESP32 tập trung, giám sát trạng thái Online/Offline của các trạm kiểm soát.

**3. Giám sát Real-time (WebSocket):**
- Show màn hình Dashboard cập nhật log liên tục. Dữ liệu được đẩy qua **WebSocket STOMP**, cho thấy cả ảnh chụp selfie thực tế và ảnh chip gốc để Operator đối chứng ngay lập tức.

---

## IV. DEMO PHẦN 2: LUỒNG ĐIỂM DANH & ANTI-PASSBACK (5.5 phút) - [Slide 11-15]

**1. Luồng Zero-Touch & DIR Assignment:**
- Thao tác quét thẻ: Nhân viên chỉ cần đặt thẻ, hệ thống tự động nhận diện là chiều **VÀO** hay **RA** dựa trên lịch sử gần nhất, không cần bấm nút chọn chiều trên màn hình.

**2. Xác thực 2 lớp (Hybrid L1/L2):**
- **Lớp 1 (Edge AI):** Ứng dụng Android xác thực tại biên giúp giảm tải server.
- **Lớp 2 (Cloud AI):** Backend gọi AI Server đối soát chéo với ảnh chip gốc DG2 để đưa ra quyết định mở cửa cuối cùng.

**3. Logic Chống gian lận Anti-passback:**
- **Strict Mode:** Chặn truy cập nếu quẹt VÀO liên tiếp mà không quẹt RA (ngăn chặn việc cho mượn thẻ).
- **Soft Mode:** Trong trường hợp quên quẹt VÀO nhưng quẹt RA, hệ thống vẫn linh hoạt mở cửa nhưng đánh **Cảnh báo đỏ** trên Dashboard để nhắc nhở và hậu kiểm.

**4. Signaling Tin cậy (Dual-path MQTT):**
- Lệnh mở cửa được gửi đồng thời qua **Topic định danh riêng** và **Topic Quảng bá (Broadcast)**. Điều này đảm bảo ESP32 luôn nhận được lệnh dù gặp sự cố mạng hay lỗi ID thiết bị.

---

## V. TỔNG KẾT & KẾT LUẬN (2 phút) - [Slide 16-18]

**Kết quả đạt được:**
- **Hiệu năng:** Chu kỳ xác thực AI chỉ mất ~2.8s; toàn bộ luồng E2E từ khi chạm thẻ đến khi khóa cửa mở chỉ ~3.8s.
- **Độ tin cậy:** Cơ chế Self-healing và Dual-signaling giúp hệ thống vận hành ổn định trong điều kiện mạng không lý tưởng.
- **Tính thực tiễn:** Giải pháp bóc tách ảnh từ chip giúp doanh nghiệp không cần xây dựng kho dữ liệu ảnh khuôn mặt tập trung, tuân thủ cao đạo luật bảo vệ dữ liệu cá nhân.

Em xin chân thành cảm ơn Hội đồng! Sau đây em xin mời các thầy cô đặt câu hỏi.

---
**GỢI Ý CÂU HỎI TRẢ LỜI NHANH:**
- **Về Threshold 0.55:** Đây là con số thực nghiệm giúp cân bằng giữa tỉ lệ nhận diện sai (FAR) và từ chối sai (FRR) đối với ảnh bóc tách từ chip NFC.
- **Về MQTT Broadcast:** Giúp dự phòng trong trường hợp ID thiết bị bị thay đổi hoặc mất đồng bộ topic đăng ký riêng lẻ.
- **Về Soft Anti-passback:** Giải pháp hài hòa giữa an ninh và trải nghiệm người dùng, tránh gây ùn tắc tại cửa ra vào khi nhân viên sơ suất.

