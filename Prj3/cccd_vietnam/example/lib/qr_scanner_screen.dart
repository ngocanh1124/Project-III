import 'package:flutter/material.dart';
import 'qr_scanner.dart'; 

class QrScannerScreen extends StatelessWidget {
  final Future<void> Function(String) onCanDetected; 
  const QrScannerScreen({
    Key? key,
    required this.onCanDetected,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Quét mã QR'),
        backgroundColor: Colors.blue,
      ),
      body: QrScanner(
        onCanDetected: (String qrData) async {
          // Parse CAN from raw data to maintain compatibility with legacy screens
          String? can;
          final match = RegExp(r'\b(\d{6})\b').firstMatch(qrData);
          if (match != null) {
            can = match.group(1);
          } else if (RegExp(r'^\d{12}$').hasMatch(qrData)) {
            can = qrData.substring(0, 6);
          } else {
            can = qrData; // Fallback
          }

          await onCanDetected(can!); 
          if (context.mounted) Navigator.pop(context);
        },
      ),
    );
  }
}