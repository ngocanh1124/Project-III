import 'dart:typed_data';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'services/face_compare_service.dart';

class FaceCompareScreen extends StatefulWidget {
  final Uint8List chipImageBytes;
  final String cccd;
  final String fullname;
  const FaceCompareScreen({
    super.key, 
    required this.chipImageBytes,
    required this.cccd,     
    required this.fullname, 
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

  Future<void> _captureAndCompare() async {
    if (!_isCameraReady || _cameraController == null) return;

    setState(() {
      _isProcessing = true;
      _status = "Đang xử lý...";
    });

    try {
      final photo = await _cameraController!.takePicture();
      _capturedPhoto = photo;

      final selfieBytes = await photo.readAsBytes();
      final result = await FaceCompareService.compareFaces(
        chipImage: widget.chipImageBytes,
        selfieImage: selfieBytes,
        cccd: widget.cccd,          
        fullname: widget.fullname, 
      );

      if (result == null) {
        setState(() => _status = "Không kết nối được Server hoặc lỗi mạng");
      } else {
        final matched = result['matched'] ?? false;
        final score = (result['score'] ?? 0).toDouble();
        final isMatch = score > 0.6; 
        setState(() {
          _matched = isMatch;
          _score = score;
          _status = isMatch
              ? "TRÙNG KHỚP (${(score * 100).toStringAsFixed(1)}%)"
              : "KHÔNG KHỚP (${(score * 100).toStringAsFixed(1)}%)";
        });
      }
    } catch (e) {
      setState(() => _status = "Lỗi App: $e");
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
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                  color: Colors.blue,
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
            
            ElevatedButton(
              onPressed: _isProcessing ? null : _captureAndCompare,
              style: ElevatedButton.styleFrom(
                padding:
                    const EdgeInsets.symmetric(horizontal: 50, vertical: 15),
              ),
              child: Text(
                _isProcessing ? "Đang gửi..." : "CHỤP & SO SÁNH",
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