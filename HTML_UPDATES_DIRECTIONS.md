# HTML Documentation Updates - Direction Field Implementation

## For GIAI_THICH_CODE.html

### Section to ADD after "AttendanceLog.java" explanation (after line ~550):

```html
  <!-- Device Entity with Direction -->
  <h3 id="java-device">📄 Device.java — Entity (Thiết bị đầu cuối) <span style="color:#f5c842">[UPDATED]</span></h3>
  <p>Mỗi thiết bị ESP32 đặt tại một cửa (cổng vào, cổng ra, sảnh chính...). Thiết bị phải được cấu hình chiều: <strong>Vào (IN)</strong> hay <strong>Ra (OUT)</strong>. Chiều này được tự động ghi vào tất cả bản ghi điểm danh từ thiết bị.</p>
  <div class="code-wrap">
    <div class="code-header">Device.java — Inner Enum & Field</div>
    <table class="lines">
      <tr><td>public enum Direction {<br/>&nbsp;&nbsp;IN("Vào"),<br/>&nbsp;&nbsp;OUT("Ra");<br/>&nbsp;&nbsp;private final String displayName;<br/>}</td><td><strong>[NEW]</strong> Inner enum cho chiều di chuyển. Mỗi thiết bị có một chiều cố định (immutable). Không phải user chọn khi quét, mà là cấu hình hệ thống. Điều này đảm bảo HR tính toán chính xác thời gian công: <strong>IN = lần vào</strong>, <strong>OUT = lần ra</strong>.</td></tr>
      <tr><td>@Column(name = "direction", length = 10)<br/>@Enumerated(EnumType.STRING)<br/>@Builder.Default<br/>private Direction direction = Direction.IN;</td><td><strong>[NEW]</strong> Field lưu chiều device. Enum được serialize thành string ("IN" hoặc "OUT") trong MySQL. Default là "IN" nếu không chỉ định. @Builder.Default để builder() tự set IN nếu không cung cấp.</td></tr>
      <tr><td>getDirectionDisplay()</td><td><strong>[NEW]</strong> Trả về "Vào" hoặc "Ra" dựa trên giá trị enum. Dùng khi hiển thị trên UI.</td></tr>
    </table>
  </div>

  <div class="note">
    <strong>⚠️ Why Device property, not per-scan?</strong> Nếu để user chọn chiều khi quét (per-scan), họ dễ nhầm → ghi sai dữ liệu. Thay vào đó, chiều được cấu hình ONCE khi tạo device. Từ đó, mọi quét tại device này tự động inherit chiều. Clean, an toàn, dễ bảo trì.
  </div>
```

### Section to ADD after "AttendanceLog" explanation (enhance existing):

```html
      <tr><td><strong>[NEW]</strong> direction ENUM('IN', 'OUT')</td><td>Chiều di chuyển của nhân viên. Tự động gán từ device.direction khi quét thẻ — nhân viên không thể thay đổi. Dùng để tính toán chính xác thời gian công (vào-ra, tính lương, sinh tế...).</td></tr>
      <tr><td>Auto-assignment in AttendanceService</td><td>Khi processAttendanceAndReply() lưu log, nó tra cứu device từ deviceCode → lấy direction → set log.direction = device.direction.toString(). Đảm bảo 100% consistency.</td></tr>
```

### Section to ADD - New Subsection for DeviceControllerV2:

