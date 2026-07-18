import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'package:mqtt_client/mqtt_client.dart';
import 'package:mqtt_client/mqtt_server_client.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'ConfigService.dart';
import 'LocalDatabaseService.dart';
import 'OfflineSyncService.dart';

class MqttClientService {
  static MqttServerClient? _client;
  static String? _deviceCode;
  static String? _clientUid; // Unique suffix stored in prefs – prevents session takeover
  // Lưu callback để tự reconnect
  static Function(Map<String, dynamic>)? _savedCallback;
  static bool _isReconnecting = false;
  static bool _isInitializing = false; // Guard against concurrent initialize calls

  // HiveMQ Cloud Configuration
  static const String MQTT_BROKER = 'd6d92a4a5dda42df88c5e1a584a37bca.s1.eu.hivemq.cloud';
  static const int MQTT_PORT = 8883;
  static const String MQTT_USERNAME = 'NgocAnh';
  static const String MQTT_PASSWORD = 'i9NEe#ufX4r.yxx';
  static const String MQTT_TOPIC_PREFIX = 'cccd/devices';

  static bool get isConnected =>
      _client != null &&
      _client!.connectionStatus?.state == MqttConnectionState.connected;

  /// Cập nhật callback xử lý lệnh MQTT mà không cần reconnect.
  /// Gọi khi màn hình mới active muốn nhận OPEN_DOOR.
  static void updateCallback(Function(Map<String, dynamic>) onCommandReceived) {
    _savedCallback = onCommandReceived;
  }

  /// Buộc kết nối lại với device code mới từ SharedPreferences.
  /// Gọi sau khi người dùng thay đổi device code trong Settings.
  static Future<void> reinitialize() async {
    if (_savedCallback != null) {
      await initialize(_savedCallback!);
    }
  }

  static Future<void> initialize(Function(Map<String, dynamic>) onCommandReceived) async {
    // Prevent concurrent initialization calls causing the reconnect storm
    if (_isInitializing) {
      _savedCallback = onCommandReceived;
      return;
    }
    _isInitializing = true;

    _savedCallback = onCommandReceived;
    _deviceCode = await ConfigService.getDeviceCode();

    // Load or create a unique client UID stored in SharedPreferences.
    // This prevents broker session-takeover when initialize() is called
    // from multiple places (main.dart + attendance_flow_screen).
    if (_clientUid == null) {
      final prefs = await SharedPreferences.getInstance();
      _clientUid = prefs.getString('mqtt_client_uid');
      if (_clientUid == null) {
        _clientUid = Random().nextInt(0xFFFFFFFF).toRadixString(16);
        await prefs.setString('mqtt_client_uid', _clientUid!);
      }
    }

    // Detach disconnect callback from old client BEFORE creating new one.
    // Without this, when the new client connects and the broker kicks the old
    // client (same clientId), the old client fires _onDisconnected → schedules
    // another initialize() → kicks the new client → infinite loop.
    if (_client != null) {
      _client!.onDisconnected = null; // prevent old client from triggering reconnect
      try { _client!.disconnect(); } catch (_) {}
    }
    _client = MqttServerClient(MQTT_BROKER, 'flutter_${_deviceCode}_$_clientUid');
    _client!.port = MQTT_PORT;
    _client!.keepAlivePeriod = 30;
    _client!.onDisconnected = _onDisconnected;
    _client!.onConnected = _onConnected;
    _client!.onSubscribed = _onSubscribed;
    
    // Configure TLS/SSL
    _client!.secure = true;
    _client!.onBadCertificate = (dynamic certificate) {
      return true; // Accept self-signed certificates (for cloud brokers)
    };

    final connMess = MqttConnectMessage()
        .withClientIdentifier('flutter_${_deviceCode}_$_clientUid')
        .authenticateAs(MQTT_USERNAME, MQTT_PASSWORD)
        .startClean() // Clean session: avoids broker replaying stale queued messages
        .withWillTopic('$MQTT_TOPIC_PREFIX/$_deviceCode/status') 
        .withWillMessage('offline')
        .withWillQos(MqttQos.atLeastOnce);

    _client!.connectionMessage = connMess;

    try {
      print('MQTT: Đang kết nối tới $MQTT_BROKER:$MQTT_PORT...');
      await _client!.connect();
    } on SocketException catch (e) {
      print('MQTT Socket Exception: $e');
      _client!.disconnect();
      _isInitializing = false;
      return;
    } catch (e) {
      print('MQTT Exception: $e');
      _client!.disconnect();
      _isInitializing = false;
      return;
    } finally {
      _isInitializing = false;
    }

    _client!.updates!.listen((List<MqttReceivedMessage<MqttMessage>> c) async {
      final MqttPublishMessage recMess = c[0].payload as MqttPublishMessage;
      final String payload = MqttPublishPayload.bytesToStringAsString(recMess.payload.message);

      print('MQTT: Nhận lệnh từ Server: $payload');
      try {
        final Map<String, dynamic> data = jsonDecode(payload);
        final action = data['action']?.toString();
        
        if (action == 'OPEN_DOOR' || action == 'START_AUTH_FLOW') {
          print('MQTT: Forwarding action $action to callback (Callback exists: ${_savedCallback != null})');
          _savedCallback?.call(data); 
        }
        else if (action == 'SYNC_DATA') {
          List<dynamic> employees = data['employees'] ?? [];
          await LocalDatabaseService.syncPermissions(employees);
          print("Đã cập nhật danh sách nhân viên Offline thành công! (${employees.length} NV)");
        }
        else if (c[0].topic == TOPIC_SERVER_IP && data['ip'] != null) {
          final newIp = data['ip'] as String;
          await ConfigService.setServerIp(newIp);
          print('MQTT: Tự động cập nhật Server IP -> $newIp');
          // Đợi 7s để đảm bảo HTTP sync cũ (với IP cũ) đã timeout (5s) trước khi retry
          Future.delayed(const Duration(seconds: 7), () async {
            final result = await OfflineSyncService.syncPending();
            final sent = result['sent'] ?? 0;
            final failed = result['failed'] ?? 0;
            if (sent > 0) {
              print('MQTT IP update: đã sync $sent bản ghi offline lên server $newIp');
            }
            if (failed > 0) {
              // HTTP vẫn fail → thử sync qua MQTT
              syncOfflineViaMqtt();
            }
          });
        }
      } catch (e) {
        print("Lỗi xử lý dữ liệu MQTT: $e");
      }
    });
  }

