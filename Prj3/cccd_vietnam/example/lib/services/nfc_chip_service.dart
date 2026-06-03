import 'dart:typed_data';
import 'package:flutter/services.dart';
import 'package:cccd_vietnam/dmrtd.dart';
import 'package:cccd_vietnam/extensions.dart';
import 'package:logging/logging.dart';

class NfcChipService {
  static final _log = Logger("NfcChipService");

  /// Read CCCD data from NFC chip using BAC (Basic Access Control)
  /// [can]       - Card Access Number (6 digits, printed on card)
  /// [docNumber] - Document/CCCD number
  /// [dob]       - Date of birth YYMMDD
  /// [doe]       - Date of expiry YYMMDD
  /// Returns map with: cccdNumber, fullName, nationality, photoBytes, dob, doe
  static Future<Map<String, dynamic>?> readChipData({
    required String can,
    required String docNumber,
    required String dob,
    required String doe,
  }) async {
    final nfc = NfcProvider();
    try {
      // Connect to NFC tag
      await nfc.connect(iosAlertMessage: 'Dat the CCCD len mat sau dien thoai');
      _log.info('NFC: connected');

      // Build DBA key from document number + dob + doe
      final dobDt = _parseMrzDateToDateTime(dob);
      final doeDt = _parseMrzDateToDateTime(doe);
      if (dobDt == null || doeDt == null) {
        await nfc.disconnect();
        return {'error': 'Ngay sinh hoac ngay het han khong hop le (YYMMDD)'};
      }
      final dbaKey = DBAKey(docNumber, dobDt, doeDt);

      // Establish secure session via BAC
      final passport = Passport(nfc);
      await passport.startSession(dbaKey);
      _log.info('NFC: session established via BAC');

      // Read DG1 (MRZ data) and DG2 (facial image)
      final dg1 = await passport.readEfDG1();
      final dg2 = await passport.readEfDG2();

      final mrz = dg1.mrz;
      final cccdNumber = mrz.documentNumber;
      final fullName   = ('${mrz.firstName} ${mrz.lastName}').trim();
      final nationality = mrz.nationality;

      // Extract photo directly from EfDG2.imageData
      final photoBytes = dg2.imageData;

      await nfc.disconnect();
      _log.info('NFC: read OK - CCCD=$cccdNumber name=$fullName');

      return {
        'cccdNumber':  cccdNumber,
        'fullName':    fullName,
        'nationality': nationality,
        'photoBytes':  photoBytes,
        'dateOfBirth': dobDt,
        'dateOfExpiry': doeDt,
      };
    } on PlatformException catch (e) {
      _log.severe('NFC Platform Error: ${e.message}');
      await nfc.disconnect().catchError((_) {});
      return {'error': e.message ?? 'NFC Platform Error', 'code': e.code};
    } catch (e) {
      _log.severe('NFC Read Error: $e');
      await nfc.disconnect().catchError((_) {});
      return {'error': e.toString()};
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