```html
  <h3 id="java-devicectrl">📄 DeviceControllerV2.java — REST API cho Device CRUD <span style="color:#f5c842">[UPDATED]</span></h3>
  <p>Controller xử lý các request từ Dashboard React: danh sách device, tạo device mới, cập nhật cấu hình (bao gồm chiều di chuyển).</p>
  <div class="code-wrap">
    <div class="code-header">DeviceControllerV2.java — Direction Field Support</div>
    <table class="lines">
      <tr><td>@PostMapping("/devices")<br/>createDevice(@RequestBody Map request)</td><td>Tạo device mới. Request body có thể chứa: <code>"direction": "IN"</code> hoặc <code>"OUT"</code>. Server validate và convert string → Device.Direction enum trước khi save.</td></tr>
      <tr><td>@PutMapping("/devices/{id}")<br/>updateDevice(..., @RequestBody Map request)</td><td><strong>[ENHANCED]</strong> Cập nhật device. Nếu request chứa <code>"direction"</code>, server validate và update. Chỉ ADMIN/SUPER_ADMIN được phép edit (checked via @PreAuthorize).</td></tr>
      <tr><td>if (request.containsKey("direction")) {<br/>&nbsp;&nbsp;String dirStr = (String) request.get("direction");<br/>&nbsp;&nbsp;device.setDirection(Device.Direction.valueOf(dirStr.toUpperCase()));<br/>}</td><td>Xử lý direction update: extract string từ request → validate enum → set. Throws IllegalArgumentException nếu string không phải "IN" hoặc "OUT".</td></tr>
      <tr><td>toMap() method includes:<br/>map.put("direction", device.getDirection().toString())</td><td><strong>[ENHANCED]</strong> API response giờ chứa direction field. Client (Dashboard) nhận được direction để hiển thị trong danh sách device.</td></tr>
    </table>
  </div>

  <div class="warn">
    <strong>🔒 Permission Check:</strong> PUT /api/v2/devices/{id} có @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')"). Ngoài backend, React Dashboard UI cũng disable direction field cho non-admin user (gray background, disabled attribute).
  </div>
```

---

## For BaoCaoChiTiet.html

### Already Updated:
- ✅ Devices table: Added direction ENUM column
- ✅ Attendance_logs table: Added direction ENUM column  
- ✅ Device API Section: Enhanced with direction field support and auto-assignment explanation

### Additional Note to ADD in Security Section:

```html
      <tr><td><span class="role-badge" style="background:#fef9c3;color:#713f12">ADMIN</span></td><td>✅ + Can edit <strong>Device direction</strong></td><td>✅</td><td>✅</td><td>✅ Trong org</td><td>❌</td></tr>
```

(Add this row to the RBAC table, showing that ADMIN can edit device direction)

---

## Summary of Changes Made

### ✅ BaoCaoChiTiet.html
1. Added `direction ENUM('IN', 'OUT') DEFAULT 'IN'` to devices table definition
2. Added `direction ENUM('IN', 'OUT')` to attendance_logs table definition
3. Updated devices ERD entity to show direction field
4. Enhanced Device API section (7.4) with comprehensive explanation:
   - New feature heading with color tag
   - Request/Response body examples
   - Auto-assignment logic explanation
   - Permission control note

### ✅ GIAI_THICH_CODE.html (Recommended additions)
1. Add Device.java with Direction enum explanation (after Employee.java)
2. Add enhanced AttendanceLog description mentioning auto-assign of direction
3. Add DeviceControllerV2.java section covering:
   - POST endpoint with direction parameter
   - PUT endpoint with direction update handling  
   - toMap() method returning direction
   - Permission controls (@PreAuthorize)

---

## Implementation Notes

**Backend Auto-Assignment Flow:**
```
User scan at device GATE_01_IN (direction='IN')
↓
Android sends: POST /api/v2/attendance/record with deviceCode="GATE_01_IN"
↓
Server AttendanceService.processAttendanceAndReply()
↓
Lookup: deviceRepository.findByDeviceCode("GATE_01_IN")
↓
Found Device with direction=Direction.IN
↓
Set attendanceLog.direction = "IN" (convert enum to string)
↓
Save & broadcast via WebSocket
↓
Dashboard shows log with direction badge (blue, icon ➡️)
```

**Permission Flow:**
```
Non-Admin user opens DeviceManagement.js
↓
No permission check needed (OPERATOR+ can view all devices)
↓
If user tries to edit direction field:
  - React: Input disabled (CSS pointer-events: none)
  - If somehow sends PUT request:
    - Spring Security: @PreAuthorize blocks
    - Returns 403 Forbidden
```

**UI Color Scheme:**
- **IN (Vào)**: Background #dbeafe (light blue), Text #0284c7 (dark blue), Icon FiArrowRight (➡️)
- **OUT (Ra)**: Background #fecaca (light red), Text #dc2626 (dark red), Icon FiArrowLeft (⬅️)

---

## Files Modified

| File | Changes |
|------|---------|
| d:\Prj3\BaoCaoChiTiet.html | ✅ Updated (3 sections modified) |
| d:\Prj3\GIAI_THICH_CODE.html | 📝 Recommended additions (see above) |

Total updates: **7 major sections** across both HTML files
