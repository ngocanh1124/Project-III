import 'dart:typed_data';
import 'package:flutter/services.dart';
import 'package:cccd_vietnam/dmrtd.dart';
import 'package:cccd_vietnam/extensions.dart';
import 'package:cccd_vietnam/src/proto/can_key.dart';
import 'package:logging/logging.dart';

class NfcChipService {
  static final _log = Logger("NfcChipService");

  /// Read CCCD data from NFC chip
  /// Tries PACE (via CAN) first, then fallback to BAC (via DocNum/MRZ)
  static Future<Map<String, dynamic>?> readChipData({
    required String can,
    String? docNumber,
    String? dob,
    String? doe,
    Function(String status)? onProgress,
  }) async {
    final nfc = NfcProvider();
    try {
      // Connect to NFC tag
      onProgress?.call('Đang kết nối NFC...\nĐặt thẻ vào sau máy');
      await nfc.connect(iosAlertMessage: 'Đặt thẻ CCCD lên mặt sau điện thoại');
      _log.info('NFC: connected');

      final passport = Passport(nfc);
      bool sessionEstablished = false;

      // 1. TRY PACE (Preferred for newer Vietnam CCCD cards)
      if (can.length == 6) {
        try {
          onProgress?.call('Đang xác thực PACE (Dùng mã CAN)...');
          _log.info('NFC: Attempting PACE session with CAN');
          
          final efCardAccess = await passport.readEfCardAccess();
          final canKey = CanKey(can);
          
          await passport.startSessionPACE(canKey, efCardAccess);
          sessionEstablished = true;
          _log.info('NFC: Session established via PACE');
        } catch (e) {
          _log.warning('PACE session failed: ${e.toString()}. Falling back to BAC (if info available)...');
        }
      }

      // 2. TRY BAC (Fallback via MRZ) - Only if session not established and we have necessary info
      if (!sessionEstablished && docNumber != null && dob != null && doe != null) {
        try {
          final dobDt = _parseMrzDateToDateTime(dob);
          final doeDt = _parseMrzDateToDateTime(doe);
          
          if (dobDt != null && doeDt != null) {
            onProgress?.call('Đang xác thực BAC (Dùng số CCCD)...');
            _log.info('NFC: Attempting BAC session with MRZ');
            final dbaKey = DBAKey(docNumber, dobDt, doeDt);
            await passport.startSession(dbaKey);
            sessionEstablished = true;
            _log.info('NFC: Session established via BAC');
          }
        } catch (e) {
          _log.severe('BAC session also failed: ${e.toString()}');
        }
      }

      if (!sessionEstablished) {
        await nfc.disconnect();
        return {
          'error': 'Không thể tạo phiên bảo mật với chip.\n'
                   'Lưu ý:\n'
                   '- Mã CAN (6 số) quét từ QR có thể bị sai\n'
                   '- Giữ thẻ cố định khi nghe tiếng "Bíp" hoặc thẻ rung'
        };
      }

      // Read DG1 (MRZ data)
      _log.info('NFC: Reading DG1...');
      onProgress?.call('Đang đọc thông tin cá nhân (DG1)...');
      final dg1 = await passport.readEfDG1();

      // Read DG2 (facial image)
      _log.info('NFC: Reading DG2 (facial image)...');
      onProgress?.call('Đang đọc ảnh chân dung (DG2)...\nVui lòng giữ chắc thẻ!');
      final dg2 = await passport.readEfDG2();

      final mrz = dg1.mrz;
      final cccdNumber = mrz.documentNumber;
      final fullName   = ('${mrz.firstName} ${mrz.lastName}').trim();
      final nationality = mrz.nationality;

      // MRZ object already provides parsed DateTime
      final dobFromChip = mrz.dateOfBirth;
      final doeFromChip = mrz.dateOfExpiry;

      // Extract photo bytes
      final photoBytes = dg2.imageData;

      await nfc.disconnect();
      _log.info('NFC: read OK - CCCD=$cccdNumber name=$fullName');

      return {
        'cccdNumber':  cccdNumber,
        'fullName':    fullName,
        'nationality': nationality,
        'photoBytes':  photoBytes,
        'dateOfBirth': dobFromChip,
        'dateOfExpiry': doeFromChip,
      };
    } on PlatformException catch (e) {
      _log.severe('NFC Platform Error: ${e.message}');
      await nfc.disconnect().catchError((_) {});
      String msg = e.message ?? 'Unknown error';
      if (e.code == '408') {
        msg = 'Hết thời gian chờ kết nối NFC (408). Vui lòng thử lại.';
      }
      return {'error': 'Lỗi thiết bị: $msg', 'code': e.code};
    } catch (e) {
      _log.severe('NFC Read Error: $e');
      await nfc.disconnect().catchError((_) {});
      
      String errorMsg = 'Lỗi đọc Chip: $e';
      if (e.toString().contains('408')) {
        errorMsg = 'Hết thời gian chờ kết nối (Lỗi 408).\n\nMẸO: Hãy áp sát thẻ CCCD vào mặt lưng máy (thường ở cạnh camera) ngay khi cửa sổ hiện lên và GIỮ NGUYÊN cho đến khi xong.';
      }
      return {'error': errorMsg};
    }
  }

  /// Parse YYMMDD string to DateTime (returns null on failure)
  static DateTime? _parseMrzDateToDateTime(String s) {
    if (s.length != 6) return null;
    try {
      int y = int.parse(s.substring(0, 2));
      int m = int.parse(s.substring(2, 4));
      int d = int.parse(s.substring(4, 6));
      y += (y <= 30) ? 2000 : 1900;
      return DateTime(y, m, d);
    } catch (_) {
      return null;
    }
  }

  /// Validate CAN format: must be exactly 6 digits
  static bool validateCAN(String can) => RegExp(r'^\d{6}$').hasMatch(can);
}