  static const String TOPIC_SERVER_IP = 'cccd/devices/system/ip';

  static void _onConnected() {
    print('MQTT: Đã kết nối thành công tới HiveMQ Cloud! (Device: $_deviceCode)');
    _client!.subscribe('$MQTT_TOPIC_PREFIX/$_deviceCode/command', MqttQos.atLeastOnce);
    _client!.subscribe('$MQTT_TOPIC_PREFIX/broadcast/command', MqttQos.atLeastOnce);
    // Tín hiệu IP server hiện đã được dời vào cccd/devices/system/ip (cho phép bởi HiveMQ)
    _client!.subscribe(TOPIC_SERVER_IP, MqttQos.atLeastOnce);
    print('MQTT: Subscribed to topics');
    // Flush offline queue: thử HTTP trước, nếu fail thì sync qua MQTT
    OfflineSyncService.syncPending().then((result) {
      final sent = result['sent'] ?? 0;
      final failed = result['failed'] ?? 0;
      if (sent > 0) {
        print('MQTT đã kết nối: flush $sent bản ghi offline lên server qua HTTP');
      }
      if (failed > 0) {
        // HTTP không tới được server (khác mạng WiFi) → sync qua MQTT
        print('MQTT: HTTP sync thất bại $failed bản ghi, chuyển sang sync qua MQTT...');
        syncOfflineViaMqtt();
      }
    });
  }

  static void _onDisconnected() {
    print('MQTT: Bị ngắt kết nối. Đang lên lịch kết nối lại...');
    if (_isReconnecting) return;
    _isReconnecting = true;
    Future.delayed(const Duration(seconds: 5), () async {
      _isReconnecting = false;
      if (_savedCallback != null && !isConnected) {
        print('MQTT: Đang thử kết nối lại...');
        await initialize(_savedCallback!);
      }
    });
  }

  static void _onSubscribed(String topic) {
    print('MQTT: Đã lắng nghe kênh $topic');
  }
  
  static void publish(String topic, String message) {
    if (_client == null || _client!.connectionStatus!.state != MqttConnectionState.connected) {
      print('MQTT: Client not connected');
      return;
    }
    final builder = MqttClientPayloadBuilder();
    builder.addString(message);
    _client?.publishMessage(topic, MqttQos.atLeastOnce, builder.payload!);
    print('MQTT: Published to $topic');
  }

  /// Publish các bản ghi offline trong SQLite lên MQTT thay vì HTTP.
  /// Dùng khi Android ở mạng khác với server (HTTP không tới được).
  static Future<void> syncOfflineViaMqtt() async {
    if (!isConnected || _deviceCode == null) return;
    try {
      final pending = await LocalDatabaseService.getPendingAttendance();
      if (pending.isEmpty) return;

      for (final record in pending) {
        final devCode = record['device_code'] ?? _deviceCode;
        final payload = jsonEncode({
          'cccd':          record['cccd'] ?? '',
          'deviceCode':    devCode,
          'capturedName':  record['full_name'] ?? '',
          'matched':       record['matched'] == 1,
          'score':         record['score'] ?? 0.0,
          'layer1Passed':  record['matched'] == 1,
          'layer1Score':   record['score'] ?? 0.0,
          'layer1Method':  record['method'] ?? 'OFFLINE_L1',
          'method':        'MQTT_OFFLINE_SYNC',
          'imageLive':     record['selfie_image'] ?? '',
          'chipImage':     '',
        });
        publish('$MQTT_TOPIC_PREFIX/$devCode/attendance', payload);
      }

      final ids = pending.map((r) => r['id'] as int).toList();
      await LocalDatabaseService.markSynced(ids);
      await LocalDatabaseService.cleanOldSynced();
      print('MqttSync: đã sync ${pending.length} bản ghi offline qua MQTT');
    } catch (e) {
      print('MqttSync: lỗi $e');
    }
  }
}