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
import 'package:logging/logging.dart';

class AttendanceFlowScreen extends StatefulWidget {
  const AttendanceFlowScreen({Key? key}) : super(key: key);

  @override
  State<AttendanceFlowScreen> createState() => _AttendanceFlowScreenState();
}

class _AttendanceFlowScreenState extends State<AttendanceFlowScreen> {
  final _log = Logger('AttendanceFlowScreen');
  
  final _flowState = AttendanceFlowState();
  int _currentStep = 0; // 0: QR (Layer 1 check), 1: CAN/NFC, 2: Face, 3: Done
  String _status = 'Bước 1: Quét QR (Lớp 1 - Kiểm tra quyền Offline)';

  @override
  void initState() {
    super.initState();
    _initializeServices();
  }

  Future<void> _initializeServices() async {
    try {
      // Initialize config
      final deviceCode = await ConfigService.getDeviceCode();
      _flowState.deviceCode = deviceCode;

      if (!MqttClientService.isConnected) {
        await MqttClientService.initialize(_handleMqttCommand);
      } else {
        // MQTT đã kết nối: cướp callback để nhận OPEN_DOOR trên màn hình này
        MqttClientService.updateCallback(_handleMqttCommand);
      }

      _log.info('Services initialized. Device: $deviceCode');
    } catch (e) {
      _log.severe('Service init error: $e');
    }
  }

  void _handleMqttCommand(Map<String, dynamic> data) {
    _log.info('MQTT command received: $data');
    if (data['action'] == 'OPEN_DOOR') {
      final msg = data['message']?.toString() ?? 'Mở cửa từ xa';
      // Hiện màn hình nhập CCCD thay vì mở cửa ngay
      appNavigatorKey.currentState?.push(
        MaterialPageRoute(
          builder: (_) => RemoteUnlockCccdScreen(remoteMessage: msg),
        ),
      );
    }
  }

  Future<void> _startQrScan() async {
    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => QrScannerScreenV2(
          flowState: _flowState,
          onScanSuccess: (can, cccd) {
            // Layer 1 authorization passed (if CCCD was in QR)
            // Layer 2 will be checked at server when sending result
            _flowState.layer1AuthorizedAtQR = true;
            
            setState(() {
              _currentStep = 1;
              _status = 'Bước 2: Đọc Chip CCCD';
            });
          },
        ),
      ),
    );

    if (result == null) {
      setState(() {
        _currentStep = 0;
        _status = 'Layer 1: Quét QR để kiểm tra quyền';
      });
    }
  }

  Future<void> _startNfcRead() async {
    if (_flowState.can == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng quét QR trước')),
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
        const SnackBar(content: Text('Vui lòng đọc chip CCCD trước')),
      );
      return;
    }

    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => FaceCaptureScreen(
          flowState: _flowState,
          onFaceCaptured: (selfieBytes) {
            setState(() {
              _currentStep = 3;
              _status = 'Bước 4: So sánh khuôn mặt';
            });
          },
        ),
      ),
    );

    if (result == true) {
      // Proceed to comparison
      await Future.delayed(const Duration(milliseconds: 300));
      if (mounted) {
        _startComparison();
      }
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
      appBar: AppBar(
        title: const Text('Hệ thống điểm danh'),
        centerTitle: true,
        elevation: 0,
      ),
      body: SingleChildScrollView(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              children: [
                // Device info
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.grey.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    'Thiết bị: ${_flowState.deviceCode ?? 'Chưa xác định'}\n'
                    'Trạng thái: ${_status}',
                    style: const TextStyle(fontSize: 12, height: 1.6),
                    textAlign: TextAlign.center,
                  ),
                ),
                const SizedBox(height: 32),

                // Progress indicator
                SizedBox(
                  height: 100,
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      _buildStepIndicator('QR', 0),
                      _buildStepLine(0),
                      _buildStepIndicator('NFC', 1),
                      _buildStepLine(1),
                      _buildStepIndicator('Mặt', 2),
                      _buildStepLine(2),
                      _buildStepIndicator('So sánh', 3),
                    ],
                  ),
                ),
                const SizedBox(height: 40),

                // Flow data display
                if (_flowState.hasChipData)
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: Colors.blue.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(
                        color: Colors.blue.withValues(alpha: 0.3),
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Dữ liệu từ chip:',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 14,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text('CCCD: ${_flowState.cccdNumber}'),
                        Text('Tên: ${_flowState.fullName}'),
                        Text('Quốc tịch: ${_flowState.nationality}'),
                        if (_flowState.selfiePhotoBytes != null)
                          const Padding(
                            padding: EdgeInsets.only(top: 8),
                            child: Text(
                              '✓ Ảnh selfie đã chụp',
                              style: TextStyle(color: Colors.green),
                            ),
                          ),
                      ],
                    ),
                  ),

                const SizedBox(height: 40),

                // Action buttons
                SizedBox(
                  width: double.infinity,
                  child: Column(
                    children: [
                      if (_currentStep == 0)
                        ElevatedButton.icon(
                          onPressed: _startQrScan,
                          icon: const Icon(Icons.qr_code),
                          label: const Text('Quét QR'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.blue,
                            padding: const EdgeInsets.symmetric(vertical: 16),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                        )
                      else if (_currentStep == 1)
                        ElevatedButton.icon(
                          onPressed: _startNfcRead,
                          icon: const Icon(Icons.nfc),
                          label: const Text('Đọc Chip CCCD'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.blue,
                            padding: const EdgeInsets.symmetric(vertical: 16),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                        )
                      else if (_currentStep == 2)
                        ElevatedButton.icon(
                          onPressed: _startFaceCapture,
                          icon: const Icon(Icons.camera_alt),
                          label: const Text('Chụp ảnh mặt'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.blue,
                            padding: const EdgeInsets.symmetric(vertical: 16),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                        ),
                      const SizedBox(height: 12),
                      SizedBox(
                        width: double.infinity,
                        child: OutlinedButton.icon(
                          onPressed: _resetFlow,
                          icon: const Icon(Icons.restart_alt),
                          label: const Text('Reset'),
                        ),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 40),

                // Debug comparison button (remove in production)
                if (_currentStep == 2 && _flowState.hasChipData)
                  TextButton(
                    onPressed: _startComparison,
                    child: const Text('→ So sánh (Debug)'),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStepIndicator(String label, int step) {
    final isActive = _currentStep >= step;
    final isDone = _currentStep > step;

    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Container(
          width: 48,
          height: 48,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: isActive ? Colors.blue : Colors.grey.withValues(alpha: 0.2),
            border: Border.all(
              color: isActive ? Colors.blue : Colors.grey,
              width: 2,
            ),
          ),
          child: Center(
            child: isDone
                ? const Icon(Icons.check, color: Colors.white)
                : Text(
                    '${step + 1}',
                    style: TextStyle(
                      color: isActive ? Colors.white : Colors.grey,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
          ),
        ),
        const SizedBox(height: 8),
        Text(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w500,
            color: isActive ? Colors.blue : Colors.grey,
          ),
        ),
      ],
    );
  }

  Widget _buildStepLine(int step) {
    final isActive = _currentStep > step;
    return Expanded(
      child: Container(
        height: 2,
        color: isActive ? Colors.blue : Colors.grey.withValues(alpha: 0.2),
        margin: const EdgeInsets.symmetric(horizontal: 4),
      ),
    );
  }
}
