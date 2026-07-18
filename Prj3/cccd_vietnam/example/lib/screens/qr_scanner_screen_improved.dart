import 'package:flutter/material.dart';
import 'qr_scanner.dart';
import '../models/attendance_flow_state.dart';

class QrScannerScreen extends StatefulWidget {
  final AttendanceFlowState flowState;
  final Function(String can) onCanExtracted;

  const QrScannerScreen({
    Key? key,
    required this.flowState,
    required this.onCanExtracted,
  }) : super(key: key);

  @override
  State<QrScannerScreen> createState() => _QrScannerScreenState();
}

class _QrScannerScreenState extends State<QrScannerScreen> {
  String? _extractedCAN;
  String _statusMessage = "Quét mã QR để lấy CAN...";
  bool _isProcessing = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Quét QR - Lấy CAN'),
        centerTitle: true,
        elevation: 0,
      ),
      body: Stack(
        children: [
          // QR Scanner
          QrScanner(
            onCanDetected: (String qrData) async {
              setState(() => _isProcessing = true);
              
              final can = _extractCANFromQR(qrData);
              if (can != null && can.isNotEmpty) {
                widget.flowState.qrData = qrData;
                widget.flowState.can = can;
                
                setState(() {
                  _extractedCAN = can;
                  _statusMessage = 'CAN: $can ✓';
                  _isProcessing = false;
                });
                
                // Notify parent and navigate back
                widget.onCanExtracted(can);
                if (mounted) {
                  Future.delayed(const Duration(milliseconds: 500), () {
                    Navigator.pop(context);
                  });
                }
              } else {
                setState(() {
                  _statusMessage = 'Không tìm thấy CAN trong QR';
                  _isProcessing = false;
                });
              }
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
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                      color: _extractedCAN != null ? Colors.green : Colors.orange,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 12),
                  
                  // Instructions
                  if (_extractedCAN == null)
                    const Text(
                      'Định hướng máy lên QR code\nLệnh quét sẽ tự động kích hoạt',
                      style: TextStyle(
                        fontSize: 14,
                        color: Colors.grey,
                      ),
                      textAlign: TextAlign.center,
                    )
                  else
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: const [
                        Icon(Icons.check_circle, color: Colors.green),
                        SizedBox(width: 8),
                        Text(
                          'Sẵn sàng tiếp tục',
                          style: TextStyle(color: Colors.green),
                        ),
                      ],
                    ),
                  const SizedBox(height: 12),
                  
                  // Back button
                  if (_isProcessing)
                    const SizedBox(
                      height: 24,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                      ),
                    )
                  else
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton(
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

  /// Extract CAN from QR code data
  /// QR typically contains: CAN or CCCD number with separators
  String? _extractCANFromQR(String qrData) {
    // Try to find 6-digit CAN
    final canMatch = RegExp(r'\b(\d{6})\b').firstMatch(qrData);
    if (canMatch != null) {
      return canMatch.group(1);
    }
    
    // If it's just the CAN
    if (RegExp(r'^\d{6}$').hasMatch(qrData)) {
      return qrData;
    }
    
    // If it's CCCD with separators (e.g. 123456789 or 123-456-789)
    final cccdMatch = RegExp(r'(\d{9})').firstMatch(qrData);
    if (cccdMatch != null) {
      // First 6 digits are CAN
      return cccdMatch.group(1)!.substring(0, 6);
    }
    
    return null;
  }
}
