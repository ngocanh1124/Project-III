import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'dart:async';
import 'dart:convert'; 
import 'dart:typed_data'; 
import 'package:cccd_vietnam/dmrtd.dart';
import 'package:cccd_vietnam/extensions.dart';
import 'package:flutter/services.dart';
import 'package:flutter_platform_widgets/flutter_platform_widgets.dart';
import 'package:logging/logging.dart';
import 'package:cccd_vietnam/src/proto/can_key.dart';
import 'package:intl/intl.dart';
import 'qr_scanner_screen.dart';
import 'mrz_scanner_screen.dart';
import 'face_compare_screen.dart';
import 'screens/authorization_denied_screen.dart';
import 'screens/attendance_flow_screen.dart';
import 'screens/remote_unlock_cccd_screen.dart';
import 'settings_screen.dart';
import 'door_open_screen.dart';
import 'services/ConfigService.dart';
import 'services/image_converter_service.dart';
import 'services/LocalDatabaseService.dart';
import 'services/MqttClientService.dart';
import 'services/OfflineSyncService.dart';
import 'app_navigator.dart';

class MrtdData {
  EfCardAccess? cardAccess;
  EfCardSecurity? cardSecurity;
  EfCOM? com;
  EfSOD? sod;
  EfDG1? dg1;
  EfDG2? dg2;
  EfDG3? dg3;
  EfDG4? dg4;
  EfDG5? dg5;
  EfDG6? dg6;
  EfDG7? dg7;
  EfDG8? dg8;
  EfDG9? dg9;
  EfDG10? dg10;
  EfDG11? dg11;
  EfDG12? dg12;
  EfDG13? dg13;
  EfDG14? dg14;
  EfDG15? dg15;
  EfDG16? dg16;
  Uint8List? aaSig;
  bool? isPACE;
  bool? isDBA;
}

String formatProgressMsg(String message, int percentProgress) {
  final p = (percentProgress / 20).round();
  final full = "🟢 " * p;
  final empty = "⚪️ " * (5 - p);
  return message + "\n\n" + full + empty;
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  await ConfigService.init();

  Logger.root.level = Level.ALL;
  Logger.root.logSensitiveData = true;
  Logger.root.onRecord.listen((record) {
    print(
      '${record.loggerName} ${record.level.name}: ${record.time}: ${record.message}',
    );
  });
  runApp(MrtdEgApp());
}

class MrtdEgApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return PlatformApp(
      navigatorKey: appNavigatorKey,
      localizationsDelegates: [
        DefaultMaterialLocalizations.delegate,
        DefaultCupertinoLocalizations.delegate,
        DefaultWidgetsLocalizations.delegate,
      ],
      material: (_, __) => MaterialAppData(),
      cupertino: (_, __) => CupertinoAppData(),
      home: const AttendanceFlowScreen(),
    );
  }
}

class MrtdHomePage extends StatefulWidget {
  @override
  _MrtdHomePageState createState() => _MrtdHomePageState();
}

class _MrtdHomePageState extends State<MrtdHomePage> with WidgetsBindingObserver {
  var _alertMessage = "";
  final _log = Logger("mrtdeg.app");
  var _isNfcAvailable = false;
  var _isReading = false;
  final _canData = GlobalKey<FormState>();
  final _can = TextEditingController();
  final _docNumber = TextEditingController();
  final _dob = TextEditingController();
  final _doe = TextEditingController();

  MrtdData? _mrtdData;

