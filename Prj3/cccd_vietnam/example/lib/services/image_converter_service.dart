import 'dart:typed_data';
import 'package:image/image.dart' as img;

class ImageConverterService {
  /// Giải mã ảnh từ bytes
  static Future<Uint8List?> decodeChipImage(Uint8List imageBytes) async {
    if (imageBytes.isEmpty) return null;

    try {
      if (_isJpeg2000(imageBytes)) {
        final decoded = img.decodeJpg(imageBytes);
        if (decoded != null) {
          return Uint8List.fromList(img.encodeJpg(decoded));
        }
        final image = img.decodeImage(imageBytes);
        if (image != null) {
          return Uint8List.fromList(img.encodeJpg(image));
        }
      } else {
        final image = img.decodeImage(imageBytes);
        if (image != null) {
          return Uint8List.fromList(img.encodeJpg(image));
        }
      }
    } catch (e) {
      print('Error decoding image: $e');
    }

    return null;
  }

  static bool _isJpeg2000(Uint8List bytes) {
    if (bytes.length < 8) return false;
    return bytes[0] == 0xFF && bytes[1] == 0x4F && bytes[2] == 0xFF && bytes[3] == 0x51;
  }
  static Uint8List? extractImageFromTlvDg2(Uint8List dg2Bytes) {
    try {
      if (dg2Bytes.isEmpty) return null;
      final jp2Start = _findJp2Start(dg2Bytes);
      if (jp2Start >= 0) {
        return dg2Bytes.sublist(jp2Start);
      }
    } catch (_) {}
    
    return null;
  }

  static int _findJp2Start(Uint8List bytes) {
    for (int i = 0; i < bytes.length - 3; i++) {
      if (bytes[i] == 0xFF && bytes[i + 1] == 0xD8 && bytes[i + 2] == 0xFF) {
        return i;
      }
      if (i < bytes.length - 7 &&
          bytes[i] == 0x00 &&
          bytes[i + 1] == 0x00 &&
          bytes[i + 2] == 0x00 &&
          bytes[i + 3] == 0x0C &&
          bytes[i + 4] == 0x6A &&
          bytes[i + 5] == 0x50 &&
          bytes[i + 6] == 0x20 &&
          bytes[i + 7] == 0x20) {
        return i;
      }
    }
    return -1;
  }
}
