-- Xóa tất cả bản ghi điểm danh test
-- Script này sẽ xóa tất cả dữ liệu trong bảng attendance_logs

DELETE FROM attendance_logs;

-- Reset auto-increment
ALTER TABLE attendance_logs AUTO_INCREMENT = 1;

-- Xác nhận
SELECT 'Đã xóa xong tất cả bản ghi điểm danh!' as result;
SELECT COUNT(*) as total_records FROM attendance_logs;
