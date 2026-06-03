import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:flutter/services.dart';
import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';
import 'package:image/image.dart' as img;
import 'package:onnxruntime/onnxruntime.dart';

class FaceEmbeddingService {
  static OrtSession? _session;

  static final _faceDetector = FaceDetector(
    options: FaceDetectorOptions(
      performanceMode: FaceDetectorMode.accurate, // chính xác hơn
      enableLandmarks: true,   // dùng landmarks để crop chuẩn hơn
      enableContours: false,
      enableClassification: false,
      minFaceSize: 0.15,       // bỏ qua khuôn mặt quá nhỏ/xa
    ),
  );

  static Future<List<double>?> getEmbeddingFromFile(String filePath) async {
    await _ensureSession();

    final croppedBytes = await _cropFaceFromFile(filePath);
    if (croppedBytes == null) return null;

    return _runEmbedding(croppedBytes);
  }

  /// This writes to a temp file and runs the same pipeline as [getEmbeddingFromFile].
  static Future<List<double>?> getEmbeddingFromBytes(Uint8List bytes) async {
    final tempDir = Directory.systemTemp.createTempSync('insightface_');
    final tmp = File('${tempDir.path}/image.jpg');
    await tmp.writeAsBytes(bytes, flush: true);
    final emb = await getEmbeddingFromFile(tmp.path);
    try {
      await tempDir.delete(recursive: true);
    } catch (_) {}
    return emb;
  }

  static Future<void> _ensureSession() async {
    if (_session != null) return;

    final modelData = await rootBundle
        .load('assets/models/buffalo_sc/w600k_mbf.onnx');
    final options = OrtSessionOptions();

    _session = OrtSession.fromBuffer(modelData.buffer.asUint8List(), options);
  }

  static Future<Uint8List?> _cropFaceFromFile(String filePath) async {
    final inputImage = InputImage.fromFilePath(filePath);
    final faces = await _faceDetector.processImage(inputImage);
    if (faces.isEmpty) return null;
    
    final face = faces.reduce((a, b) {
      final aArea = a.boundingBox.width * a.boundingBox.height;
      final bArea = b.boundingBox.width * b.boundingBox.height;
      return aArea >= bArea ? a : b;
    });

    final bytes = await File(filePath).readAsBytes();
    final image = img.decodeImage(bytes);
    if (image == null) return null;

    final rect = face.boundingBox;

    final padding = (max(rect.width, rect.height) * 0.25).round();
    final left = max(0, rect.left.round() - padding);
    final top = max(0, rect.top.round() - padding);
    final right = min(image.width, rect.right.round() + padding);
    final bottom = min(image.height, rect.bottom.round() + padding);
    final width = max(1, right - left);
    final height = max(1, bottom - top);

    final cropped = img.copyCrop(image, x: left, y: top, width: width, height: height);
    final resized = img.copyResize(cropped, width: 112, height: 112);
    return Uint8List.fromList(img.encodeJpg(resized));
  }

  static List<double>? _toFloatList(img.Image image) {
    final width = image.width;
    final height = image.height;
    if (width != 112 || height != 112) return null;
    final floatData = Float32List(1 * 3 * width * height);
    int idx = 0;
    for (var c = 0; c < 3; ++c) {
      for (var y = 0; y < height; ++y) {
        for (var x = 0; x < width; ++x) {
          final pixel = image.getPixel(x, y);
          double value;
          if (c == 0) {
            value = pixel.r / 255.0;
          } else if (c == 1) {
            value = pixel.g / 255.0;
          } else {
            value = pixel.b / 255.0;
          }
          // Normalize to [-1, 1].
          floatData[idx++] = (value - 0.5) / 0.5;
        }
      }
    }

    return floatData;
  }

  static Future<List<double>?> _runEmbedding(Uint8List bytes) async {
    if (_session == null) return null;

    final image = img.decodeImage(bytes);
    if (image == null) return null;

    final floatData = _toFloatList(image);
    if (floatData == null) return null;

    final inputTensor = OrtValueTensor.createTensorWithDataList(
      floatData,
      [1, 3, 112, 112],
    );

    final runOptions = OrtRunOptions();

    final outputs = _session!.run(runOptions, {'input.1': inputTensor});
    if (outputs.isEmpty || outputs[0] == null) return null;

    final output = outputs[0] as OrtValueTensor;
    final raw = output.value;

    List<double> embedding;
    if (raw is List<double>) {
      embedding = raw;
    } else if (raw is List) {
      embedding = raw
          .expand((e) => e is List ? e : [e])
          .map((e) => (e as num).toDouble())
          .toList();
    } else {
      return null;
    }

    final norm = sqrt(embedding.fold<double>(0, (sum, v) => sum + v * v));
    if (norm > 0) {
      return embedding.map((e) => e / norm).toList();
    }
    return embedding;
  }

  static double cosineSimilarity(List<double> a, List<double> b) {
    final len = min(a.length, b.length);
    var dot = 0.0;
    var normA = 0.0;
    var normB = 0.0;
    for (var i = 0; i < len; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    if (normA == 0 || normB == 0) return 0.0;
    return dot / (sqrt(normA) * sqrt(normB));
  }
}
