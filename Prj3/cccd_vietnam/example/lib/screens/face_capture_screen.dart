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
  
  String _statusMessage = 'Hướng mặt về camera';

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
      CameraDescription camera = cameras.firstWhere(
        (c) => c.lensDirection == CameraLensDirection.front,
        orElse: () => cameras.first,
      );

      _cameraController = CameraController(
        camera,
        ResolutionPreset.medium, // Giảm độ phân giải để tăng tốc độ nhận diện và giảm dung lượng upload
        enableAudio: false,
      );

      await _cameraController!.initialize();
      if (!mounted) return;

      await _cameraController!.startImageStream(_processFrame);

      setState(() {
        _isCameraInitialized = true;
      });
    } catch (e) {
      _log.severe('Camera init error: $e');
      if (mounted) {
        setState(() => _statusMessage = 'Lỗi Camera: $e');
      }
    }
  }

  Future<void> _processFrame(CameraImage image) async {
    try {
      final inputImage = InputImage.fromBytes(
        bytes: image.planes[0].bytes,
        metadata: InputImageMetadata(
          size: Size(image.width.toDouble(), image.height.toDouble()),
          rotation: InputImageRotation.rotation270deg,
          format: InputImageFormat.nv21,
          bytesPerRow: image.planes[0].bytesPerRow,
        ),
      );

      final faces = await _faceDetector.processImage(inputImage);

      if (mounted) {
        setState(() {
          _faceDetected = faces.isNotEmpty;
          if (faces.isNotEmpty) {
            _detectedFace = faces.first;
            _statusMessage = 'Đã quét thấy mặt';
          } else {
            _statusMessage = 'Đang chờ...';
          }
        });
      }
    } catch (e) {}
  }

  Future<void> _capturePhoto() async {
    if (_isCapturing) return;

    setState(() => _isCapturing = true);

    try {
      if (_cameraController!.value.isStreamingImages) {
        await _cameraController!.stopImageStream();
      }

      final photo = await _cameraController!.takePicture();
      final photoBytes = await photo.readAsBytes();

      if (!mounted) return;

      widget.flowState.selfiePhotoBytes = photoBytes;
      widget.onFaceCaptured(photoBytes);

      Navigator.pop(context, true);
    } catch (e) {
      _log.severe('Capture error: $e');
      if (mounted) {
        setState(() => _isCapturing = false);
        _cameraController!.startImageStream(_processFrame);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_isCameraInitialized) {
      return Scaffold(
        appBar: AppBar(title: const Text('Xác thực khuôn mặt')),
        body: Center(child: Text(_statusMessage)),
      );
    }

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: const Text('Quét khuôn mặt'),
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        elevation: 0,
      ),
      body: Column(
        children: [
          Expanded(
            child: Row(
              children: [
                Expanded(
                  flex: 2,
                  child: Container(
                    decoration: BoxDecoration(border: Border.all(color: Colors.blue, width: 2)),
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        CameraPreview(_cameraController!),
                        if (_faceDetected)
                          Center(
                            child: Container(
                              width: 140,
                              height: 180,
                              decoration: BoxDecoration(
                                border: Border.all(color: Colors.green, width: 2),
                                borderRadius: BorderRadius.circular(100),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
                Expanded(
                  flex: 1,
                  child: Container(
                    color: Colors.grey[900],
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        if (widget.flowState.chipPhotoBytes != null)
                          Image.memory(widget.flowState.chipPhotoBytes!, width: 100, height: 130, fit: BoxFit.cover)
                        else
                          const Icon(Icons.person, size: 80, color: Colors.white24),
                        const SizedBox(height: 10),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                          child: Text(
                            widget.flowState.fullName ?? '', 
                            style: const TextStyle(color: Colors.white70, fontSize: 11),
                            textAlign: TextAlign.center,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.all(32),
            color: const Color(0xFF1A1A1A),
            child: Column(
              children: [
                Text(_statusMessage, style: const TextStyle(color: Colors.white, fontSize: 16)),
                const SizedBox(height: 24),
                if (_isCapturing)
                  const CircularProgressIndicator()
                else
                  IconButton(
                    icon: const Icon(Icons.camera_alt, color: Colors.white, size: 48),
                    onPressed: _capturePhoto,
                    style: IconButton.styleFrom(
                      backgroundColor: _faceDetected ? Colors.green : Colors.blue,
                      padding: const EdgeInsets.all(16),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
