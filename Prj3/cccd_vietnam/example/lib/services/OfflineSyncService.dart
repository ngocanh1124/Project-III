import 'dart:convert';
import 'package:http/http.dart' as http;
import 'ConfigService.dart';
import 'LocalDatabaseService.dart';

/// Đồng bộ kết quả quét offline lên server khi có kết nối trở lại
class OfflineSyncService {
  static bool _isSyncing = false;

  /// Gọi hàm này khi MQTT kết nối lại hoặc app khởi động
  static Future<Map<String, int>> syncPending() async {
    if (_isSyncing) return {'sent': 0, 'failed': 0};
    _isSyncing = true;

    int sent = 0;
    int failed = 0;

    try {
      final pending = await LocalDatabaseService.getPendingAttendance();
      if (pending.isEmpty) return {'sent': 0, 'failed': 0};

      final String baseUrl = ConfigService.baseUrl;
      final url = Uri.parse('$baseUrl/api/v2/attendance/batch-offline');

      // Gửi toàn bộ batch 1 request
      final body = pending.map((r) => {
        'cccd': r['cccd'] ?? '',
        'deviceCode': r['device_code'] ?? '',
        'capturedName': r['full_name'] ?? '',
        'matched': r['matched'] == 1,
        'score': r['score'] ?? 0.0,
        'method': r['method'] ?? 'OFFLINE_SYNC',
        'scanTime': r['scan_time'],
        'imageLive': r['selfie_image'] ?? '',
      }).toList();

      try {
        final response = await http.post(
          url,
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode(body),
        ).timeout(const Duration(seconds: 5));

        if (response.statusCode == 200) {
          final ids = pending.map((r) => r['id'] as int).toList();
          await LocalDatabaseService.markSynced(ids);
          sent = pending.length;
          await LocalDatabaseService.cleanOldSynced();
          print('OfflineSync: đồng bộ thành công $sent bản ghi');
        } else {
          failed = pending.length;
          print('OfflineSync: server lỗi ${response.statusCode}');
        }
      } catch (e) {
        failed = pending.length;
        print('OfflineSync: mất mạng - $e');
      }
    } catch (e) {
      print('OfflineSync: lỗi $e');
    } finally {
      _isSyncing = false;
    }

    return {'sent': sent, 'failed': failed};
  }

  /// Kiểm tra server còn sống không (dùng /api/v2/attendance/ping - không cần JWT)
  static Future<bool> isServerReachable() async {
    try {
      final String baseUrl = ConfigService.baseUrl;
      final response = await http.get(
        Uri.parse('$baseUrl/api/v2/attendance/ping'),
      ).timeout(const Duration(seconds: 3));
      return response.statusCode == 200;
    } catch (_) {
      return false;
    }
  }
}
