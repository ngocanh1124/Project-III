import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';
import '../models/attendance_flow_state.dart';
import '../services/face_embedding_service.dart';
import 'package:logging/logging.dart';

class FaceCaptureScreen extends StatefulWidget {
  final AttendanceFlowState flowState;
  final Function(Uint8List selfieBytes) onFaceCaptured;

  const FaceCaptureScreen({
    Key? key,
    required this.flowState,
    required this.onFaceCaptured,
  }) : super(key: key);

  @override
  State<FaceCaptureScreen> createState() => _FaceCaptureScreenState();
}

class _FaceCaptureScreenState extends State<FaceCaptureScreen> {
  final _log = Logger('FaceCaptureScreen');
  
  CameraController? _cameraController;
  final _faceDetector = FaceDetector(options: FaceDetectorOptions());
  
  bool _isCameraInitialized = false;
  bool _isCapturing = false;
  bool _faceDetected = false;
  Face? _detectedFace;
  
  String _statusMessage = 'Khởi động camera...';
  int _retryCount = 0;

  @override
  void initState() {
    super.initState();
    _initCamera();
  }

  @override
  void dispose() {
    _cameraController?.dispose();
    _faceDetector.close();
    super.dispose();
  }

  Future<void> _initCamera() async {
    try {
      final cameras = await availableCameras();
      
      // Prefer front camera
      CameraDescription camera = cameras.firstWhere(
        (c) => c.lensDirection == CameraLensDirection.front,
        orElse: () => cameras.first,
      );

      _cameraController = CameraController(
        camera,
        ResolutionPreset.high,
        enableAudio: false,
      );

      await _cameraController!.initialize();

      if (!mounted) return;

      // Start image streaming for face detection
      await _cameraController!.startImageStream(_processFrame);

      setState(() {
        _isCameraInitialized = true;
        _statusMessage = 'Hướng mặt về camera';
      });
    } catch (e) {
      _log.severe('Camera init error: $e');
      if (mounted) {
        setState(() => _statusMessage = 'Lỗi Camera: $e');
      }
    }
  }

  Future<void> _processFrame(CameraImage image) async {
    if (image.format.group != ImageFormatGroup.bgra8888 &&
        image.format.group != ImageFormatGroup.yuv420) {
      return;
    }

    try {
      final InputImageRotation rotation = InputImageRotation.rotation0deg;

      final InputImageFormat format =
          image.format.group == ImageFormatGroup.bgra8888
              ? InputImageFormat.bgra8888
              : InputImageFormat.nv21;

      final inputImage = InputImage.fromBytes(
        bytes: image.planes[0].bytes,
        metadata: InputImageMetadata(
          size: Size(image.width.toDouble(), image.height.toDouble()),
          rotation: rotation,
          format: format,
          bytesPerRow: image.planes[0].bytesPerRow,
        ),
      );

      final faces = await _faceDetector.processImage(inputImage);

      if (mounted) {
        setState(() {
          _faceDetected = faces.isNotEmpty;
          if (faces.isNotEmpty) {
            _detectedFace = faces.first;
            _statusMessage = 'Mặt được phát hiện - Chụp ảnh';
          } else {
            _statusMessage = 'Không phát hiện mặt - Hướng mặt lại';
          }
        });
      }
    } catch (e) {
      _log.warning('Frame processing error: $e');
    }
  }

  Future<void> _capturePhoto() async {
    if (!_faceDetected || _isCapturing) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Chưa phát hiện mặt hoặc đang xử lý')),
      );
      return;
    }

    setState(() => _isCapturing = true);

    try {
      // Pause frame processing
      await _cameraController!.stopImageStream();

      // Take photo
      final photo = await _cameraController!.takePicture();
      final photoBytes = await photo.readAsBytes();

      if (!mounted) return;

      // Validate face in captured photo
      final embedding = await FaceEmbeddingService.getEmbeddingFromBytes(photoBytes);
      
      // If no embedding, retry
      if (embedding == null) {
        _showRetryDialog('Không phát hiện được mặt rõ ràng.\nVui lòng thử lại.');
        await _cameraController!.startImageStream(_processFrame);
        setState(() => _isCapturing = false);
        return;
      }

      // Store and callback
      widget.flowState.selfiePhotoBytes = photoBytes;
      widget.onFaceCaptured(photoBytes);

      // Navigate to comparison
      if (mounted) {
        Navigator.pop(context, true);
      }
    } catch (e) {
      _log.severe('Capture error: $e');
      _showRetryDialog('Lỗi chụp ảnh: $e');
      if (mounted) {
        setState(() => _isCapturing = false);
      }
    }
  }

  void _showRetryDialog(String message) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Thử lại'),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (!_isCameraInitialized) {
      return Scaffold(
        appBar: AppBar(title: const Text('Chụp Ảnh Mặt')),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const CircularProgressIndicator(),
              const SizedBox(height: 16),
              Text(_statusMessage),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Chụp Ảnh Mặt'),
        elevation: 0,
      ),
      body: Stack(
        children: [
          // Camera preview
          CameraPreview(_cameraController!),

          // Face detection overlay
          if (_faceDetected && _detectedFace != null)
            Positioned(
              left: _detectedFace!.boundingBox.left,
              top: _detectedFace!.boundingBox.top,
              width: _detectedFace!.boundingBox.width,
              height: _detectedFace!.boundingBox.height,
              child: Container(
                decoration: BoxDecoration(
                  border: Border.all(color: Colors.green, width: 2),
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
            ),

          // Controls
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: Container(
              decoration: const BoxDecoration(
                color: Colors.black54,
                borderRadius: BorderRadius.only(
                  topLeft: Radius.circular(20),
                  topRight: Radius.circular(20),
                ),
              ),
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Status
                  Text(
                    _statusMessage,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 24),

                  // Capture button
                  Center(
                    child: _isCapturing
                        ? const SizedBox(
                            width: 60,
                            height: 60,
                            child: CircularProgressIndicator(
                              strokeWidth: 3,
                              valueColor: AlwaysStoppedAnimation<Color>(
                                Colors.white,
                              ),
                            ),
                          )
                        : GestureDetector(
                            onTap: _faceDetected ? _capturePhoto : null,
                            child: Container(
                              width: 60,
                              height: 60,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: _faceDetected
                                    ? Colors.green
                                    : Colors.grey,
                                border: Border.all(
                                  color: Colors.white,
                                  width: 3,
                                ),
                              ),
                              child: const Icon(
                                Icons.camera_alt,
                                color: Colors.white,
                                size: 28,
                              ),
                            ),
                          ),
                  ),
                  const SizedBox(height: 16),

                  // Instructions
                  const Text(
                    'Căn mặt vào giữa màn hình\nRồi nhấn nút để chụp',
                    style: TextStyle(
                      color: Colors.white70,
                      fontSize: 13,
                    ),
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