  final NfcProvider _nfc = NfcProvider();
  late Timer _timerStateUpdater;
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
    ]);
    _initPlatformState();
    _timerStateUpdater = Timer.periodic(Duration(seconds: 3), (Timer t) {
      if (mounted) {
      _initPlatformState();
      }
    });
    _startMqttService();
    // Flush offline queue khi app khởi động và có mạng
    OfflineSyncService.syncPending();
    // Lắng nghe app lifecycle để sync khi mạng trở lại
    WidgetsBinding.instance.addObserver(this);
    // Sync danh sách nhân viên được phép vào cửa từ server (HTTP fallback)
    _syncPermissionsFromServer();
  }

  Future<void> _syncPermissionsFromServer() async {    final deviceCode = await ConfigService.getDeviceCode();
    if (deviceCode != 'CHƯA_CẤU_HÌNH') {
      await LocalDatabaseService.fetchPermissionsFromServer(deviceCode);
    }
  }

  Future<void> _initPlatformState() async {
    bool isNfcAvailable;
    try {
      NfcStatus status = await NfcProvider.nfcStatus;
      isNfcAvailable = status == NfcStatus.enabled;
    } on PlatformException {
      isNfcAvailable = false;
    }

    if (!mounted) return;

    setState(() {
      _isNfcAvailable = isNfcAvailable;
    });
  }
 // 1. Cập nhật hàm điều hướng so sánh mặt
  void _navigateToFaceCompare() async {
    if (_mrtdData?.dg2 == null || _mrtdData?.dg1 == null) {
      setState(() {
        _alertMessage = "Dữ liệu chưa đủ! Vui lòng đọc thẻ CCCD trước (cần DG1 và DG2).";
      });
      return;
    }
    
    final String chipCccd = _mrtdData!.dg1!.mrz.documentNumber.replaceAll('<', '').trim();
    print('DEBUG chip CCCD="$chipCccd" (${chipCccd.length} chars)');

    // Refresh danh sách quyền từ server trước khi kiểm tra
    // (tránh tình trạng MQTT SYNC_DATA bị miss hoặc DB bị xóa khi app khởi động)
    await _syncPermissionsFromServer();

    // Tìm CCCD đầy đủ 12 số trong DB cục bộ (suffix match cho chip 9 số).
    final String? fullCccd = await LocalDatabaseService.findMatchingCccd(chipCccd);

    // Kiểm tra quyền: CCCD không có trong DB phân quyền cục bộ → TỪ CHỐI ngay,
    // không cho tiến tới màn hình quét mặt.
    // Bỏ qua khi _isRemoteUnlockMode=true (admin đã xác nhận mở cửa từ xa).
    if (fullCccd == null && !_isRemoteUnlockMode) {
      print('DEBUG auth denied: chipCccd="$chipCccd" không có trong DB phân quyền');
      if (mounted) {
        await Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => AuthorizationDeniedScreen(
              cccdNumber: chipCccd,
              reason: 'Bạn không có quyền vào cửa này.\nVui lòng liên hệ quản trị viên để được cấp quyền.',
            ),
          ),
        );
      }
      return;
    }

    final String cccdToUse = fullCccd ?? chipCccd;
    print('DEBUG cccdToUse="$cccdToUse" (local=${fullCccd != null})');

    final String fullName = "${_mrtdData!.dg1!.mrz.lastName} ${_mrtdData!.dg1!.mrz.firstName}";
    Uint8List? chipImageBytes = _mrtdData!.dg2!.imageData;
    
    if (chipImageBytes == null || chipImageBytes.isEmpty) {
      if (mounted) setState(() => _alertMessage = "Lỗi: Không tìm thấy ảnh trong chip!");
      return;
    }

    final convertedBytes = await ImageConverterService.decodeChipImage(chipImageBytes);
    if (convertedBytes == null) {
      if (mounted) setState(() => _alertMessage = "Lỗi: Không thể giải mã ảnh từ chip!");
      return;
    }

    if (!mounted) return;
    
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => FaceCompareScreen(
          chipImageBytes: convertedBytes,
          cccd: cccdToUse,   // Dùng CCCD đầy đủ 12 số từ DB, hoặc chipCccd nếu không có
          fullname: fullName,
          remoteUnlock: _isRemoteUnlockMode, // true khi admin trigger qua MQTT
        ),
      ),
    );

    // Reset CCCD data sau khi trở về từ FaceCompareScreen/DoorOpenScreen
    if (mounted) {
      setState(() {
        _mrtdData = null;
        _alertMessage = '';
      });
      _isRemoteUnlockMode = false; // reset sau khi flow hoàn thành
      _doe.clear();
    }
  }

  // 2. Cập nhật hàm xử lý tín hiệu MQTT thành công
  bool _isOpenDoorSignal = false;
  bool _isRemoteUnlockMode = false; // true khi admin bấm "Mở cửa từ xa" → skip permission check

  void _handleOpenDoorSignalLocal(String msg) {
    if (!mounted) return;
    // Reset data
    setState(() {
      _mrtdData = null;
      _can.clear();
      _alertMessage = '';
      _isOpenDoorSignal = false;
    });
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => DoorOpenScreen(label: msg),
      ),
    );
  }

  void _navigateToQrScanner() async {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => QrScannerScreen(
          onCanDetected: (can) async { 
            setState(() {
              _can.text = can;
              _buttonPressed(); 
            });
          },
        ),
      ),
    );
  }

  void _buttonPressed() async {
    print("Button pressed");

    String errorText = "";
    if (_can.text.isEmpty) {
      errorText = "Vui lòng quét mã QR trên CCCD!";
    } else if (_can.text.length != 6) {
      errorText = "Mã CAN phải có đúng 6 chữ số!";
    }

    setState(() {
      _alertMessage = errorText;
    });

    if (errorText.isNotEmpty) return;

    final canKeySeed = CanKey(_can.text);
    _readMRTD(accessKey: canKeySeed);
  }

  int _nfcRetryCount = 0;
  static const int maxNfcRetries = 2;
  static const Duration connectTimeout = Duration(seconds: 20);
  static const Duration readTimeout = Duration(seconds: 30);
  static const Duration retryDelay = Duration(seconds: 2);

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _cleanupNfc();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      // Mỗi khi app lên foreground: thử sync offline queue
      OfflineSyncService.syncPending().then((result) {
        if ((result['sent'] ?? 0) > 0 && mounted) {
          setState(() => _alertMessage = '✓ Đã đồng bộ ${result['sent']} bản ghi offline lên server');
          Future.delayed(const Duration(seconds: 4), () {
            if (mounted) setState(() => _alertMessage = '');
          });
        }
      });
    }
  }

  void _startMqttService() async {
    try {
      await MqttClientService.initialize((Map<String, dynamic> data) {
        final action = data['action']?.toString() ?? '';
        if (action == 'START_AUTH_FLOW') {
          // Admin yêu cầu xác minh danh tính người đứng trước cửa
          // BẮT BUỘC: Quét thẻ NFC (CCCD cứng) để đảm bảo chính chủ
          print('[MQTT] START_AUTH_FLOW → navigate to RemoteUnlockCccdScreen (Force NFC Mode)');
          if (mounted) {
            // Quay về màn chính trước
            appNavigatorKey.currentState?.popUntil((route) => route.isFirst);
            
            WidgetsBinding.instance.addPostFrameCallback((_) {
              if (mounted) {
                appNavigatorKey.currentState?.push(
                  MaterialPageRoute(
                    builder: (_) => RemoteUnlockCccdScreen(
                      remoteMessage: 'Admin yêu cầu quét thẻ CCCD cứng để mở cửa',
                    ),
                  ),
                );
              }
            });
          }
        } else if (action == 'OPEN_DOOR') {
          // OPEN_DOOR từ approve PENDING_APPROVAL — chỉ xử lý nếu đang ở màn chính
          // (face_compare_screen.dart tự xử lý khi đang chờ duyệt)
          final msg = data['message']?.toString() ?? 'Cửa đang mở';
          print('[MQTT] OPEN_DOOR received on main screen: $msg');
          if (mounted) {
            appNavigatorKey.currentState?.push(
              MaterialPageRoute(builder: (_) => DoorOpenScreen(label: msg)),
            );
          }
        }
      });
    } catch (e) {
      print('MQTT init error: $e');
    }
  }

  Future<void> _cleanupNfc() async {
    try {
      if (_nfc.isConnected()) {
        await _nfc.disconnect();
      }
      _isReading = false;
      _nfcRetryCount = 0;
    } catch (e) {
      print("Error during NFC cleanup: $e");
    }
  }

  void _readMRTD({required AccessKey accessKey}) async {
    try {
      setState(() {
        _mrtdData = null;
        _alertMessage = "Đang đợi kết nối NFC... (${_nfcRetryCount + 1}/$maxNfcRetries)";
        _isReading = true;
      });

      final status = await NfcProvider.nfcStatus;
      if (status != NfcStatus.enabled) {
        throw Exception('NFC disabled');
      }

      bool connected = false;
      while (!connected && _nfcRetryCount < maxNfcRetries) {
        try {
          await _nfc.connect(
            timeout: connectTimeout,
            iosAlertMessage: "Đặt điện thoại gần CCCD",
          );
          connected = true;
          print("NFC connected successfully");
        } catch (e) {
          _nfcRetryCount++;
          print("NFC connect attempt $_nfcRetryCount failed: $e");
          if (_nfcRetryCount < maxNfcRetries) {
            setState(() {
              _alertMessage = "Đang thử kết nối lại... ($_nfcRetryCount/$maxNfcRetries)";
            });
            await Future.delayed(retryDelay);
          } else {
            throw TimeoutException("Không thể kết nối sau nhiều lần thử");
          }
        }
      }

      final passport = Passport(_nfc);
      setState(() {
        _alertMessage = "Đang đọc dữ liệu...";
      });

      final mrtdData = MrtdData();

      try {
        _nfc.setIosAlertMessage("Đang đọc EF.CardAccess ...");
        mrtdData.cardAccess = await passport.readEfCardAccess();
        print("CardAccess data: ${mrtdData.cardAccess?.toBytes().hex()}");
      } catch (e) {
        print("Error reading CardAccess: $e");
      }

      _nfc.setIosAlertMessage("Đang thiết lập kết nối an toàn...");

      try {
        print("Bắt đầu phiên PACE...");
        await passport.startSessionPACE(accessKey, mrtdData.cardAccess!)
            .timeout(readTimeout);
        print("PACE thành công!");
        mrtdData.isPACE = true;
      } catch (e) {
        print("Lỗi PACE: $e");
        setState(() {
          if (e is TimeoutException) {
            _alertMessage = "Quá thời gian chờ kết nối PACE. Vui lòng thử lại.";
          } else {
            _alertMessage = "Không thể kết nối! Vui lòng kiểm tra lại mã CAN.";
          }
        });
        return;
      }

      _nfc.setIosAlertMessage("Đang đọc dữ liệu chính...");

      try {
        mrtdData.dg1 = await passport.readEfDG1();
        print("Đọc DG1 thành công");
      } catch (e) {
        print("Lỗi đọc DG1: $e");
      }

      try {
        mrtdData.dg11 = await passport.readEfDG11();
        print("Đọc DG11 thành công");
      } catch (e) {
        print("Lỗi đọc DG11: $e");
      }
      try {
        _nfc.setIosAlertMessage("Đang đọc EF.COM ...");
        mrtdData.com = await passport.readEfCOM();
        print("Đọc COM thành công: ${mrtdData.com!.dgTags}");
      } catch (e) {
        print("Lỗi đọc EF.COM: $e");
      }

      try {
        if (mrtdData.com != null && mrtdData.com!.dgTags.contains(EfDG2.TAG)) {
          mrtdData.dg2 = await passport.readEfDG2();
          print("Đọc DG2 thành công");
        }
      } catch (e) {
        print("Lỗi đọc DG2: $e");
      }

      setState(() {
        _mrtdData = mrtdData;
        _alertMessage = "";
      });

    } catch (e) {
      final se = e.toString().toLowerCase();
      String alertMsg = "Đã xảy ra lỗi khi đọc CCCD!";

      if (se.contains('timeout')) {
        alertMsg = "Quá thời gian chờ kết nối. Vui lòng thử lại và giữ điện thoại gần CCCD hơn.";
      } else if (se.contains("tag was lost")) {
        alertMsg = "Mất kết nối! Vui lòng thử lại.";
      } else if (se.contains("many times")) {
        alertMsg = "Không thể kết nối sau nhiều lần thử. Vui lòng kiểm tra vị trí CCCD.";
      }
      print("NFC error details: $e");

      setState(() {
        _alertMessage = alertMsg;
      });

    } finally {
      await _cleanupNfc();
      if (_alertMessage.isNotEmpty) {
        await _nfc.disconnect(iosErrorMessage: _alertMessage);
      } else {
        await _nfc.disconnect(
          iosAlertMessage: "Hoàn tất!",
        );
      }
    }
  }

  bool _disabledInput() {
    return _isReading || !_isNfcAvailable;
  }

  List<Widget> _mrtdDataWidgets() {
    List<Widget> list = [];
    if (_mrtdData == null) return list;

    if (_mrtdData!.isPACE != null && _mrtdData!.isDBA != null)
      list.add(
        _makeMrtdAccessDataWidget(
          header: "Access protocol",
          collapsedText: '',
          isDBA: _mrtdData!.isDBA!,
          isPACE: _mrtdData!.isPACE!,
        ),
      );

    if (_mrtdData!.cardAccess != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.CardAccess',
          collapsedText: '',
          dataText: _mrtdData!.cardAccess!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.cardSecurity != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.CardSecurity',
          collapsedText: '',
          dataText: _mrtdData!.cardSecurity!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.sod != null) {
      extractCertificatesFromSOD(_mrtdData!.sod!.toBytes());
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.SOD',
          collapsedText: '',
          dataText: base64Encode(_mrtdData!.sod!.toBytes()),
        ),
      );
    }

    if (_mrtdData!.com != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.COM',
          collapsedText: '',
          dataText: formatEfCom(_mrtdData!.com!),
        ),
      );
    }

    if (_mrtdData!.dg1 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG1',
          collapsedText: '',
          dataText: formatMRZ(_mrtdData!.dg1!.mrz),
        ),
      );
    }

    if (_mrtdData!.dg2 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG2',
          collapsedText: '',
          dataText: _mrtdData!.dg2!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.dg3 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG3',
          collapsedText: '',
          dataText: _mrtdData!.dg3!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.dg4 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG4',
          collapsedText: '',
          dataText: _mrtdData!.dg4!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.dg5 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG5',
          collapsedText: '',
          dataText: _mrtdData!.dg5!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.dg6 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG6',
          collapsedText: '',
          dataText: _mrtdData!.dg6!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.dg7 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG7',
          collapsedText: '',
          dataText: _mrtdData!.dg7!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.dg8 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG8',
          collapsedText: '',
          dataText: _mrtdData!.dg8!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.dg9 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG9',
          collapsedText: '',
          dataText: _mrtdData!.dg9!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.dg10 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG10',
          collapsedText: '',
          dataText: _mrtdData!.dg10!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.dg11 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG11',
          collapsedText: '',
          dataText: _mrtdData!.dg11!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.dg12 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG12',
          collapsedText: '',
          dataText: _mrtdData!.dg12!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.dg13 != null) {
      readEfDG13(_mrtdData!.dg13!.toBytes());
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG13',
          collapsedText: '',
          dataText: _mrtdData!.dg13!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.dg14 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG14',
          collapsedText: '',
          dataText: _mrtdData!.dg14!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.dg15 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG15',
          collapsedText: '',
          dataText: _mrtdData!.dg15!.toBytes().hex(),
        ),
      );
    }

    if (_mrtdData!.aaSig != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'Active Authentication signature',
          collapsedText: '',
          dataText: _mrtdData!.aaSig!.hex(),
        ),
      );
    }

    if (_mrtdData!.dg16 != null) {
      list.add(
        _makeMrtdDataWidget(
          header: 'EF.DG16',
          collapsedText: '',
          dataText: _mrtdData!.dg16!.toBytes().hex(),
        ),
      );
    }

    return list;
  }

  @override
  Widget build(BuildContext context) {
    return PlatformScaffold(
      appBar: PlatformAppBar(title: Text('Đọc Thông Tin CCCD'),
        trailingActions: <Widget>[
          PlatformIconButton(
            icon: Icon(Icons.settings, color: Colors.white),
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const SettingsScreen()),
              );
            },
          ),
        ],
      ),
      body: Material(
        child: SafeArea(
          child: SingleChildScrollView(
            controller: _scrollController,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                _buildForm(context),
                Padding(
                  padding: EdgeInsets.all(16.0),
                  child: Column(
                    children: [
                      Row(
                        children: <Widget>[
                          Text(
                            'NFC:',
                            style: TextStyle(
                              fontSize: 18.0,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          SizedBox(width: 4),
                          Text(
                            _isNfcAvailable ? "Sẵn sàng" : "Không khả dụng",
                            style: TextStyle(fontSize: 18.0),
                          ),
                        ],
                      ),
                      if (_alertMessage.isNotEmpty) ...[
                        SizedBox(height: 16),
                        Container(
                          padding: const EdgeInsets.all(8),
                          child: Text(
                            _alertMessage,
                            textAlign: TextAlign.center,
                            style: const TextStyle(
                              fontSize: 15.0,
                              fontWeight: FontWeight.bold,
                              color: Colors.red,
                            ),
                          ),
                        ),
                      ],
                      if (_mrtdData != null) ...[
                        SizedBox(height: 24),
                        Text(
                          "Thông tin CCCD:",
                          style: TextStyle(
                            fontSize: 18.0,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        SizedBox(height: 16),
                        _buildCccdInfo(),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildForm(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16.0),
      child: Form(
        key: _canData,
        child: Column(
          children: <Widget>[
            Container(
              width: double.infinity,
              margin: const EdgeInsets.only(bottom: 16.0),
              child: ElevatedButton.icon(
                onPressed: _isReading ? null : () => _navigateToQrScanner(),
                icon: Icon(Icons.qr_code_scanner),
                label: Text('Quét mã QR trên CCCD'),
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 12.0),
                ),
              ),
            ),
            
            Container(
              width: double.infinity,
              margin: const EdgeInsets.only(bottom: 16.0),
              child: ElevatedButton.icon(
                onPressed: _isReading ? null : _testNfc,
                icon: Icon(Icons.nfc),
                label: Text('Test NFC'),
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 12.0),
                ),
              ),
            ),
            
            Container(
              width: double.infinity,
              margin: const EdgeInsets.only(bottom: 16.0),
              child: ElevatedButton.icon(
                onPressed: _isReading ? null : _navigateToFaceCompare,
                icon: Icon(Icons.face),
                label: Text('So sánh khuôn mặt'),
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 12.0),
                ),
              ),
            ),
            
            TextFormField(
              enabled: false, 
              controller: _can,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                labelText: 'Mã CAN (6 số cuối của số định danh)',
                fillColor: Colors.white,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCccdInfo() {
    final dg1 = _mrtdData?.dg1;
    final dg11 = _mrtdData?.dg11;

    return Card(
      child: Padding(
        padding: EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (dg1 != null) ...[
              _buildInfoRow("Số CCCD:", dg1.mrz.documentNumber),
              _buildInfoRow("Họ và tên:", "${dg1.mrz.lastName} ${dg1.mrz.firstName}"),
              _buildInfoRow("Ngày sinh:", DateFormat('dd/MM/yyyy').format(dg1.mrz.dateOfBirth)),
              _buildInfoRow("Giới tính:", dg1.mrz.gender == "F" ? "Nữ" : "Nam"),
              _buildInfoRow("Quốc tịch:", dg1.mrz.nationality),
              _buildInfoRow("Ngày hết hạn:", DateFormat('dd/MM/yyyy').format(dg1.mrz.dateOfExpiry)),
            ],
            if (dg11 != null) ...[
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: TextStyle(
                fontWeight: FontWeight.bold,
                color: Colors.grey[700],
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: TextStyle(
                fontSize: 16.0,
              ),
            ),
          ),
        ],
      ),
    );
  }

  DateTime? _getDOBDate() {
    try {
      final parts = _dob.text.split('/');
      return DateTime(
        int.parse(parts[2]),
        int.parse(parts[1]),
        int.parse(parts[0])
      );
    } catch (e) {
      return null;
    }
  }

  DateTime? _getDOEDate() {
    try {
      final parts = _doe.text.split('/');
      return DateTime(
        int.parse(parts[2]),
        int.parse(parts[1]),
        int.parse(parts[0])
      );
    } catch (e) {
      return null;
    }
  }

  Widget _makeMrtdAccessDataWidget({
    required String header,
    required String collapsedText,
    required bool isPACE,
    required bool isDBA,
  }) {
    return Card(
      child: ListTile(
        title: Text(header),
        subtitle: Text(
          'PACE: ${isPACE ? "Yes" : "No"}\n'
          'DBA: ${isDBA ? "Yes" : "No"}'
        ),
      ),
    );
  }

  Widget _makeMrtdDataWidget({
    required String header,
    required String collapsedText,
    required String dataText,
  }) {
    return Card(
      child: ListTile(
        title: Text(header),
        subtitle: Text(dataText),
      ),
    );
  }

  void extractCertificatesFromSOD(Uint8List bytes) {
  }

  String formatEfCom(EfCOM com) {
    final buffer = StringBuffer();
    buffer.writeln('Version: ${com.version}');
    buffer.writeln('Unicode Version: ${com.unicodeVersion}');
    buffer.writeln('DG Tags: ${com.dgTags}');
    return buffer.toString();
  }

  String formatMRZ(MRZ mrz) {
    final buffer = StringBuffer();
    buffer.writeln('Document Code: ${mrz.documentCode}');
    buffer.writeln('Document Number: ${mrz.documentNumber}');
    buffer.writeln('Country: ${mrz.country}');
    buffer.writeln('Nationality: ${mrz.nationality}');
    buffer.writeln('Name: ${mrz.lastName} ${mrz.firstName}');
    buffer.writeln('Gender: ${mrz.gender}');
    buffer.writeln('Birth Date: ${mrz.dateOfBirth}');
    buffer.writeln('Expiry Date: ${mrz.dateOfExpiry}');
    return buffer.toString();
  }

  void readEfDG13(Uint8List bytes) {
  }

  void _navigateToMrzScanner() async {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder:
            (context) => MrzScannerScreen(
              onMrzDataReceived: (docNumber, dob, doe) {
                setState(() {
                  _docNumber.text = docNumber;
                  _dob.text = dob;
                  _doe.text = doe;
                });
              },
            ),
      ),
    );
  }
  void _testNfc() async {
    setState(() {
      _alertMessage = "Đang kiểm tra NFC...";
      _isReading = true;
    });
    try {
      final status = await NfcProvider.nfcStatus;
      if (status != NfcStatus.enabled) {
        setState(() {
          _alertMessage = "NFC chưa được bật hoặc không khả dụng.";
          _isReading = false;
        });
        return;
      }
      await _nfc.connect(timeout: Duration(seconds: 20), iosAlertMessage: "Đặt CCCD gần điện thoại");
      
      setState(() {
        _alertMessage = "Đã kết nối NFC thành công.";
      });
      
      try {
        
        final selectCmd = Uint8List.fromList([0x00, 0xA4, 0x00, 0x0C, 0x02, 0x3F, 0x00]);
        final resp = await _nfc.transceive(selectCmd);
        print("SELECT response: ${resp.hex()}");
        setState(() {
          _alertMessage += "\nSELECT resp: ${resp.hex()}";
        });
      } catch (e) {
        print("Transceive error: $e");
        setState(() {
          _alertMessage += "\nTransceive error: $e";
        });
      }
      await _nfc.disconnect();
    } catch (e) {
      print("NFC test error: $e");
      setState(() {
        _alertMessage = "Lỗi kiểm tra NFC: $e";
      });
    } finally {
      setState(() {
        _isReading = false;
      });
    }
  }
}