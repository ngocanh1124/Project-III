import 'package:http/http.dart' as http;
import 'dart:convert';
import 'package:flutter/material.dart';
import '../models/attendance_flow_state.dart';
import '../services/ConfigService.dart';
import '../services/MqttClientService.dart';
import '../door_open_screen.dart';
import '../app_navigator.dart';
import 'remote_unlock_cccd_screen.dart';
import 'qr_scanner_screen_v2.dart';
import 'nfc_chip_read_screen.dart';
import 'face_capture_screen.dart';
import 'comparison_result_screen.dart';
import '../face_compare_screen.dart';
import '../settings_screen.dart';
import 'package:logging/logging.dart';

class AttendanceFlowScreen extends StatefulWidget {
  const AttendanceFlowScreen({Key? key}) : super(key: key);

  @override
  State<AttendanceFlowScreen> createState() => _AttendanceFlowScreenState();
}

class _AttendanceFlowScreenState extends State<AttendanceFlowScreen> {
  final _log = Logger('AttendanceFlowScreen');
  
  final _flowState = AttendanceFlowState();
  final _canController = TextEditingController();
  int _currentStep = 0; // 0: QR (Layer 1 check), 1: CAN/NFC, 2: Face, 3: Done
  String _status = '';
  String _nfcStatus = 'Sẵn sàng';
  bool _mqttConnected = false;
  late Stream<bool> _mqttStatusStream;

  @override
  void initState() {
    super.initState();
    _initializeServices();
    // Tạo stream giả lập để cập nhật UI
    Stream.periodic(const Duration(seconds: 3)).listen((_) {
      if (mounted) {
        setState(() {
          _mqttConnected = MqttClientService.isConnected;
        });
      }
    });
  }

  @override
  void dispose() {
    _canController.dispose();
    super.dispose();
  }

  Future<void> _initializeServices() async {
    try {
      // Initialize config
      final deviceCode = await ConfigService.getDeviceCode();
      setState(() {
        _flowState.deviceCode = deviceCode;
        _mqttConnected = MqttClientService.isConnected;
      });

      if (!MqttClientService.isConnected) {
        await MqttClientService.initialize(_handleMqttCommand);
      } else {
        // MQTT đã kết nối: cướp callback để nhận OPEN_DOOR trên màn hình này
        MqttClientService.updateCallback(_handleMqttCommand);
      }
      
      setState(() {
        _mqttConnected = MqttClientService.isConnected;
      });

      _log.info('Services initialized. Device: $deviceCode, MQTT: $_mqttConnected');
    } catch (e) {
      _log.severe('Service init error: $e');
    }
  }

  void _handleMqttCommand(Map<String, dynamic> data) {
    _log.info('MQTT command received: $data');
    final action = data['action']?.toString() ?? '';
    
    if (action == 'START_AUTH_FLOW') {
      // Admin yêu cầu xác thực bằng CCCD (NFC)
      final msg = data['message']?.toString() ?? 'Xác thực bắt buộc (Thẻ CCCD cứng)';
      _log.info('MQTT: Admin yêu cầu quét thẻ CCCD (START_AUTH_FLOW)');
      appNavigatorKey.currentState?.push(
        MaterialPageRoute(
          builder: (_) => RemoteUnlockCccdScreen(remoteMessage: msg),
        ),
      );
    } else if (action == 'OPEN_DOOR') {
      // Admin bấm "Mở ngay" -> Không yêu cầu CCCD, hiển thị màn hình mở cửa luôn
      // Dùng 'message' hoặc 'reason' hoặc 'name' để hiển thị
      final msg = data['message']?.toString() ?? 
                  data['reason']?.toString() ?? 
                  (data['name'] != null ? 'Mở cửa cho ${data['name']}' : 'Admin đã mở cửa từ xa');
      
      _log.info('MQTT: Admin mở cửa trực tiếp (OPEN_DOOR)');
      appNavigatorKey.currentState?.push(
        MaterialPageRoute(builder: (_) => DoorOpenScreen(label: msg)),
      );
    }
  }

