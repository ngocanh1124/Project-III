import 'package:flutter/material.dart';
import '../qr_scanner.dart';
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
  bool _hasDetected = false; // Cờ ngăn chặn quét nhiều lần gây lỗi navigation

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
              if (_hasDetected) return;
              
              setState(() {
                _isProcessing = true;
              });
              
              // Try to extract CCCD from QR
              final result = _extractFromQR(qrData);
              final can = result['can'];
              final cccd = result['cccd'];

              if (can == null || can.isEmpty) {
                _log.warning('QR detection failed to parse CAN. Data: $qrData');
                setState(() {
                  _statusMessage = 'Lỗi: Định dạng QR không hỗ trợ trích lọc CAN';
                  _isProcessing = false;
                });
                return;
              }

              // Đánh dấu đã nhận diện thành công
              _hasDetected = true;

              setState(() {
                _extractedCAN = can;
                _extractedCCCD = cccd;
                _statusMessage = 'Lấy được: CAN=$can (Vui lòng kiểm tra CAN trên thẻ)';
                _isCheckingAuth = true;
              });

              _log.info('QR parsing successful. CAN: $can, CCCD: $cccd');
              
              // Thêm thông báo SnackBar để người dùng xác nhận CAN
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text('Mã CAN nhận diện: $can. Nếu sai, vui lòng nhập tay.'),
                    duration: const Duration(seconds: 3),
                  ),
                );
              }

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

    _log.info('Parsing QR Data: $qrData');

    // 1. Trường hợp QR CCCD Việt Nam (ngăn cách bởi | hoặc \n)
    // Format chuẩn: cccd|mrzDocNo|fullName|dob|gender|address|issueDate
    // Một số bản mới có thêm CAN ở cuối (phần thứ 7 hoặc 8)
    final parts = qrData.split(RegExp(r'[\|\n]'));
    if (parts.length >= 3) {
      final f0 = parts[0].trim();
      if (RegExp(r'^\d{12}$').hasMatch(f0)) {
        cccd = f0;
      }
      
      // Tìm CAN (thường là 6 số nằm ở các phần cuối)
      for (int i = parts.length - 1; i >= 0; i--) {
        final field = parts[i].trim();
        if (RegExp(r'^\d{6}$').hasMatch(field)) {
          can = field;
          break; 
        }
      }
      
      if (cccd != null) {
        // Nếu không thấy CAN trong các phần phân cách, fallback tìm 6 số cuối trong toàn chuỗi
        if (can == null) {
          final allMatches = RegExp(r'\b\d{6}\b').allMatches(qrData).toList();
          if (allMatches.isNotEmpty) {
            // Lấy match cuối cùng thường là CAN
            can = allMatches.last.group(0);
          }
        }
        
        _log.info('Detected via Split: CCCD=$cccd, CAN=$can');
        // Theo yêu cầu: CAN là 6 số cuối của CCCD 12 số
        final ruleCan = cccd.length >= 6 ? cccd.substring(cccd.length - 6) : cccd;
        return {'can': ruleCan, 'cccd': cccd};
      }
    }

    // 2. Ưu tiên tìm 12 chữ số (CCCD mới) bất kỳ đâu trong chuỗi
    final cccd12Match = RegExp(r'(\d{12})').firstMatch(qrData);
    if (cccd12Match != null) {
      cccd = cccd12Match.group(1);
      can = cccd!.substring(cccd!.length - 6);
      _log.info('Detected via Rule (Last 6): CCCD=$cccd, CAN=$can');
      return {'can': can, 'cccd': cccd};
    }

    // 3. Try to find 9-digit CCCD (số cũ)
    final cccd9Match = RegExp(r'(\d{9})').firstMatch(qrData);
    if (cccd9Match != null) {
      cccd = cccd9Match.group(1);
      can = cccd!.substring(cccd!.length - 6);
      return {'can': can, 'cccd': cccd};
    }

    // 4. Chỉ có 6 số (CAN)
    final can6Match = RegExp(r'^\d{6}$').firstMatch(qrData);
    if (can6Match != null) {
      can = can6Match.group(1);
      return {'can': can, 'cccd': null};
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

    // Cập nhật FlowState trước khi pop để đảm bảo parent nhận được dữ liệu ngay khi await kết thúc
    widget.flowState.qrData = _extractedCAN;
    widget.flowState.can = _extractedCAN;
    widget.onScanSuccess(_extractedCAN!, _extractedCCCD ?? '');

    // Pop the scanner
    Navigator.pop(context, true);
  }
}
