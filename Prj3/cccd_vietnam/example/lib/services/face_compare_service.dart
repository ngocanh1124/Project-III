import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';
import 'package:http/http.dart' as http;
import 'package:image/image.dart' as img;
import 'ConfigService.dart';
import 'LocalDatabaseService.dart';
import 'MqttClientService.dart';
import 'OfflineSyncService.dart';
import 'face_embedding_service.dart';

class FaceCompareService {
  static const double _matchThreshold   = 0.65;  // ngưỡng so sánh khuôn mặt Layer 1 (Android on-device)
  static const double _backupThreshold  = 0.65;  // chỉ reject LOCAL khi score CỰC THẤP; server InsightFace quyết định cuối

  /// So s�nh selfie v?i chipPhoto tr�n thi?t b? (Layer 1).
  /// N?u PASS ? g?i l�n server k�m c? hai ?nh + k?t qu? L1 d? server re-verify (Layer 2).
  static Future<Map<String, dynamic>?> compareFaces({
    required Uint8List chipImage,
    required Uint8List selfieImage,
    String? cccd,
    String? fullname,
    bool remoteUnlock = false,
  }) async {
    final serverIp      = await ConfigService.getServerIp();
    final organizationId = await ConfigService.getOrgId();
    final deviceCode    = await ConfigService.getDeviceCode();

    final String hostOnly = serverIp
        .replaceAll('http://', '')
        .replaceAll('https://', '')
        .split(':')[0];

    // Dùng port 8080 (cùng port với dashboard) — port 8081 có thể bị firewall chặn
    final String springBaseUrl = 'http://$hostOnly:8080';

    // -- LAYER 1: So sánh selfie vs chip photo trên thiết bị -----------------
    final double? localScore = await _getMatchScore(
      chipImage: chipImage,
      selfieImage: selfieImage,
    );

    // localScore == null nghĩa là ML Kit không detect được mặt trong ảnh chip/selfie
    // → bỏ qua Layer 1, gửi thẳng lên server (Python InsightFace làm tốt hơn trên ảnh thẻ ID)
    final bool l1DetectedFace = localScore != null;
    final double l1Score  = localScore ?? 0.0;
    // Layer 1 chỉ reject local khi score CỰC THẤP (< 0.10), tức là rõ ràng khác người.
    // Mọi trường hợp khác → gửi server, để Python AI quyết định (Layer 2 là nguồn chính).
    // QUAN TRỌNG: luôn gửi l1Passed=true để server không reject sớm;
    // server sẽ tự so sánh selfie vs chip photo bằng InsightFace/FaceNet.
    const bool l1Passed = true;  // server luôn là người quyết định cuối
    const String l1Method = 'LOCAL_FACE';

    // Chỉ reject local nếu cả hai ảnh đều detect được mặt VÀ score CỰC THẤP (< 0.25)
    // Ảnh chip CCCD thường cho InsightFace score 0.30-0.55 → không reject sớm ở đây
    if (l1DetectedFace && l1Score < _backupThreshold) {
      print('FaceCompareService: L1 reject (obvious mismatch): score=$l1Score < $_backupThreshold');
      return {
        'matched':     false,
        'score':       l1Score,
        'layer1Passed': false,
        'l1Score':     l1Score,
        'success':     false,
        'offline':     true,
        'reason':      'LAYER1_FAILED',
        'message':     'Khuôn mặt không khớp với ảnh chip CCCD (điểm: ${(l1Score * 100).toStringAsFixed(1)}%)',
      };
    }

    // -- G?i l�n server d? Layer 2 re-verify --------------------------------
    // Server s?: L2a selfie vs DB vector + L2b selfie vs chip photo l?i m?t l?n n?a
    final String chipBase64   = base64Encode(chipImage);
    final String selfieBase64 = base64Encode(selfieImage);

    final Map<String, dynamic> body = {
      'deviceCode':    deviceCode,
      'organizationId': organizationId.isNotEmpty ? int.tryParse(organizationId) : null,
      'cccd':          cccd ?? '',
      'capturedName':  fullname ?? '',

      // ?nh d? server re-verify Layer 2
      'imageLive':  selfieBase64,   // Selfie
      'chipImage':  chipBase64,     // Chip photo d? server verify l?i L2b

      // K?t qu? Layer 1 t? Android (server s? ki?m tra layer1Passed tru?c ti�n)
      'layer1Passed':  l1Passed,
      'layer1Score':   l1Score,
      'layer1Method':  l1Method,

      // Remote unlock mode: admin triggered → skip permission check on server
      'remoteUnlock': remoteUnlock,

      // Legacy fields (tuong th�ch ngu?c)
      'matched': l1Passed,
      'score':   l1Score,
      'method':  l1Method,
    };

    // POST l�n port 8081 (device-dedicated port)
    final url = Uri.parse('$springBaseUrl/api/v2/attendance/record');

    try {
      final response = await http.post(
        url,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(body),
      ).timeout(const Duration(seconds: 30)); // 30s: server g?i Python AI 2 l?n

      if (response.statusCode == 200 || response.statusCode == 201) {
        OfflineSyncService.syncPending();
        try {
          final serverData = jsonDecode(response.body);
          final data = serverData['data'] ?? serverData;

          final bool serverGranted = data['accessGranted'] == true;
          final double serverScore = (data['matchScore'] ?? l1Score).toDouble();

          // Chi ti?t two-layer t? server
          final bool selfieVsDb   = data['selfieVsDbPassed']   == true;
          final bool selfieVsChip = data['selfieVsChipPassed'] == true;
          final String status     = data['status']   ?? (serverGranted ? 'SUCCESS' : 'DENIED');
          final String message    = data['message']  ?? '';

          return {
            'matched':          serverGranted,
            'score':            serverScore,
            'layer1Passed':     true,
            'l1Score':          l1Score,
            'selfieVsDbPassed': selfieVsDb,
            'selfieVsChipPassed': selfieVsChip,
            'status':           status,
            'message':          message,
            'success':          true,
            'offline':          false,
          };
        } catch (_) {
          return {
            'matched': l1Passed,
            'score':   l1Score,
            'success': true,
            'offline': false,
          };
        }
      } else if (response.statusCode == 202) {
        // PENDING_APPROVAL: mặt đã xác minh nhưng cần admin phê duyệt
        try {
          final serverData = jsonDecode(response.body);
          final data = serverData['data'] ?? serverData;
          return {
            'matched':      false,
            'pending':      true,
            'logId':        data['attendanceId'],
            'visitorName':  data['fullName'] ?? fullname,
            'status':       'PENDING_APPROVAL',
            'message':      data['message'] ?? 'Đang chờ phê duyệt...',
            'score':        l1Score,
            'success':      true,
            'offline':      false,
          };
        } catch (_) {
          return {'matched': false, 'pending': true, 'status': 'PENDING_APPROVAL',
                  'success': true, 'offline': false, 'score': l1Score};
        }
      } else if (response.statusCode == 403) {
        // Server từ chối (Layer 2 failed)
        try {
          final serverData = jsonDecode(response.body);
          final data = serverData['data'] ?? serverData;
          // Backend đặt authorizationReason vào field 'message' của APIResponseDTO.error()
          // Ví dụ: "OUTSIDE_HOURS", "NO_PERMISSION", "FACE_MISMATCH_CHIP", ...
          final String reason = (data['message'] ?? data['errorCode'] ?? 'DENIED').toString();
          return {
            'matched': false,
            'score':   l1Score,
            'success': true,
            'offline': false,
            'reason':  reason,
            'message': data['errorDetails'] ?? 'Máy chủ từ chối truy cập (Layer 2)',
          };
        } catch (_) {
          return {'matched': false, 'score': l1Score, 'success': false, 'offline': false};
        }
      } else {
        // Server lỗi → fallback offline
        return _buildOfflineResult(cccd, l1Passed, l1Score,
            selfieImage: selfieBase64, fullName: fullname, deviceCode: deviceCode);
      }
    } on Exception catch (e) {
      print('FaceCompareService: network error: $e');
      // Thử gửi qua MQTT trước khi lưu offline (hoạt động khi khác WiFi nhưng có internet)
      final bool mqttSent = await _publishViaMqtt(
        cccd: cccd,
        fullName: fullname,
        deviceCode: deviceCode,
        l1Passed: l1Passed,
        l1Score: l1Score,
        selfieBase64: base64Encode(selfieImage),
        chipBase64: base64Encode(chipImage),
        organizationId: organizationId,
      );
      if (mqttSent) {
        print('FaceCompareService: đã gửi qua MQTT (khác WiFi)');
        return {
          'matched': l1Passed,
          'score': l1Score,
          'layer1Passed': true,
          'success': true,
          'offline': true,
          'mqttSynced': true,
        };
      }
      // Fallback cuối: lưu SQLite, sync sau khi cùng mạng
      return _buildOfflineResult(cccd, l1Passed, l1Score,
          selfieImage: base64Encode(selfieImage), fullName: fullname, deviceCode: deviceCode);
    }
  }