  Future<void> _checkActivationViaAPIAndProceed(String can, String cccdNumber) async {
    _flowState.layer1AuthorizedAtQR = true;

    try {
      final String baseUrl = ConfigService.baseUrl;
      final String safeCccd = cccdNumber.isNotEmpty ? cccdNumber : "UNKNOWN";
      
      _log.info("Check-QR: safeCccd=$safeCccd (Server: $baseUrl)");
      setState(() => _status = 'Đang kiểm tra trạng thái...');
      
      // Giảm timeout xuống 3s để tránh chờ quá lâu nếu sai IP
      final url = Uri.parse("$baseUrl/api/employees/check-qr?cccd=$safeCccd");
      final response = await http.get(url).timeout(const Duration(seconds: 3));

      if (response.statusCode == 200) {
        final Map<String, dynamic> data = jsonDecode(response.body);
        final String status = data['status']?.toString() ?? 'PENDING';
        final String message = data['message']?.toString() ?? 'Kiểm tra xong';

        if (status == 'ACTIVE' && data['chipImageBase64'] != null) {
          final String base64Str = data['chipImageBase64'];
          _log.info("Warm Start! User ACTIVE: ${data['fullName']}");
          
          setState(() {
            _status = 'Xác minh: ' + (data['fullName'] ?? 'Thành viên');
            _flowState.chipPhotoBytes = base64Decode(base64Str);
            _flowState.cccdNumber = data['cccd']?.toString() ?? cccdNumber;
            _flowState.fullName = data['fullName']?.toString();
            _currentStep = 2; // Nhảy thẳng tới Bước 3: Chụp ảnh
          });

          await Future.delayed(const Duration(milliseconds: 1000));
          if (mounted) {
            _startFaceCapture();
          }
          return; // KẾT THÚC LUỒNG ACTIVE, KHÔNG CHẠY XUỐNG NFC
        } else {
          // Trường hợp PENDING hoặc ACTIVE nhưng thiếu ảnh chip trên server
          _log.info("Server response: $status - $message. Fallback to NFC.");
          setState(() => _status = status.contains('PENDING') ? 'Tài khoản chờ kích hoạt. Vui lòng quét thẻ CCCD.' : message);
          // Chờ 1.5s để người dùng đọc thông báo
          await Future.delayed(const Duration(milliseconds: 1500));
        }
      } else {
        _log.warning("Server error ${response.statusCode}");
        setState(() => _status = 'Lỗi kết nối server (Code: ${response.statusCode}). Quét NFC offline.');
        await Future.delayed(const Duration(seconds: 1));
      }
    } catch (e) {
      _log.warning("Warm Start check failed: $e");
      setState(() => _status = 'Không kết nối được server. Chuyển sang chế độ Offline (Quét NFC).');
      await Future.delayed(const Duration(seconds: 1));
    }

    // LUỒNG CỐ ĐỊNH CHO PENDING / OFFLINE / LỖI: BẮT BUỘC QUÉT CHIP NFC
    if (mounted) {
      setState(() {
        _currentStep = 1;
        _status = 'Bước 2: Đọc thẻ Chip (NFC)';
      });
      _startNfcRead();
    }
  }

