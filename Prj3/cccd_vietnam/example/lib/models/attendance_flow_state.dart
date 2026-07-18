import 'dart:typed_data';
import 'package:cccd_vietnam/extensions.dart';
import 'package:cccd_vietnam/dmrtd.dart';

/// Central state container for the entire attendance flow
class AttendanceFlowState {
  // Step 1: QR Scan
  String? qrData;
  String? can;

  // Step 2: NFC Chip Read
  String? cccdNumber;
  String? fullName;
  String? nationality;
  Uint8List? chipPhotoBytes;
  DateTime? dateOfBirth;
  DateTime? dateOfExpiry;

  // Step 3: Face Capture
  Uint8List? selfiePhotoBytes;

  // Step 4: Comparison Result
  double? comparisonScore;
  bool? isMatched;
  String? comparisonMode; // 'local' or 'server_fallback'
  String? errorMessage;

  // Step 5: Authorization
  // Layer 1: Early check (offline - after QR scan)
  bool? layer1AuthorizedAtQR;
  String? layer1AuthErrorMessage;
  
  // Layer 2: Final check (online - at server)
  bool? layer2AuthorizedAtServer;
  String? layer2AuthErrorMessage;

  // Combined result
  bool get isAuthorizedForDoor =>
      (layer1AuthorizedAtQR == true) && (layer2AuthorizedAtServer != false);

  DateTime? allowedEnterTime;
  DateTime? allowedExitTime;

  // Metadata
  String? deviceCode;
  DateTime? recordedAt;

  /// Reset all steps (for new scan)
  void reset() {
    qrData = null;
    can = null;
    cccdNumber = null;
    fullName = null;
    nationality = null;
    chipPhotoBytes = null;
    dateOfBirth = null;
    dateOfExpiry = null;
    selfiePhotoBytes = null;
    comparisonScore = null;
    isMatched = null;
    comparisonMode = null;
    errorMessage = null;
    layer1AuthorizedAtQR = null;
    layer1AuthErrorMessage = null;
    layer2AuthorizedAtServer = null;
    layer2AuthErrorMessage = null;
    allowedEnterTime = null;
    allowedExitTime = null;
    recordedAt = null;
  }

  /// Check if basic CCCD info was successfully extracted
  bool get hasChipData =>
      cccdNumber != null &&
      fullName != null &&
      chipPhotoBytes != null;

  /// Check if face comparison is ready
  bool get canCompareFaces =>
      hasChipData && selfiePhotoBytes != null;

  /// Check if ready to send to server
  bool get isReadyToSend =>
      canCompareFaces &&
      comparisonScore != null &&
      isMatched != null;

  @override
  String toString() {
    return '''AttendanceFlowState(
      qr=$qrData, can=$can,
      cccd=$cccdNumber, name=$fullName,
      score=$comparisonScore, matched=$isMatched,
      authorized=$isAuthorizedForDoor
    )''';
  }
}