  /// Gửi attendance record qua MQTT khi HTTP không thể kết nối
  static Future<bool> _publishViaMqtt({
    required String? cccd,
    required String? fullName,
    required String? deviceCode,
    required bool l1Passed,
    required double l1Score,
    required String selfieBase64,
    required String chipBase64,
    required String organizationId,
  }) async {
    try {
      if (!MqttClientService.isConnected) return false;
      if (cccd == null || deviceCode == null) return false;

      final payload = jsonEncode({
        'cccd': cccd,
        'deviceCode': deviceCode,
        'capturedName': fullName ?? '',
        'organizationId': organizationId.isNotEmpty ? int.tryParse(organizationId) : null,
        'layer1Passed': l1Passed,
        'layer1Score': l1Score,
        'layer1Method': 'LOCAL_FACE',
        'matched': l1Passed,
        'score': l1Score,
        'method': 'MQTT_SYNC',
        'imageLive': selfieBase64,
        'chipImage': chipBase64,
      });

      MqttClientService.publish('cccd/devices/$deviceCode/attendance', payload);
      return true;
    } catch (e) {
      print('FaceCompareService._publishViaMqtt error: $e');
      return false;
    }
  }

  /// Offline fallback: L1 đã pass → check local whitelist DB + lưu vào SQLite
  static Future<Map<String, dynamic>> _buildOfflineResult(
      String? cccd, bool l1Passed, double l1Score,
      {String? selfieImage, String? fullName, String? deviceCode}) async {
    if (!l1Passed) {
      return {'matched': false, 'score': l1Score, 'success': false, 'offline': true,
              'reason': 'LAYER1_FAILED'};
    }
    bool localAccess = cccd != null
        ? await LocalDatabaseService.checkAccess(cccd)
        : false;

    // Lưu vào SQLite để đồng bộ lên server khi có mạng trở lại
    if (cccd != null && cccd.isNotEmpty) {
      try {
        await LocalDatabaseService.savePendingAttendance(
          cccd: cccd,
          fullName: fullName ?? '',
          deviceCode: deviceCode ?? 'UNKNOWN',
          matched: localAccess,
          score: l1Score,
          method: 'OFFLINE_L1',
          selfieImage: selfieImage,
        );
        print('OfflineQueue: saved pending record for cccd=$cccd');
      } catch (e) {
        print('OfflineQueue: save failed: $e');
      }
    }

    return {
      'matched': localAccess,
      'score':   l1Score,
      'layer1Passed': true,
      'success': true,
      'offline': true,
    };
  }

