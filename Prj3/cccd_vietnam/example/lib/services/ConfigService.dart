import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'dart:io';

class ConfigService {
  static const String _serverIpKey = 'server_ip';
  static const String _orgIdKey = 'organization_id';
  static const String _adminPinKey = 'admin_pin';
  static const String _deviceCodeKey = 'device_code';
  static const String _isConfiguredKey = 'is_device_configured';
  static String? _remoteMasterPin; 

  static Future<String> getServerIp() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_serverIpKey) ?? '192.168.1.1';
  }

  static Future<String> getDeviceCode() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_deviceCodeKey) ?? 'CHƯA_CẤU_HÌNH';
  }

  static Future<bool> isDeviceLocked() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_isConfiguredKey) ?? false;
  }

  static Future<bool> fetchRemoteMasterPin() async {
    try {
      final ip = await getServerIp();
      // Đảm bảo URL có port 8080
      final baseUrl = ip.startsWith('http') ? ip : 'http://$ip:8080';
      final url = Uri.parse('$baseUrl/api/config/master-pin');
      
      final response = await http.get(
        url,
        headers: {'X-Device-Secret': 'MY_SUPER_SECRET_KEY'}, 
      ).timeout(const Duration(seconds: 5));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        _remoteMasterPin = data['pin'].toString();
        return true;
      }
    } catch (e) {
      _remoteMasterPin = null; 
      print("Lỗi kết nối Server: $e");
    }
    return false;
  }

  static Future<bool> setDeviceCode(String code, {String? inputPin, bool skipLockCheck = false}) async {
    final prefs = await SharedPreferences.getInstance();
    bool isLocked = prefs.getBool(_isConfiguredKey) ?? false;

    if (isLocked && !skipLockCheck) {
      if (_remoteMasterPin == null || inputPin != _remoteMasterPin) {
        return false;
      }
    }

    _remoteMasterPin = null; // xóa sau khi dùng xong
    await prefs.setString(_deviceCodeKey, code);
    return await prefs.setBool(_isConfiguredKey, true); 
  }

  /// Xác thực Master PIN đã fetch từ server. Trả về true nếu khớp.
  static bool verifyMasterPin(String input) {
    if (_remoteMasterPin == null) return false;
    final ok = input == _remoteMasterPin;
    if (ok) _remoteMasterPin = null; // xóa sau khi xác thực thành công
    return ok;
  }

  static Future<bool> setServerIp(String ip) async {
    final prefs = await SharedPreferences.getInstance();
    return await prefs.setString(_serverIpKey, ip);
  }

  static Future<String> getOrgId() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_orgIdKey) ?? '';
  }

  static Future<bool> setOrgId(String orgId) async {
    final prefs = await SharedPreferences.getInstance();
    return await prefs.setString(_orgIdKey, orgId);
  }

  static Future<bool> checkAdminPin(String pin) async {
    final prefs = await SharedPreferences.getInstance();
    String savedPin = prefs.getString(_adminPinKey) ?? '1234';
    return savedPin == pin;
  }

  static Future<bool> setAdminPin(String newPin) async {
    final prefs = await SharedPreferences.getInstance();
    return await prefs.setString(_adminPinKey, newPin);
  }

  /// Quét toàn bộ subnet cùng mạng WiFi, tìm TẤT CẢ máy đang chạy
  /// server điểm danh (xác minh qua /api/config/ping — chỉ hệ thống điểm
  /// danh trả về {"app":"AttendanceSystem"}).
  /// Trả về danh sách IP tìm được (có thể rỗng).
  static Future<List<String>> discoverServers({
    void Function(int checked, int total)? onProgress,
  }) async {
    // Lấy IP của thiết bị Android trên WiFi
    String? localIp;
    try {
      final interfaces = await NetworkInterface.list(
        type: InternetAddressType.IPv4,
        includeLinkLocal: false,
      );
      for (final iface in interfaces) {
        final isWifi = iface.name.toLowerCase().contains('wlan') ||
            iface.name.toLowerCase().contains('wifi') ||
            iface.name.toLowerCase().contains('eth');
        for (final addr in iface.addresses) {
          if (!addr.isLoopback &&
              (addr.address.startsWith('192.168') ||
               addr.address.startsWith('10.') ||
               addr.address.startsWith('172.'))) {
            if (isWifi || localIp == null) localIp = addr.address;
          }
        }
      }
    } catch (_) {}

    if (localIp == null) return [];

    final parts = localIp.split('.');
    final subnet = '${parts[0]}.${parts[1]}.${parts[2]}';
    const int total = 254;
    int checked = 0;

    // Quét song song, xác minh từng IP bằng /api/config/ping
    final futures = List.generate(total, (i) async {
      final ip = '$subnet.${i + 1}';
      final isAttendanceServer = await _probeServer(ip);
      checked++;
      onProgress?.call(checked, total);
      return isAttendanceServer ? ip : null;
    });

    final results = await Future.wait(futures);
    return results.whereType<String>().toList();
  }

  /// Giữ lại phương thức cũ (trả về 1 IP) để tương thích ngược.
  static Future<String?> discoverServer({
    void Function(int checked, int total)? onProgress,
  }) async {
    final list = await discoverServers(onProgress: onProgress);
    return list.isNotEmpty ? list.first : null;
  }

  /// Thử kết nối 1 IP, xác minh đây đúng là server điểm danh.
  static Future<bool> _probeServer(String ip) async {
    try {
      final url = Uri.parse('http://$ip:8080/api/config/ping');
      final response = await http
          .get(url)
          .timeout(const Duration(milliseconds: 500));
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        return data['app'] == 'AttendanceSystem';
      }
    } catch (_) {}
    return false;
  }
}