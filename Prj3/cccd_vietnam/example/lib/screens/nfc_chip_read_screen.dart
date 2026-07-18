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
  bool _autoStarted = false;

  @override
  void initState() {
    super.initState();
    if (widget.flowState.can != null && widget.flowState.can!.isNotEmpty) {
      _canController.text = widget.flowState.can!;
      // Trigger auto-read after first frame
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && !_autoStarted) {
          _autoStarted = true;
          _startNfcRead(widget.flowState.can!, null, null, null);
        }
      });
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
    // If CAN was extracted from QR, we simplify the UI significantly
    final bool hasCanFromQr = widget.flowState.can != null && widget.flowState.can!.isNotEmpty;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Xác thực Chip NFC'),
        centerTitle: true,
        elevation: 0,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const SizedBox(height: 20),
            // NFC Icon Animation / Illustration
            Icon(
              Icons.contactless_outlined,
              size: 100,
              color: _isReading ? Colors.blue : Colors.grey,
            ),
            const SizedBox(height: 32),
            
            Text(
              hasCanFromQr ? 'Đã nhận dạng mã CAN từ QR' : 'Yêu cầu đọc Chip',
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Text(
              _isReading 
                ? 'Đang thực hiện kết nối...\nVui lòng đặt thẻ vào mặt lưng máy'
                : 'Đặt thẻ CCCD gắn chip vào vùng NFC phía sau điện thoại',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey[600]),
            ),
            
            const SizedBox(height: 40),

            // Progress/Status indicator
            if (_status.isNotEmpty)
              Column(
                children: [
                  const CircularProgressIndicator(strokeWidth: 2),
                  const SizedBox(height: 12),
                  Text(_status, textAlign: TextAlign.center, style: const TextStyle(color: Colors.blue)),
                ],
              ),

            // Cho phép nhập CAN thủ công nếu tự động đọc thất bại để khắc phục lỗi CAN từ QR bị sai
            if (_errorMessage.isNotEmpty || hasCanFromQr)
              Padding(
                padding: const EdgeInsets.only(top: 24),
                child: Column(
                  children: [
                    const Text('Kiểm tra mã CAN (6 số trên thẻ):', style: TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    TextField(
                      controller: _canController,
                      textAlign: TextAlign.center,
                      decoration: const InputDecoration(
                        hintText: 'Nhập 6 số CAN',
                        border: OutlineInputBorder(),
                        contentPadding: EdgeInsets.symmetric(horizontal: 10),
                      ),
                      keyboardType: TextInputType.number,
                      maxLength: 6,
                    ),
                  ],
                ),
              ),

            // Error display
            if (_errorMessage.isNotEmpty)
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(16),
                margin: const EdgeInsets.only(top: 20),
                decoration: BoxDecoration(
                  color: Colors.red.withValues(alpha: 0.05),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.red.withValues(alpha: 0.2)),
                ),
                child: Column(
                  children: [
                    const Icon(Icons.error_outline, color: Colors.red),
                    const SizedBox(height: 8),
                    Text(
                      _errorMessage,
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: Colors.red),
                    ),
                    const SizedBox(height: 16),
                    // Retry button if auto-read failed
                    if (hasCanFromQr)
                      ElevatedButton(
                        onPressed: () => _startNfcRead(_canController.text, null, null, null),
                        child: const Text('Thử lại'),
                      ),
                  ],
                ),
              ),

            // Only show manual fields if NO CAN was provided from QR
            if (!hasCanFromQr) ...[
              const Divider(height: 48),
              const Text('Nhập thủ công nếu cần:', style: TextStyle(fontWeight: FontWeight.w500)),
              const SizedBox(height: 16),
              TextField(
                controller: _canController,
                decoration: const InputDecoration(labelText: 'Mã CAN (6 số)', border: OutlineInputBorder()),
                keyboardType: TextInputType.number,
                maxLength: 6,
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _docNumberController,
                decoration: const InputDecoration(labelText: 'Số CCCD', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton.icon(
                  onPressed: _validateAndReadChipManual,
                  icon: const Icon(Icons.nfc),
                  label: const Text('Bắt đầu đọc Chip'),
                ),
              ),
            ],

            const SizedBox(height: 40),
            
            if (!_isReading)
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Quay lại màn hình chính'),
              ),
          ],
        ),
      ),
    );
  }

  void _validateAndReadChipManual() {
    final can = _canController.text.trim();
    final doc = _docNumberController.text.trim();
    final dob = _dobController.text.trim();
    final doe = _doeController.text.trim();

    if (can.isEmpty && doc.isEmpty) {
      setState(() => _errorMessage = 'Vui lòng nhập ít nhất mã CAN hoặc số CCCD');
      return;
    }
    _startNfcRead(can, doc, dob, doe);
  }

  Future<void> _startNfcRead(
    String can,
    String? docNumber,
    String? dob,
    String? doe,
  ) async {
    if (_isReading) return;
    
    setState(() {
      _isReading = true;
      _errorMessage = '';
      _status = 'Khởi động NFC...';
    });

    try {
      final result = await NfcChipService.readChipData(
        can: can,
        docNumber: docNumber,
        dob: dob,
        doe: doe,
        onProgress: (msg) {
          if (mounted) setState(() => _status = msg);
        },
      );

      if (!mounted) return;

      if (result != null && result['error'] != null) {
        setState(() {
          _isReading = false;
          _status = '';
          _errorMessage = result['error'];
        });
        return;
      }

      if (result != null) {
        // Save to state
        widget.flowState.cccdNumber = result['cccdNumber'];
        widget.flowState.fullName = result['fullName'];
        widget.flowState.nationality = result['nationality'];
        widget.flowState.chipPhotoBytes = result['photoBytes'];
        widget.flowState.dateOfBirth = result['dateOfBirth'];
        widget.flowState.dateOfExpiry = result['dateOfExpiry'];

        _log.info('NFC Read success: ${widget.flowState.cccdNumber}');
        
        // Success - pop back to flow controller
        Navigator.pop(context, true);
      } else {
        setState(() {
          _isReading = false;
          _status = '';
          _errorMessage = 'Lỗi không xác định khi đọc chip';
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isReading = false;
          _status = '';
          _errorMessage = 'Lỗi: $e';
        });
      }
    }
  }
}