  static Future<double?> _getMatchScore({
    required Uint8List chipImage,
    required Uint8List selfieImage,
  }) async {
    try {
      final chipEmbedding   = await FaceEmbeddingService.getEmbeddingFromBytes(chipImage);
      final selfieEmbedding = await FaceEmbeddingService.getEmbeddingFromBytes(selfieImage);

      if (chipEmbedding != null && selfieEmbedding != null) {
        return _cosineSimilarity(chipEmbedding, selfieEmbedding);
      }
      // Nếu không detect được mặt trong ảnh chip hoặc selfie → trả null
      // Caller sẽ bỏ qua Layer 1 và gửi thẳng lên server
      print('FaceCompareService: embedding null (chip=${chipEmbedding == null}, selfie=${selfieEmbedding == null}) → skip L1');
    } catch (e) {
      print('FaceCompareService._getMatchScore error: $e');
    }
    return null;
  }

  static double _cosineSimilarity(List<double> a, List<double> b) {
    if (a.length != b.length) return 0.0;
    double dot = 0, normA = 0, normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot   += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    if (normA == 0 || normB == 0) return 0.0;
    final raw = dot / (sqrt(normA) * sqrt(normB));
    // Normalize t? cosine [-1,1] ? [0,1]
    return ((raw + 1) / 2).clamp(0.0, 1.0);
  }

  static double? _localCompare(Uint8List a, Uint8List b) {
    try {
      final imgA = img.decodeImage(a);
      final imgB = img.decodeImage(b);
      if (imgA == null || imgB == null) return null;
      final hashA = _averageHash(imgA);
      final hashB = _averageHash(imgB);
      return 1.0 - (_hammingDistance(hashA, hashB) / 64.0);
    } catch (_) { return null; }
  }

  static List<int> _averageHash(img.Image image) {
    final resized = img.copyResize(image, width: 8, height: 8);
    final grayscale = img.grayscale(resized);
    int sum = 0;
    for (var p in grayscale) { sum += img.getLuminance(p).toInt(); }
    final avg = sum / 64;
    return grayscale.map((p) => img.getLuminance(p) > avg ? 1 : 0).toList();
  }

  static int _hammingDistance(List<int> a, List<int> b) {
    int dist = 0;
    for (var i = 0; i < a.length; i++) { if (a[i] != b[i]) dist++; }
    return dist;
  }
}
