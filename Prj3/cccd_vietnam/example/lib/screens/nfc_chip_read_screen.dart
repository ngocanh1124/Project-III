import 'package:flutter/material.dart';
import '../models/attendance_flow_state.dart';
import '../services/nfc_chip_service.dart';
import '../services/LocalDatabaseService.dart';
import '../services/ConfigService.dart';
import 'package:logging/logging.dart';

class NfcChipReadScreen extends StatefulWidget {
  final AttendanceFlowState flowState;

  const NfcChipReadScreen({
    Key? key,
    required this.flowState,
  }) : super(key: key);

  @override
  State<NfcChipReadScreen> createState() => _NfcChipReadScreenState();
}

class _NfcChipReadScreenState extends State<NfcChipReadScreen> {
  final _log = Logger('NfcChipReadScreen');
  
  final _canController = TextEditingController();
  final _docNumberController = TextEditingController();
  final _dobController = TextEditingController();
  final _doeController = TextEditingController();

  bool _isReading = false;
  String _status = '';
  String _errorMessage = '';

  @override
  void initState() {
    super.initState();
    // Pre-fill CAN from QR scan
    if (widget.flowState.can != null) {
      _canController.text = widget.flowState.can!;
    }
  }

  @override
  void dispose() {
    _canController.dispose();
    _docNumberController.dispose();
    _dobController.dispose();
    _doeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Đọc Chip CCCD'),
        centerTitle: true,
        elevation: 0,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Instructions
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.blue.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.blue.withValues(alpha: 0.3)),
              ),
              child: const Text(
                'Nhập thông tin từ thẻ CCCD của bạn:\n'
                '• CAN: Mã truy cập (6 chữ số)\n'
                '• Số CCCD/Hộ chiếu: Từ trang đầu thẻ\n'
                '• Ngày sinh: YYMMDD (ví dụ: 900115)\n'
                '• Ngày hết hạn: YYMMDD',
                style: TextStyle(fontSize: 13),
              ),
            ),
            const SizedBox(height: 24),

            // CAN Input
            TextField(
              controller: _canController,
              enabled: !_isReading,
              decoration: InputDecoration(
                labelText: 'CAN (6 chữ số)',
                hintText: '123456',
                prefixIcon: const Icon(Icons.numbers),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
                enabled: !_isReading,
              ),
              keyboardType: TextInputType.number,
              maxLength: 6,
            ),
            const SizedBox(height: 16),

            // Document Number
            TextField(
              controller: _docNumberController,
              enabled: !_isReading,
              decoration: InputDecoration(
                labelText: 'Số CCCD/Hộ chiếu',
                hintText: '123456789',
                prefixIcon: const Icon(Icons.credit_card),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              keyboardType: TextInputType.number,
              maxLength: 12,
            ),
            const SizedBox(height: 16),

            // Date of Birth
            TextField(
              controller: _dobController,
              enabled: !_isReading,
              decoration: InputDecoration(
                labelText: 'Ngày sinh (YYMMDD)',
                hintText: '900115',
                prefixIcon: const Icon(Icons.calendar_today),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              keyboardType: TextInputType.number,
              maxLength: 6,
            ),
            const SizedBox(height: 16),

            // Date of Expiry
            TextField(
              controller: _doeController,
              enabled: !_isReading,
              decoration: InputDecoration(
                labelText: 'Ngày hết hạn (YYMMDD)',
                hintText: '300115',
                prefixIcon: const Icon(Icons.calendar_today),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              keyboardType: TextInputType.number,
              maxLength: 6,
            ),
            const SizedBox(height: 24),

            // Status message
            if (_status.isNotEmpty)
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.orange.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.orange.withValues(alpha: 0.3)),
                ),
                child: Text(
                  _status,
                  style: const TextStyle(fontSize: 14, color: Colors.orange),
                ),
              ),

            // Error message
            if (_errorMessage.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 16),
                child: Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.red.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.red.withValues(alpha: 0.3)),
                  ),
                  child: Text(
                    _errorMessage,
                    style: const TextStyle(fontSize: 13, color: Colors.red),
                  ),
                ),
              ),

            const SizedBox(height: 24),

            // Read Button
            SizedBox(
              width: double.infinity,
              height: 48,
              child: _isReading
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const CircularProgressIndicator(),
                          const SizedBox(height: 8),
                          const Text(
                            'Đang đọc chip...\nĐặt thẻ gần điện thoại',
                            textAlign: TextAlign.center,
                            style: TextStyle(fontSize: 12),
                          ),
                        ],
                      ),
                    )
                  : ElevatedButton.icon(
                      onPressed: _validateAndReadChip,
                      icon: const Icon(Icons.nfc),
                      label: const Text('Bắt đầu đọc Chip'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.blue,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                    ),
            ),

            const SizedBox(height: 12),

            // Back button
            if (!_isReading)
              SizedBox(
                width: double.infinity,
                height: 48,
                child: OutlinedButton(
                  onPressed: () => Navigator.pop(context),
                  style: OutlinedButton.styleFrom(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: const Text('Quay về'),
                ),
              ),
          ],
        ),
      ),
    );
  }

  void _validateAndReadChip() {
    setState(() => _errorMessage = '');

    final can = _canController.text.trim();
    final docNumber = _docNumberController.text.trim();
    final dob = _dobController.text.trim();
    final doe = _doeController.text.trim();

    // Validation
    if (can.isEmpty || !NfcChipService.validateCAN(can)) {
      setState(() => _errorMessage = 'CAN không hợp lệ (phải là 6 chữ số)');
      return;
    }
    if (docNumber.isEmpty || docNumber.length < 9) {
      setState(() => _errorMessage = 'Số CCCD/Hộ chiếu không hợp lệ');
      return;
    }
    if (dob.isEmpty || dob.length != 6) {
      setState(() => _errorMessage = 'Ngày sinh không hợp lệ (phải YYMMDD)');
      return;
    }
    if (doe.isEmpty || doe.length != 6) {
      setState(() => _errorMessage = 'Ngày hết hạn không hợp lệ (phải YYMMDD)');
      return;
    }

    _startNfcRead(can, docNumber, dob, doe);
  }

  Future<void> _startNfcRead(
    String can,
    String docNumber,
    String dob,
    String doe,
  ) async {
    setState(() {
      _isReading = true;
      _status = 'Chuẩn bị...';
    });

    try {
      setState(() => _status = 'Chờ thẻ...');
      
      final result = await NfcChipService.readChipData(
        can: can,
        docNumber: docNumber,
        dob: dob,
        doe: doe,
      );

      if (!mounted) return;

      if (result == null) {
        setState(() {
          _isReading = false;
          _errorMessage = 'Không thể đọc chip. Thử lại.';
        });
        return;
      }

      if (result.containsKey('error')) {
        setState(() {
          _isReading = false;
          _errorMessage = 'Lỗi: ${result['error']}';
        });
        return;
      }

      // Success - update flow state
      // Chip NFC (MRZ TD1) trả 9 số cuối, cần resolve lên 12 số đầy đủ từ DB
      final String chipCccd = result['cccdNumber'] ?? '';

      // Refresh danh sách quyền từ server trước khi resolve
      final deviceCode = await ConfigService.getDeviceCode();
      if (deviceCode != 'CHƯA_CẤU_HÌNH') {
        await LocalDatabaseService.fetchPermissionsFromServer(deviceCode);
      }

      final String? fullCccd = await LocalDatabaseService.findMatchingCccd(chipCccd);
      widget.flowState.cccdNumber = fullCccd ?? chipCccd; // Ưu tiên 12 số, fallback chip
      widget.flowState.fullName = result['fullName'];
      widget.flowState.nationality = result['nationality'];
      widget.flowState.chipPhotoBytes = result['photoBytes'];
      widget.flowState.dateOfBirth = result['dateOfBirth'];
      widget.flowState.dateOfExpiry = result['dateOfExpiry'];

      setState(() => _status = 'Đọc thành công! ✓');

      // Return success
      await Future.delayed(const Duration(milliseconds: 500));
      if (mounted) {
        Navigator.pop(context, true);
      }
    } catch (e) {
      _log.severe('NFC Read Error: $e');
      if (mounted) {
        setState(() {
          _isReading = false;
          _errorMessage = 'Lỗi: $e';
        });
      }
    }
  }
}
