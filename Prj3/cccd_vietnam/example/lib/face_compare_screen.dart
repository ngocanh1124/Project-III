import 'dart:typed_data';
import 'dart:io';
import 'dart:convert'; // Xử lý JSON
import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'services/face_compare_service.dart';
import 'services/MqttClientService.dart'; 
import 'services/ConfigService.dart'; 
import 'package:image/image.dart' as img; // Thư viện xử lý ảnh
import 'door_open_screen.dart';

class FaceCompareScreen extends StatefulWidget {
  final Uint8List chipImageBytes;
  final String cccd;
  final String fullname;
  final bool remoteUnlock; // true khi được trigger bởi admin mở cửa từ xa
  const FaceCompareScreen({
    super.key, 
    required this.chipImageBytes,
    required this.cccd,          
    required this.fullname,
    this.remoteUnlock = false,
  });

  @override
  State<FaceCompareScreen> createState() => _FaceCompareScreenState();
}

class _FaceCompareScreenState extends State<FaceCompareScreen> {
  CameraController? _cameraController;
  bool _isCameraReady = false;
  bool _isProcessing = false;

  String _status = "Chuẩn bị camera...";
  XFile? _capturedPhoto;

  double? _score;
  bool? _matched;
  bool _isOutsideHours = false;
  bool _isPending = false;   // Chờ admin phê duyệt
  String? _debugMessage;

  @override
  void initState() {
    super.initState();
    _initCamera();
  }

  Future<void> _initCamera() async {
    try {
      final cameras = await availableCameras();
      final frontCamera = cameras.firstWhere(
        (c) => c.lensDirection == CameraLensDirection.front,
        orElse: () => cameras.first,
      );

      _cameraController = CameraController(
        frontCamera,
        ResolutionPreset.high, 
        enableAudio: false,
        imageFormatGroup: Platform.isAndroid 
            ? ImageFormatGroup.jpeg 
            : ImageFormatGroup.bgra8888,
      );

      await _cameraController!.initialize();
      if (!mounted) return;

      setState(() {
        _isCameraReady = true;
        _status = "Sẵn sàng chụp (CCCD: ${widget.cccd})";
      });
    } catch (e) {
      setState(() => _status = "Lỗi Camera: $e");
    }
  }

  Future<Uint8List> _compressImage(Uint8List bytes) async {
    try {
      img.Image? image = img.decodeImage(bytes);
      if (image == null) return bytes;
      img.Image resized = img.copyResize(image, width: 480);
      return Uint8List.fromList(img.encodeJpg(resized, quality: 70));
    } catch (e) {
      print("Lỗi nén ảnh: $e");
      return bytes;
    }
  }

  Future<void> _captureAndCompare() async {
    if (!_isCameraReady || _cameraController == null) return;

    setState(() {
      _capturedPhoto = null;
      _isProcessing = true;
      _isOutsideHours = false;
      _isPending = false;
      _status = "Đang xử lý & Nén ảnh...";
    });

    try {
      final photo = await _cameraController!.takePicture();
      final originalBytes = await photo.readAsBytes();
      final compressedSelfieBytes = await _compressImage(originalBytes);

      _capturedPhoto = photo;

      final result = await FaceCompareService.compareFaces(
        chipImage: widget.chipImageBytes,
        selfieImage: compressedSelfieBytes, 
        cccd: widget.cccd,          
        fullname: widget.fullname,
        remoteUnlock: widget.remoteUnlock,
      );

      if (result == null) {
        setState(() {
          _status = "Không kết nối được Server hoặc lỗi mạng";
          _capturedPhoto = null;
        });
      } else if (result['pending'] == true) {
        // PENDING_APPROVAL: mặt đã xác minh, chờ admin mở cửa
        setState(() {
          _isPending = true;
          _status = "✓ Khuôn mặt đã xác minh\nĐang chờ nhân viên quản lý phê duyệt...";
        });
        // Lắng nghe lệnh OPEN_DOOR từ MQTT khi admin bấm nút trên web
        MqttClientService.updateCallback((data) {
          if (data['action'] == 'OPEN_DOOR' && mounted) {
            Navigator.pushReplacement(
              context,
              MaterialPageRoute(builder: (_) => DoorOpenScreen(label: widget.fullname)),
            );
          }
        });
      } else {
        final matched = result['matched'] ?? false;
        final score = (result['score'] ?? 0).toDouble();
        final isMatch = matched;
        final String statusCode = (result['status'] ?? result['reason'] ?? '').toString();
        final bool isOutsideHours = statusCode == 'OUTSIDE_HOURS';

        setState(() {
          _matched = isMatch;
          _isOutsideHours = isOutsideHours;
          _score = score;
          _debugMessage = null;
          _status = isOutsideHours
              ? "⚠ NGOÀI GIỜ LÀM VIỆC CHO PHÉP"
              : isMatch
                  ? "TRÙNG KHỚP"
                  : "KHÔNG KHỚP";

          if (!isMatch && !isOutsideHours) {
            _capturedPhoto = null;
          }
        });

        if (isMatch) {
          final deviceCode = await ConfigService.getDeviceCode();

          MqttClientService.publish(
            'devices/events',
            jsonEncode({
              "action": "FACE_MATCHED",
              "deviceCode": deviceCode,
              "cccd": widget.cccd,
              "name": widget.fullname,
              "timestamp": DateTime.now().toIso8601String()
            })
          );

          if (mounted) {
            await Navigator.pushReplacement(
              context,
              MaterialPageRoute(
                builder: (_) => DoorOpenScreen(label: widget.fullname),
              ),
            );
          }
        }
      }
    } catch (e) {
      setState(() {
        _status = "Lỗi App: $e";
        _capturedPhoto = null;
      });
    } finally {
      setState(() => _isProcessing = false);
    }
  }

