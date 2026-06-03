import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../models/attendance_flow_state.dart';
import '../services/face_compare_service.dart';
import '../services/LocalDatabaseService.dart';
import '../services/MqttClientService.dart';
import '../door_open_screen.dart';
import 'package:logging/logging.dart';

class ComparisonResultScreen extends StatefulWidget {
  final AttendanceFlowState flowState;

  const ComparisonResultScreen({
    Key? key,
    required this.flowState,
  }) : super(key: key);

  @override
  State<ComparisonResultScreen> createState() => _ComparisonResultScreenState();
}

class _ComparisonResultScreenState extends State<ComparisonResultScreen> {
  final _log = Logger('ComparisonResultScreen');
  
  bool _isProcessing = true;
  bool _isSuccess = false;
  String _message = '';
  String _detailMessage = '';

  @override
  void initState() {
    super.initState();
    _processComparison();
  }

  Future<void> _processComparison() async {
    try {
      if (widget.flowState.chipPhotoBytes == null ||
          widget.flowState.selfiePhotoBytes == null) {
        _setState(false, 'Thiếu dữ liệu ảnh');
        return;
      }

      _setState(true, 'Đang so sánh khuôn mặt...', 'Vui lòng đợi');

      // Step 1: Compare faces
      final result = await FaceCompareService.compareFaces(
        chipImage: widget.flowState.chipPhotoBytes!,
        selfieImage: widget.flowState.selfiePhotoBytes!,
        cccd: widget.flowState.cccdNumber,
        fullname: widget.flowState.fullName,
      );

      if (!mounted) return;

      if (result == null) {
        _setState(false, 'Lỗi so sánh khuôn mặt');
        return;
      }

      widget.flowState.comparisonScore = result['score'] ?? 0.0;
      widget.flowState.isMatched       = result['matched'] ?? false;
      widget.flowState.comparisonMode  = result['method'] ?? 'DUAL_LAYER';

      // ── Layer 1 thất bại (thiết bị từ chối trước khi gửi server) ──────────
      if (result['layer1Passed'] == false) {
        final double l1Score = (result['l1Score'] ?? 0.0).toDouble();
        _setState(
          false,
          '✗ Lớp 1 thất bại',
          'Khuôn mặt không khớp với ảnh chip CCCD tại thiết bị.\n'
          'Vui lòng chụp lại selfie rõ hơn.',
        );
        return;
      }

      // ── Server từ chối (Layer 2 failed) ─────────────────────────────────
      if (!(result['matched'] ?? false)) {
        final String reason  = result['reason']  ?? '';
        final String srvMsg  = result['message'] ?? '';
        final double l1Score = (result['l1Score'] ?? 0.0).toDouble();

        String detail = 'Lớp 1 (thiết bị): ✓ Đạt\n';
        if (result['selfieVsDbPassed'] == false) {
          detail += '✗ Lớp 2a: Không khớp dữ liệu trên server\n';
        }
        if (result['selfieVsChipPassed'] == false) {
          detail += '✗ Lớp 2b: Không khớp chip khi server kiểm tra lại\n';
        }
        if (srvMsg.isNotEmpty) detail += srvMsg;

        _setState(false, '✗ Từ chối truy cập', detail.trim());
        return;
      }

      // ── Cả 2 layer pass ──────────────────────────────────────────────────
      widget.flowState.layer2AuthorizedAtServer = true;
      final bool isOnlineResult = result['offline'] == false;

      if (!isOnlineResult) {
        // Offline mode: Layer 1 đã pass → check local whitelist
        final isAuthorized =
            await LocalDatabaseService.checkAccess(widget.flowState.cccdNumber);
        if (!mounted) return;
        if (!isAuthorized) {
          _setState(
            false,
            '✗ Không có quyền truy cập',
            'CCCD này không có trong danh sách được phép (offline).\n'
            'Lớp 1 đã xác thực khuôn mặt nhưng không có quyền thiết bị.',
          );
          widget.flowState.layer2AuthorizedAtServer = false;
          return;
        }
      }

      if (!mounted) return;
      widget.flowState.recordedAt = DateTime.now();

      // MQTT signal (server đã gửi OPEN_DOOR, cái này chỉ là backup log)
      try {
        MqttClientService.publish(
          'devices/${widget.flowState.deviceCode}/record',
          '{"cccd":"${widget.flowState.cccdNumber}",'
          '"name":"${widget.flowState.fullName}",'
          '"timestamp":"${widget.flowState.recordedAt}",'
          '"matched":true,'
          '"score":${widget.flowState.comparisonScore},'
          '"layer":"DUAL_LAYER"}',
        );
      } catch (e) {
        _log.warning('MQTT publish failed: $e');
      }

      // Chuyển ngay sang DoorOpenScreen — nó tự quay về màn hình đầu sau 3s
      if (mounted) {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(
            builder: (_) => DoorOpenScreen(
              label: widget.flowState.fullName ?? 'Truy cập được phép',
            ),
          ),
        );
      }
    } catch (e) {
      _log.severe('Comparison error: $e');
      if (mounted) {
        _setState(false, 'Lỗi xử lý: $e');
      }
    }
  }

  void _setState(
    bool processing,
    String message, [
    String detailMessage = '',
    bool isSuccess = false,
  ]) {
    setState(() {
      _isProcessing    = processing;
      _isSuccess       = isSuccess;
      _message         = message;
      _detailMessage   = detailMessage;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Kết quả so sánh'),
        elevation: 0,
        automaticallyImplyLeading: false,
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Status Icon
              Container(
                width: 100,
                height: 100,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: _isSuccess
                      ? Colors.green.withValues(alpha: 0.1)
                      : Colors.red.withValues(alpha: 0.1),
                ),
                child: Center(
                  child: _isProcessing
                      ? const CircularProgressIndicator(strokeWidth: 3)
                      : Icon(
                          _isSuccess ? Icons.check_circle : Icons.cancel,
                          size: 60,
                          color: _isSuccess ? Colors.green : Colors.red,
                        ),
                ),
              ),
              const SizedBox(height: 32),

              // Main message
              Text(
                _message,
                style: TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  color: _isSuccess ? Colors.green : Colors.red,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),



              const SizedBox(height: 32),

              // Action buttons
              if (!_isProcessing)
                Column(
                  children: [
                    if (!_isSuccess)
                      SizedBox(
                        width: double.infinity,
                        child: ElevatedButton.icon(
                          onPressed: () => Navigator.pop(context),
                          icon: const Icon(Icons.refresh),
                          label: const Text('Thử lại'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.blue,
                            padding: const EdgeInsets.symmetric(vertical: 12),
                          ),
                        ),
                      ),
                    const SizedBox(height: 12),
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        onPressed: () =>
                            Navigator.of(context).popUntil((route) => route.isFirst),
                        icon: const Icon(Icons.home),
                        label: const Text('Quay về trang chủ'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor:
                              _isSuccess ? Colors.green : Colors.grey,
                          padding: const EdgeInsets.symmetric(vertical: 12),
                        ),
                      ),
                    ),
                  ],
                ),
            ],
          ),
        ),
      ),
    );
  }
}
