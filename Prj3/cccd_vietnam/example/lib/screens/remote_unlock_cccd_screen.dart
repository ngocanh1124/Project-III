import 'dart:convert';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../models/attendance_flow_state.dart';
import '../services/ConfigService.dart';
import '../door_open_screen.dart';
import 'nfc_chip_read_screen.dart';
import 'face_capture_screen.dart';

/// Luong mo cua tu xa danh cho khach vang lai:
/// 1. Quet chip CCCD (NFC) -> lay ten that + anh chip
/// 2. Chup selfie (camera truoc)
/// 3. Server so sanh selfie vs anh chip -> neu khop -> luu log + ESP32 mo relay
///
/// KHONG co nut "Bo qua". Cua CHI mo khi xac minh khuon mat thanh cong.
class RemoteUnlockCccdScreen extends StatefulWidget {
  final String remoteMessage;

  const RemoteUnlockCccdScreen({Key? key, this.remoteMessage = 'Mo cua tu xa'})
      : super(key: key);

  @override
  State<RemoteUnlockCccdScreen> createState() => _RemoteUnlockCccdScreenState();
}

class _RemoteUnlockCccdScreenState extends State<RemoteUnlockCccdScreen> {
  final _flowState = AttendanceFlowState();

  bool _isSubmitting = false;
  String? _errorMessage;

  Future<void> _startNfcRead() async {
    setState(() => _errorMessage = null);
    final result = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => NfcChipReadScreen(flowState: _flowState)),
    );
    if (result == true && mounted) _startFaceCapture();
  }

  Future<void> _startFaceCapture() async {
    if (_flowState.chipPhotoBytes == null) {
      setState(() => _errorMessage = 'Chua doc duoc anh chip CCCD. Vui long thu lai.');
      return;
    }
    final result = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => FaceCaptureScreen(flowState: _flowState, onFaceCaptured: (_) {}),
      ),
    );
    if (result == true && mounted) _submitToServer();
  }

  Future<void> _submitToServer() async {
    if (_flowState.selfiePhotoBytes == null || _flowState.chipPhotoBytes == null) {
      setState(() => _errorMessage = 'Thieu du lieu anh. Vui long thuc hien lai tu dau.');
      return;
    }
    setState(() { _isSubmitting = true; _errorMessage = null; });
    try {
      final serverIp   = await ConfigService.getServerIp();
      final deviceCode = await ConfigService.getDeviceCode();
      final host    = serverIp.replaceAll('http://', '').replaceAll('https://', '').split(':')[0];
      final baseUrl = 'http://$host:8080';
      final selfieB64   = base64Encode(_flowState.selfiePhotoBytes!);
      final chipB64     = base64Encode(_flowState.chipPhotoBytes!);
      final visitorName = _flowState.fullName ?? 'Khach vang lai';
      final cccdNumber  = _flowState.cccdNumber ?? '';

      final response = await http.post(
        Uri.parse('$baseUrl/api/v2/attendance/remote-entry'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'deviceCode':  deviceCode,
          'visitorName': visitorName,
          'selfieImage': selfieB64,
          'chipImage':   chipB64,
          'cccdNumber':  cccdNumber,
        }),
      ).timeout(const Duration(seconds: 30));

      if (!mounted) return;
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final name = data['data']?['visitorName'] ?? visitorName;
        _flowState.reset();
        Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => DoorOpenScreen(label: name)));
      } else if (response.statusCode == 403) {
        final data = jsonDecode(response.body);
        setState(() { _errorMessage = data['message'] ?? 'Khuon mat khong khop voi chip CCCD.'; _isSubmitting = false; });
      } else {
        setState(() { _errorMessage = 'Loi server (${response.statusCode}). Vui long thu lai.'; _isSubmitting = false; });
      }
    } catch (e) {
      if (mounted) setState(() { _errorMessage = 'Loi ket noi: $e'; _isSubmitting = false; });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF1A1A2E),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: const Text('Mo Cua Tu Xa', style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
        centerTitle: true,
      ),
      body: _isSubmitting ? _buildLoading() : _buildLanding(),
    );
  }

  Widget _buildLoading() {
    return const Center(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        CircularProgressIndicator(color: Colors.orange),
        SizedBox(height: 20),
        Text('Dang xac minh khuon mat...', style: TextStyle(color: Colors.white70, fontSize: 15)),
        SizedBox(height: 8),
        Text('Vui long cho (10-30 giay)', style: TextStyle(color: Colors.white38, fontSize: 13)),
      ]),
    );
  }

  Widget _buildLanding() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 20),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 88, height: 88,
            decoration: BoxDecoration(
              shape: BoxShape.circle, color: Colors.orange.shade700,
              boxShadow: [BoxShadow(color: Colors.orange.withOpacity(0.4), blurRadius: 32, spreadRadius: 6)],
            ),
            child: const Icon(Icons.lock_open_rounded, color: Colors.white, size: 44),
          ),
          const SizedBox(height: 16),
          Text(widget.remoteMessage, style: TextStyle(color: Colors.orange.shade300, fontSize: 14), textAlign: TextAlign.center),
          const SizedBox(height: 32),
          _buildStep(1, Icons.nfc_rounded, 'Quet chip CCCD', 'Dat the CCCD len mat sau dien thoai'),
          const SizedBox(height: 12),
          _buildStep(2, Icons.face_retouching_natural, 'Chup anh khuon mat', 'Nhin thang vao camera'),
          const SizedBox(height: 12),
          _buildStep(3, Icons.verified_user_rounded, 'Xac minh & mo cua', 'Khuon mat khop -> cua mo tu dong'),
          const SizedBox(height: 28),
          if (_errorMessage != null) ...[
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.red.shade900.withOpacity(0.5),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: Colors.red.shade400),
              ),
              child: Row(children: [
                const Icon(Icons.error_outline, color: Colors.redAccent, size: 20),
                const SizedBox(width: 8),
                Expanded(child: Text(_errorMessage!, style: const TextStyle(color: Colors.redAccent, fontSize: 13))),
              ]),
            ),
            const SizedBox(height: 16),
          ],
          SizedBox(
            width: double.infinity, height: 56,
            child: ElevatedButton.icon(
              onPressed: _startNfcRead,
              icon: const Icon(Icons.nfc_rounded, size: 22),
              label: Text(_errorMessage != null ? 'Thu lai tu dau' : 'Bat dau - Quet chip CCCD',
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.orange.shade700,
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStep(int num, IconData icon, String title, String subtitle) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.06),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white.withOpacity(0.1)),
      ),
      child: Row(children: [
        Container(
          width: 36, height: 36,
          decoration: BoxDecoration(shape: BoxShape.circle, color: Colors.orange.shade800),
          child: Center(child: Text('$num', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold))),
        ),
        const SizedBox(width: 12),
        Icon(icon, color: Colors.orange.shade300, size: 22),
        const SizedBox(width: 10),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: const TextStyle(color: Colors.white, fontSize: 14, fontWeight: FontWeight.w600)),
          Text(subtitle, style: const TextStyle(color: Colors.white54, fontSize: 12)),
        ])),
      ]),
    );
  }
}
