import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:google_mlkit_barcode_scanning/google_mlkit_barcode_scanning.dart';
import 'package:camera/camera.dart';
import 'package:google_mlkit_commons/google_mlkit_commons.dart';

class QrScanner extends StatefulWidget {
  final Function(String) onCanDetected;

  const QrScanner({Key? key, required this.onCanDetected}) : super(key: key);

  @override
  _QrScannerState createState() => _QrScannerState();
}

class _QrScannerState extends State<QrScanner> {
  final BarcodeScanner _barcodeScanner = BarcodeScanner();
  CameraController? _cameraController;
  List<CameraDescription>? _cameras;
  bool _isCameraInitialized = false;
  bool _isProcessing = false;
  String _scanResult = '';

  @override
  void initState() {
    super.initState();
    _initializeCamera();
  }

  Future<void> _initializeCamera() async {
    try {
      _cameras = await availableCameras();
      if (_cameras != null && _cameras!.isNotEmpty) {
        _cameraController = CameraController(
          _cameras![0],
          ResolutionPreset.high,
          enableAudio: false,
        );
        await _cameraController!.initialize();
        _cameraController!.startImageStream(_processImage);
        setState(() {
          _isCameraInitialized = true;
        });
      }
    } catch (e) {
      print('Error initializing camera: $e');
    }
  }

  @override
  void dispose() {
    _barcodeScanner.close();
    _cameraController?.dispose();
    super.dispose();
  }

  Future<void> _processImage(CameraImage image) async {
    if (_isProcessing) return;
    _isProcessing = true;

    try {
      Uint8List bytes;
      InputImageFormat inputImageFormat;
      int bytesPerRow;

      if (image.format.group == ImageFormatGroup.yuv420) {
        final planeY = image.planes[0];
        final planeU = image.planes[1];
        final planeV = image.planes[2];

        final width = image.width;
        final height = image.height;
        final int ySize = planeY.bytes.length;
        final int uvSize = planeU.bytes.length;

        final Uint8List nv21 = Uint8List(ySize + uvSize * 2);
        nv21.setRange(0, ySize, planeY.bytes);

        int uvIndex = ySize;
        for (int i = 0; i < uvSize; i++) {
          nv21[uvIndex++] = planeV.bytes[i];
          nv21[uvIndex++] = planeU.bytes[i];
        }

        bytes = nv21;
        inputImageFormat = InputImageFormat.nv21;
        bytesPerRow = planeY.bytesPerRow;
      } else {
        bytes = image.planes[0].bytes;
        inputImageFormat = InputImageFormat.bgra8888;
        bytesPerRow = image.planes[0].bytesPerRow;
      }

      final inputImage = InputImage.fromBytes(
        bytes: bytes,
        metadata: InputImageMetadata(
          size: Size(image.width.toDouble(), image.height.toDouble()),
          rotation: InputImageRotation.rotation0deg,
          format: inputImageFormat,
          bytesPerRow: bytesPerRow,
        ),
      );

      final barcodes = await _barcodeScanner.processImage(inputImage);
      for (final barcode in barcodes) {
        if (barcode.rawValue != null && barcode.rawValue!.isNotEmpty) {
          final qrData = barcode.rawValue!;
          print("QR Data: $qrData");
          final parts = qrData.split('|');
          if (parts.isNotEmpty) {
            final idNumber = parts[0].replaceAll(RegExp(r'[^\d]'), '');
            if (idNumber.length >= 6) {
              final can = idNumber.substring(idNumber.length - 6);
              if (RegExp(r'^\d{6}$').hasMatch(can)) {
                setState(() => _scanResult = 'CAN: $can');
                await _cameraController?.stopImageStream();
                widget.onCanDetected(can);
                return;
              }
            }
          }
          final match = RegExp(r'\d{6}').firstMatch(qrData);
          if (match != null) {
            final can = match.group(0)!;
            setState(() => _scanResult = 'CAN: $can');
            await _cameraController?.stopImageStream();
            widget.onCanDetected(can);
            return;
          }
        }
      }
    } catch (e) {
      print('Error processing image: $e');
      setState(() => _scanResult = 'Error: $e');
    } finally {
      _isProcessing = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Quét mã QR CCCD'),
        backgroundColor: Colors.blue,
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Expanded(
            child: _isCameraInitialized
                ? Stack(
                    children: [
                      CameraPreview(_cameraController!),
                      Positioned.fill(
                        child: CustomPaint(painter: QrScannerOverlay()),
                      ),
                      Positioned(
                        top: 20,
                        left: 0,
                        right: 0,
                        child: Container(
                          color: Colors.black54,
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            children: [
                              const Text(
                                'Đặt mã QR trên CCCD vào khung quét',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: 16,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              if (_scanResult.isNotEmpty) ...[
                                const SizedBox(height: 8),
                                Text(
                                  _scanResult,
                                  style: TextStyle(
                                    color: _scanResult.startsWith('Error')
                                        ? Colors.red
                                        : Colors.green,
                                    fontSize: 14,
                                  ),
                                ),
                              ]
                            ],
                          ),
                        ),
                      ),
                    ],
                  )
                : const Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        CircularProgressIndicator(),
                        SizedBox(height: 16),
                        Text('Đang khởi tạo camera...'),
                      ],
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}

// Overlay vẽ khung quét QR
class QrScannerOverlay extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final Paint borderPaint = Paint()
      ..color = Colors.green
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3;

    final double scannerSize = size.width * 0.7;
    final Rect rect = Rect.fromCenter(
      center: Offset(size.width / 2, size.height / 2),
      width: scannerSize,
      height: scannerSize,
    );

    final Paint overlayPaint = Paint()
      ..color = Colors.black54
      ..style = PaintingStyle.fill;

    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height), overlayPaint);
    canvas.drawRect(rect, Paint()..blendMode = BlendMode.clear);
    canvas.drawRect(rect, borderPaint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