  Future<void> _startQrScan() async {
    // Reset state cũ để đảm bảo không bị nhận diện nhầm kết quả lần trước
    _flowState.can = null;
    _flowState.cccdNumber = null;

    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => QrScannerScreenV2(
          flowState: _flowState,
          onScanSuccess: (can, cccd) {
            // Callback này sẽ chạy TRƯỚC khi Navigator.pop bên trong QrScannerScreenV2 hoàn tất
            _flowState.can = can;
            _flowState.cccdNumber = cccd;
          },
        ),
      ),
    );

    // Kiểm tra kết quả sau khi màn hình quét đóng lại
    if (_flowState.can != null) {
      final String can = _flowState.can!;
      final String cccd = _flowState.cccdNumber ?? '';
      
      setState(() {
        _canController.text = can;
      });

      _log.info("QR Scan success: CAN=$can, CCCD=$cccd. Checking activation...");
      await _checkActivationViaAPIAndProceed(can, cccd);
    } else {
      _log.info("QR Scan cancelled or failed to extract CAN.");
      setState(() {
        _currentStep = 0;
        _status = 'Bước 1: Quét QR để lấy CAN';
      });
    }
  }

  Future<void> _startNfcRead() async {
    // Ưu tiên lấy CAN từ controller nếu người dùng nhập tay
    final manualCan = _canController.text.trim();
    if (manualCan.isNotEmpty) {
      _flowState.can = manualCan;
    }

    if (_flowState.can == null || _flowState.can!.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng quét QR hoặc nhập mã CAN')),
      );
      return;
    }

    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => NfcChipReadScreen(
          flowState: _flowState,
        ),
      ),
    );

    if (result == true) {
      setState(() {
        _currentStep = 2;
        _status = 'Bước 3: Chụp ảnh mặt';
      });
      
      // Auto proceed to face capture
      await Future.delayed(const Duration(milliseconds: 500));
      if (mounted) {
        _startFaceCapture();
      }
    }
  }

  Future<void> _startFaceCapture() async {
    if (_flowState.chipPhotoBytes == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Thiếu dữ liệu trích xuất từ thẻ chip vật lý')),
      );
      return;
    }

    // Chuyển sang giao diện So sánh khuôn mặt (Image 2)
    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => FaceCompareScreen(
          chipImageBytes: _flowState.chipPhotoBytes!,
          cccd: _flowState.cccdNumber ?? '',
          fullname: _flowState.fullName ?? '',
        ),
      ),
    );

    if (result == true) {
      _resetFlow();
    }
  }

  Future<void> _startComparison() async {
    if (mounted) {
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (context) => ComparisonResultScreen(
            flowState: _flowState,
          ),
        ),
      );

      // Reset flow after completion
      _resetFlow();
    }
  }

  void _resetFlow() {
    setState(() {
      _flowState.reset();
      _currentStep = 0;
      _status = 'Bước 1: Quét QR để lấy CAN';
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        title: const Text('Đọc Thông Tin CCCD', style: TextStyle(color: Colors.black, fontWeight: FontWeight.normal)),
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: false,
        actions: [
          IconButton(
            icon: const Icon(Icons.settings, color: Colors.grey),
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => SettingsScreen()),
              );
            },
          ),
        ],
      ),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 20),
              
              // Button List
              _buildWideButton(
                label: 'Quét mã QR trên CCCD',
                icon: Icons.qr_code_scanner_outlined,
                onPressed: _startQrScan,
              ),
              const SizedBox(height: 12),
              _buildWideButton(
                label: 'Test NFC',
                icon: Icons.contactless_outlined,
                onPressed: _startNfcRead,
              ),
              const SizedBox(height: 12),
              _buildWideButton(
                label: 'So sánh khuôn mặt',
                icon: Icons.face_outlined,
                onPressed: _startFaceCapture,
              ),
              
              const SizedBox(height: 32),
              
              // CAN Field
              const Text(
                'Mã CAN (6 số cuối của số định danh)',
                style: TextStyle(fontSize: 14, color: Colors.grey),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _canController,
                decoration: InputDecoration(
                  hintText: '002150',
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8),
                    borderSide: BorderSide(color: Colors.grey.shade300),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8),
                    borderSide: BorderSide(color: Colors.grey.shade300),
                  ),
                  contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                ),
                keyboardType: TextInputType.number,
                onChanged: (val) {
                  _flowState.can = val;
                },
              ),
              
              const SizedBox(height: 48),
              
              // Device Info & MQTT Status
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'DVC: ${_flowState.deviceCode ?? '...'}',
                    style: const TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                  Row(
                    children: [
                      Container(
                        width: 8,
                        height: 8,
                        decoration: BoxDecoration(
                          color: _mqttConnected ? Colors.green : Colors.red,
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 4),
                      Text(
                        _mqttConnected ? 'Online' : 'Offline',
                        style: TextStyle(fontSize: 12, color: _mqttConnected ? Colors.green : Colors.red),
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 12),
              
              // NFC Status
              RichText(
                text: TextSpan(
                  style: const TextStyle(fontSize: 22, color: Colors.black),
                  children: [
                    const TextSpan(text: 'NFC: ', style: TextStyle(fontWeight: FontWeight.bold)),
                    TextSpan(text: _nfcStatus),
                  ],
                ),
              ),
              
              const SizedBox(height: 32),
              
              // Hidden debug/reset or state display if needed
              if (_flowState.fullName != null)
                 Padding(
                   padding: const EdgeInsets.only(top: 20),
                   child: Center(child: Text("Chào, ${_flowState.fullName}")),
                 ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildWideButton({
    required String label,
    required IconData icon,
    required VoidCallback onPressed,
  }) {
    return SizedBox(
      width: double.infinity,
      height: 56,
      child: ElevatedButton(
        onPressed: onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: const Color(0xFFEEEEEE),
          foregroundColor: Colors.black,
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(28),
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: Colors.grey[600], size: 24),
            const SizedBox(width: 12),
            Text(
              label,
              style: const TextStyle(fontSize: 16, color: Color(0xFF555555), fontWeight: FontWeight.w400),
            ),
          ],
        ),
      ),
    );
  }
}

