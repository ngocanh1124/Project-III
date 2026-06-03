import 'package:flutter/material.dart';
import 'qr_scanner.dart';
import '../models/attendance_flow_state.dart';
import '../services/LocalDatabaseService.dart';
import 'authorization_denied_screen.dart';
import 'package:logging/logging.dart';

class QrScannerScreenV2 extends StatefulWidget {
  final AttendanceFlowState flowState;
  final Function(String can, String cccd) onScanSuccess;

  const QrScannerScreenV2({
    Key? key,
    required this.flowState,
    required this.onScanSuccess,
  }) : super(key: key);

  @override
  State<QrScannerScreenV2> createState() => _QrScannerScreenV2State();
}

class _QrScannerScreenV2State extends State<QrScannerScreenV2> {
  final _log = Logger('QrScannerV2');
  
  String? _extractedCAN;
  String? _extractedCCCD;
  String _statusMessage = "Quét mã QR để lấy thông tin...";
  bool _isProcessing = false;
  bool _isCheckingAuth = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Quét QR - Lớp 1: Kiểm tra Quyền'),
        centerTitle: true,
        elevation: 0,
      ),
      body: Stack(
        children: [
          // QR Scanner
          QrScanner(
            onCanDetected: (String qrData) async {
              setState(() => _isProcessing = true);
              
              // Try to extract CCCD from QR
              final result = _extractFromQR(qrData);
              final can = result['can'];
              final cccd = result['cccd'];

              if (can == null || can.isEmpty) {
                setState(() {
                  _statusMessage = 'Lỗi: Không tìm thấy CAN trong QR';
                  _isProcessing = false;
                });
                return;
              }

              setState(() {
                _extractedCAN = can;
                _extractedCCCD = cccd;
                _statusMessage = 'Lấy được: CAN=$can, CCCD=$cccd';
                _isCheckingAuth = true;
              });

              // **LAYER 1: Early Authorization Check**
              await _checkAuthorizationLayer1(cccd);
            },
          ),

          // Overlay with instructions
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: Container(
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.95),
                borderRadius: const BorderRadius.only(
                  topLeft: Radius.circular(20),
                  topRight: Radius.circular(20),
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.1),
                    blurRadius: 10,
                  ),
                ],
              ),
              padding: const EdgeInsets.all(20),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Status
                  Text(
                    _statusMessage,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w500,
                      color: _extractedCAN != null ? Colors.green : Colors.orange,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 12),

                  // Processing indicator
                  if (_isProcessing || _isCheckingAuth)
                    Column(
                      children: [
                        const SizedBox(
                          height: 24,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          _isCheckingAuth
                              ? 'Đang kiểm tra quyền...'
                              : 'Đang xử lý...',
                          style: const TextStyle(fontSize: 12, color: Colors.grey),
                        ),
                      ],
                    )
                  else
                    // Instructions or back button
                    SizedBox(
                      width: double.infinity,
                      child: _extractedCAN == null
                          ? const Text(
                              'Định hướng máy lên QR code\n'
                              'Lệnh quét sẽ tự động kích hoạt',
                              style: TextStyle(
                                fontSize: 13,
                                color: Colors.grey,
                              ),
                              textAlign: TextAlign.center,
                            )
                          : ElevatedButton(
                              onPressed: () => Navigator.pop(context),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: Colors.grey,
                              ),
                              child: const Text('Huỷ'),
                            ),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// Extract CAN and CCCD from QR code data
  /// Vietnamese CCCD QR formats:
  /// - 6 digits: CAN only
  /// - 9 digits: CCCD số cũ (9 số) — không còn phổ biến
  /// - 12 digits: CCCD mới (12 số như 025304002150)
  /// - CCCD MRZ QR: nhiều trường ngăn cách bởi | hoặc newline
  Map<String, String?> _extractFromQR(String qrData) {
    String? can;
    String? cccd;

    _log.info('QR Data: $qrData');

    // Ưu tiên tìm 12 chữ số trước (CCCD mới Việt Nam)
    final cccd12Match = RegExp(r'\b(\d{12})\b').firstMatch(qrData);
    if (cccd12Match != null) {
      cccd = cccd12Match.group(1);
      // CAN: 6 số của trường riêng trong QR, hoặc lấy 6 số đầu CCCD làm fallback
      // QR CCCD Việt Nam format: "cccd|mrzDocNo|dob|doe|name|address|can" (7 fields)
      final parts = qrData.split(RegExp(r'[\|\n]'));
      if (parts.length >= 7) {
        can = parts[6].trim(); // CAN là field thứ 7
      } else if (parts.length >= 2) {
        // Thử field 2 (index 1) - một số format khác
        final f2 = parts[1].trim();
        can = RegExp(r'^\d{6}$').hasMatch(f2) ? f2 : cccd!.substring(0, 6);
      } else {
        can = cccd!.substring(0, 6);
      }
      _log.info('Extracted CCCD12: $cccd, CAN: $can');
      return {'can': can, 'cccd': cccd};
    }

    // Try to find 9-digit CCCD (số cũ)
    final cccdMatch = RegExp(r'\b(\d{9})\b').firstMatch(qrData);
    if (cccdMatch != null) {
      cccd = cccdMatch.group(1);
      can = cccd!.substring(0, 6);
      _log.info('Extracted CCCD9: $cccd, CAN: $can');
      return {'can': can, 'cccd': cccd};
    }

    // Try to find 6-digit CAN
    final canMatch = RegExp(r'\b(\d{6})\b').firstMatch(qrData);
    if (canMatch != null) {
      can = canMatch.group(1);
      _log.info('Extracted CAN: $can (CCCD unknown)');
      return {'can': can, 'cccd': null};
    }

    // If it's just 6, 9 or 12 digits
    if (RegExp(r'^\d{12}$').hasMatch(qrData)) {
      cccd = qrData;
      can = qrData.substring(0, 6);
      _log.info('QR is CCCD12: $cccd');
      return {'can': can, 'cccd': cccd};
    }
    if (RegExp(r'^\d{6}$').hasMatch(qrData)) {
      can = qrData;
      _log.info('QR is CAN: $can');
      return {'can': can, 'cccd': null};
    }
    if (RegExp(r'^\d{9}$').hasMatch(qrData)) {
      cccd = qrData;
      can = qrData.substring(0, 6);
      _log.info('QR is CCCD9: $cccd');
      return {'can': can, 'cccd': cccd};
    }

    return {'can': null, 'cccd': null};
  }

  /// **LAYER 1: Early Authorization Check (Offline)**
  /// Check if CCCD/CAN is authorized for this door
  /// Fail fast before NFC/camera steps
  Future<void> _checkAuthorizationLayer1(String? cccd) async {
    try {
      if (cccd == null) {
        // Only have CAN, can't fully check yet
        // Need to proceed to NFC to get full CCCD
        _log.info('Only CAN extracted, proceeding to NFC for full CCCD');
        _proceedWithAuthorization();
        return;
      }

      setState(() => _statusMessage = 'Kiểm tra quyền trong hệ thống...');

      // Check authorization in local database
      final isAuthorized = await LocalDatabaseService.checkAccess(cccd);

      if (!mounted) return;

      if (!isAuthorized) {
        _log.warning('Authorization denied for CCCD: $cccd');

        // Redirect to denied screen
        await Navigator.push(
          context,
          MaterialPageRoute(
            builder: (ctx) => AuthorizationDeniedScreen(
              cccdNumber: cccd,
              reason: 'CCCD này không có quyền vào cửa này.\n'
                  'Vui lòng liên hệ quản trị viên.',
            ),
          ),
        );

        // Return to home after denied screen closes
        if (mounted) {
          Navigator.pop(context);
        }
        return;
      }

      // Authorization successful
      _log.info('Authorization successful for CCCD: $cccd');
      _proceedWithAuthorization();
    } catch (e) {
      _log.severe('Authorization check error: $e');
      setState(() {
        _statusMessage = 'Lỗi kiểm tra quyền: $e';
        _isCheckingAuth = false;
      });
    }
  }

  void _proceedWithAuthorization() {
    setState(() {
      _statusMessage = 'Quyền xác nhận ✓ - Tiếp tục...';
      _isCheckingAuth = false;
    });

    widget.flowState.qrData = _extractedCAN;
    widget.flowState.can = _extractedCAN;

    // Callback to parent
    widget.onScanSuccess(_extractedCAN!, _extractedCCCD ?? '');

    Future.delayed(const Duration(milliseconds: 500), () {
      if (mounted) Navigator.pop(context);
    });
  }
}
