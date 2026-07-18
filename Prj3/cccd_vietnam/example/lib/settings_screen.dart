import 'package:flutter/material.dart';
import 'services/ConfigService.dart';
import 'services/MqttClientService.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  _SettingsScreenState createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _formKey = GlobalKey<FormState>();
  final _serverIpController = TextEditingController();
  final _deviceCodeController = TextEditingController();
  final _orgIdController = TextEditingController();
  final _pinController = TextEditingController();

  String _message = '';
  bool _loading = false;
  bool _isLocked = false;
  bool _unlockMode = false; // true = đã xác thực master PIN, đang cho phép chỉnh sửa
  bool _scanning = false;
  int _scanProgress = 0;
  int _scanTotal = 254;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final ip = await ConfigService.getServerIp();
    final orgId = await ConfigService.getOrgId();
    final code = await ConfigService.getDeviceCode();
    final locked = await ConfigService.isDeviceLocked();

    setState(() {
      _serverIpController.text = ip;
      _orgIdController.text = orgId;
      _deviceCodeController.text = code;
      _isLocked = locked;
    });
  }

  Future<void> _handleDiscoverServer() async {
    setState(() {
      _scanning = true;
      _scanProgress = 0;
      _message = 'Đang quét mạng nội bộ để tìm server điểm danh...';
    });

    final foundList = await ConfigService.discoverServers(
      onProgress: (checked, total) {
        if (mounted) {
          setState(() {
            _scanProgress = checked;
            _scanTotal = total;
          });
        }
      },
    );

    if (!mounted) return;
    setState(() => _scanning = false);

    if (foundList.isEmpty) {
      setState(() => _message = '✗ Không tìm thấy server. Kiểm tra server đang chạy và cùng WiFi.');
    } else if (foundList.length == 1) {
      // Chỉ 1 server → tự điền luôn
      setState(() {
        _serverIpController.text = foundList.first;
        _message = '✓ Tìm thấy server tại: ${foundList.first} — Nhấn LƯU để xác nhận.';
      });
    } else {
      // Nhiều server → hiện dialog chọn
      final chosen = await _showServerPickerDialog(foundList);
      if (chosen != null && mounted) {
        setState(() {
          _serverIpController.text = chosen;
          _message = '✓ Đã chọn: $chosen — Nhấn LƯU để xác nhận.';
        });
      }
    }
  }

  Future<String?> _showServerPickerDialog(List<String> servers) {
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Chọn server điểm danh'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'Tìm thấy ${servers.length} server trong mạng.\nChọn máy chủ bạn muốn kết nối:',
              style: const TextStyle(fontSize: 13, color: Colors.grey),
            ),
            const SizedBox(height: 12),
            ...servers.map((ip) => ListTile(
              leading: const Icon(Icons.computer, color: Colors.blue),
              title: Text(ip, style: const TextStyle(fontWeight: FontWeight.bold)),
              subtitle: Text('http://$ip:8080'),
              onTap: () => Navigator.pop(ctx, ip),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
            )),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Hủy')),
        ],
      ),
    );
  }

  Future<void> _handleSave() async {
    if (!_formKey.currentState!.validate()) return;

    // 1. Kiểm tra PIN quản trị nội bộ App
    if (!await ConfigService.checkAdminPin(_pinController.text)) {
      setState(() => _message = "Mã PIN Admin App không chính xác!");
      return;
    }

    setState(() {
      _loading = true;
      _message = "Đang lưu cấu hình...";
    });

    // 2. Lưu (không cần master PIN nữa vì đã xác thực ở bước mở khóa)
    final resIp  = await ConfigService.setServerIp(_serverIpController.text.trim());
    final resOrg = await ConfigService.setOrgId(_orgIdController.text.trim());
    final resDev = await ConfigService.setDeviceCode(
      _deviceCodeController.text.trim(),
      skipLockCheck: _unlockMode, // đã mở khóa trước rồi → bỏ qua check PIN
    );

    if (resIp && resOrg && resDev) {
      // Reinitialize MQTT với device code mới ngay lập tức
      MqttClientService.reinitialize().ignore();
    }

    setState(() {
      _loading = false;
      if (resIp && resOrg && resDev) {
        _message = "Cấu hình thành công!";
        _isLocked = true;
        _unlockMode = false; // khóa lại sau khi lưu
      } else {
        _message = "Thất bại: Không thể lưu cấu hình!";
      }
    });
  }

  // Mở khóa thiết bị bằng Master PIN từ Server
  Future<void> _handleUnlock() async {
    setState(() {
      _loading = true;
      _message = "Đang kết nối Server để lấy mã mở khóa...";
    });

    bool fetched = await ConfigService.fetchRemoteMasterPin();
    if (!fetched) {
      setState(() {
        _loading = false;
        _message = "Lỗi: Không thể kết nối Server để lấy mã mở khóa!";
      });
      return;
    }

    setState(() => _loading = false);

    final masterPinInput = await _askMasterPin();
    if (masterPinInput == null) return;

    final ok = ConfigService.verifyMasterPin(masterPinInput);
    if (ok) {
      setState(() {
        _unlockMode = true;
        _message = "✓ Mở khóa thành công! Hãy chỉnh sửa rồi nhấn LƯU.";
      });
    } else {
      setState(() => _message = "✗ Master PIN không đúng!");
    }
  }

  // Dialog hỏi mã PIN lấy từ Web/Server
  Future<String?> _askMasterPin() {
    TextEditingController temp = TextEditingController();
    return showDialog<String>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        title: const Text("Xác thực hệ thống"),
        content: TextField(
          controller: temp,
          obscureText: true,
          decoration: const InputDecoration(hintText: "Nhập Master PIN từ Web Admin"),
          keyboardType: TextInputType.number,
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text("Hủy")),
          TextButton(onPressed: () => Navigator.pop(ctx, temp.text), child: const Text("Xác nhận")),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Cấu hình thiết bị")),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Form(
          key: _formKey,
          child: Column(
            children: [
              TextFormField(
                controller: _serverIpController,
                decoration: const InputDecoration(labelText: "Server IP / URL", border: OutlineInputBorder()),
              ),
              const SizedBox(height: 8),
              // Nút tự động tìm server
              SizedBox(
                width: double.infinity,
                height: 42,
                child: OutlinedButton.icon(
                  onPressed: (_scanning || _loading) ? null : _handleDiscoverServer,
                  icon: _scanning
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.search, color: Colors.blue),
                  label: Text(
                    _scanning
                        ? 'Đang quét... $_scanProgress/$_scanTotal'
                        : 'Tự động tìm Server trong mạng',
                    style: const TextStyle(color: Colors.blue),
                  ),
                  style: OutlinedButton.styleFrom(side: const BorderSide(color: Colors.blue)),
                ),
              ),
              const SizedBox(height: 15),
              TextFormField(
                controller: _orgIdController,
                decoration: const InputDecoration(labelText: "Mã doanh nghiệp (Org ID)", border: OutlineInputBorder()),
              ),
              const SizedBox(height: 15),
              TextFormField(
                controller: _deviceCodeController,
                enabled: !_isLocked || _unlockMode, // cho phép sửa khi chưa khóa hoặc đã mở khóa
                decoration: InputDecoration(
                  labelText: "Mã định danh cửa (Device Code)",
                  border: const OutlineInputBorder(),
                  suffixIcon: Icon(
                    _unlockMode ? Icons.lock_open : (_isLocked ? Icons.lock : Icons.lock_open),
                    color: _unlockMode ? Colors.green : (_isLocked ? Colors.red : Colors.green),
                  ),
                  helperText: _unlockMode
                      ? "Đã mở khóa. Chỉnh sửa rồi nhấn LƯU."
                      : (_isLocked ? "Đã khóa. Nhấn nút MỞ KHÓA để sửa." : "Lần đầu: Nhập mã cửa rồi lưu để chốt."),
                ),
              ),
              if (_isLocked && !_unlockMode) ...[
                const SizedBox(height: 10),
                SizedBox(
                  width: double.infinity,
                  height: 44,
                  child: OutlinedButton.icon(
                    onPressed: _loading ? null : _handleUnlock,
                    icon: const Icon(Icons.lock_open, color: Colors.orange),
                    label: const Text("MỞ KHÓA ĐỂ CHỈNH SỬA", style: TextStyle(color: Colors.orange)),
                    style: OutlinedButton.styleFrom(side: const BorderSide(color: Colors.orange)),
                  ),
                ),
              ],
              const SizedBox(height: 25),
              const Divider(),
              const SizedBox(height: 10),
              TextFormField(
                controller: _pinController,
                obscureText: true,
                decoration: const InputDecoration(labelText: "Nhập PIN Admin App để xác nhận lưu", border: OutlineInputBorder()),
              ),
              const SizedBox(height: 30),
              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton(
                  onPressed: _loading ? null : _handleSave,
                  child: _loading ? const CircularProgressIndicator(color: Colors.white) : const Text("LƯU CẤU HÌNH"),
                ),
              ),
              const SizedBox(height: 20),
              Text(_message, style: const TextStyle(color: Colors.blue, fontWeight: FontWeight.bold), textAlign: TextAlign.center),
            ],
          ),
        ),
      ),
    );
  }
}