  @override
  void dispose() {
    _cameraController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("So sánh khuôn mặt")),
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(8),
              child: Text(
                _status,
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: _matched == true ? 24 : 16,
                  fontWeight: FontWeight.bold,
                  color: _matched == true ? Colors.green : Colors.blue,
                ),
              ),
            ),

            Expanded(
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      children: [
                        const Text("Ảnh CHIP",
                            style: TextStyle(fontWeight: FontWeight.bold)),
                        const SizedBox(height: 5),
                        Expanded(
                          child: Image.memory(
                            widget.chipImageBytes,
                            fit: BoxFit.contain,
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(width: 5),

                  Expanded(
                    child: Column(
                      children: [
                        const Text("Ảnh SELFIE",
                            style: TextStyle(fontWeight: FontWeight.bold)),
                        const SizedBox(height: 5),
                        Expanded(
                          child: _capturedPhoto != null
                              ? Image.file(
                                  File(_capturedPhoto!.path),
                                  fit: BoxFit.contain,
                                )
                              : (_isCameraReady
                                  ? CameraPreview(_cameraController!)
                                  : const Center(
                                      child: CircularProgressIndicator())),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 10),
            
            if (_isPending) ...[
              Container(
                margin: const EdgeInsets.symmetric(horizontal: 16),
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.blue.shade50,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.blue.shade300),
                ),
                child: const Column(
                  children: [
                    CircularProgressIndicator(),
                    SizedBox(height: 12),
                    Text(
                      "Đang chờ phê duyệt",
                      style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold, color: Colors.blue),
                    ),
                    SizedBox(height: 6),
                    Text(
                      "Khuôn mặt đã được xác minh.\nVui lòng đứng chờ nhân viên quản lý mở cửa.",
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.blueGrey),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 10),
            ],
            if (_matched == false && !_isOutsideHours && !_isPending) ...[
              const Text(
                "Khuôn mặt không khớp! Vui lòng thử lại.",
                style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 10),
            ],
            if (_isOutsideHours) ...[
              const Text(
                "Khuôn mặt hợp lệ nhưng ngoài khung giờ cho phép.",
                style: TextStyle(color: Colors.orange, fontWeight: FontWeight.bold),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 10),
            ],
            
            if (!_isPending)
              ElevatedButton(
                onPressed: _isProcessing ? null : _captureAndCompare,
                style: ElevatedButton.styleFrom(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 50, vertical: 15),
                  backgroundColor: _matched == false ? Colors.orange : null,
                ),
                child: Text(
                  _isProcessing
                    ? "Đang gửi..."
                    : (_matched == false && !_isOutsideHours ? "CHỤP LẠI" : "CHỤP & SO SÁNH"),
                  style: const TextStyle(fontSize: 18),
                ),
              ),

            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }
}