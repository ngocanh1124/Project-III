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
    return QrScanner(
      onCanDetected: (String can) async {
        await onCanDetected(can); 
        Navigator.pop(context);
      },
    );
  }
}