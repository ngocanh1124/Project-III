import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'ConfigService.dart';

class LocalDatabaseService {
  static Database? _db;

  static Future<Database> get database async {
    if (_db != null) return _db!;
    _db = await _initDb();
    return _db!;
  }

  static Future<Database> _initDb() async {
    String path = join(await getDatabasesPath(), 'attendance_offline.db');
    return await openDatabase(
      path,
      version: 2,
      onCreate: (db, version) async {
        await _createTables(db);
      },
      onUpgrade: (db, oldVersion, newVersion) async {
        if (oldVersion < 2) {
          await _createPendingTable(db);
        }
      },
    );
  }

  static Future<void> _createTables(Database db) async {
    await db.execute('''
      CREATE TABLE permissions (
        cccd TEXT PRIMARY KEY,
        full_name TEXT,
        start_time TEXT,
        end_time TEXT,
        is_active INTEGER
      )
    ''');
    await _createPendingTable(db);
  }

  static Future<void> _createPendingTable(Database db) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS pending_attendance (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        cccd TEXT,
        full_name TEXT,
        device_code TEXT,
        matched INTEGER,
        score REAL,
        method TEXT,
        scan_time TEXT,
        selfie_image TEXT,
        synced INTEGER DEFAULT 0
      )
    ''');
  }

  // ── Permissions ──────────────────────────────────────────────────────────

  /// Chuẩn hóa CCCD về 9 số cuối để khớp với chip NFC (MRZ TD1 trả 9 số cuối).
  /// Web dashboard vẫn hiển thị đủ 12 số vì server log dùng employee.getCccd() từ DB server.
  static String _to9Digit(dynamic cccd) {
    final s = (cccd ?? '').toString().replaceAll('<', '').trim();
    return s.length > 9 ? s.substring(s.length - 9) : s;
  }

  static Future<void> syncPermissions(List<dynamic> employees) async {
    final db = await database;
    Batch batch = db.batch();
    batch.delete('permissions'); 
    
    for (var emp in employees) {
      batch.insert('permissions', {
        'cccd': _to9Digit(emp['cccd']),
        'full_name': emp['full_name'],
        'start_time': emp['start_time'] ?? '06:00:00',
        'end_time': emp['end_time'] ?? '22:00:00',
        'is_active': 1
      });
    }
    await batch.commit();
  }

  /// Tìm CCCD trong DB local.
  /// Chip NFC MRZ TD1 trả 9 số cuối → DB cũng lưu 9 số cuối → exact match.
  /// Server log vẫn dùng 12 số đầy đủ (từ employee.getCccd() trên server).
  static Future<String?> findMatchingCccd(String chipCccd) async {
    final db = await database;
    final String trimmed = _to9Digit(chipCccd);

    List<Map> all = await db.query('permissions', where: 'is_active = 1');
    print('LocalDB: chip="$trimmed" (9-digit), DB has ${all.length} rows: ${all.map((r) => r["cccd"]).toList()}');

    // 1. Khớp chính xác (cả 2 đều là 9 số → luôn match nếu có quyền)
    List<Map> exact = await db.query('permissions',
        where: 'cccd = ? AND is_active = 1', whereArgs: [trimmed]);
    if (exact.isNotEmpty) return trimmed;

    // 2. Suffix fallback (dữ liệu cũ chưa được re-sync)
    List<Map> suffix = await db.rawQuery(
        "SELECT cccd FROM permissions WHERE cccd LIKE ? AND is_active = 1",
        ['%$trimmed']);
    if (suffix.isNotEmpty) {
      final matched = suffix.first['cccd'] as String;
      print('LocalDB: suffix match! chip="$trimmed" -> DB="$matched"');
      return matched;
    }

    print('LocalDB: NO match for "$trimmed"');
    return null;
  }

  static Future<bool> checkAccess(String cccd) async {
    return (await findMatchingCccd(cccd)) != null;
  }

  /// Fetch permissions từ server qua HTTP (fallback khi MQTT chưa gửi SYNC_DATA)
  /// Gọi khi app khởi động để đảm bảo danh sách luôn cập nhật
  static Future<void> fetchPermissionsFromServer(String deviceCode) async {
    try {
      final String baseUrl = ConfigService.baseUrl;
      final url = Uri.parse('$baseUrl/api/v1/device/$deviceCode/whitelist');
      final response = await http.get(url).timeout(const Duration(seconds: 5));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final List<dynamic> employees = data['data'] ?? [];

        final db = await database;
        Batch batch = db.batch();
        batch.delete('permissions');
        for (var emp in employees) {
          batch.insert('permissions', {
            'cccd': _to9Digit(emp['cccd']),
            'full_name': emp['fullName'] ?? emp['full_name'] ?? '',
            'start_time': '06:00:00',
            'end_time': '22:00:00',
            'is_active': 1,
          });
        }
        await batch.commit();
        print('LocalDB: Synced ${employees.length} permissions from server via HTTP (9-digit CCCD)');
      }
    } catch (e) {
      print('LocalDB: HTTP permission sync failed (offline?): $e');
    }
  }

  // ── Pending Attendance (offline queue) ─────────────────────────────────

  /// Lưu kết quả quét vào hàng đợi offline khi mất kết nối
  static Future<void> savePendingAttendance({
    required String cccd,
    required String fullName,
    required String deviceCode,
    required bool matched,
    required double score,
    required String method,
    String? selfieImage,
  }) async {
    final db = await database;
    await db.insert('pending_attendance', {
      'cccd': cccd,
      'full_name': fullName,
      'device_code': deviceCode,
      'matched': matched ? 1 : 0,
      'score': score,
      'method': method,
      'scan_time': DateTime.now().toIso8601String(),
      'selfie_image': selfieImage ?? '',
      'synced': 0,
    });
  }

  /// Lấy tất cả bản ghi chưa đồng bộ (tối đa 50 bản ghi gần nhất)
  static Future<List<Map<String, dynamic>>> getPendingAttendance() async {
    final db = await database;
    final result = await db.query(
      'pending_attendance',
      where: 'synced = 0',
      orderBy: 'scan_time ASC',
      limit: 50,
    );
    return result.map((r) => Map<String, dynamic>.from(r)).toList();
  }

  /// Đánh dấu đã đồng bộ thành công
  static Future<void> markSynced(List<int> ids) async {
    if (ids.isEmpty) return;
    final db = await database;
    await db.update(
      'pending_attendance',
      {'synced': 1},
      where: 'id IN (${ids.map((_) => '?').join(',')})',
      whereArgs: ids,
    );
  }

  /// Xoá các bản ghi đã đồng bộ cũ hơn 7 ngày
  static Future<void> cleanOldSynced() async {
    final db = await database;
    final cutoff = DateTime.now().subtract(const Duration(days: 7)).toIso8601String();
    await db.delete(
      'pending_attendance',
      where: 'synced = 1 AND scan_time < ?',
      whereArgs: [cutoff],
    );
  }

  /// Đếm số bản ghi đang chờ đồng bộ
  static Future<int> getPendingCount() async {
    final db = await database;
    final result = await db.rawQuery('SELECT COUNT(*) FROM pending_attendance WHERE synced = 0');
    return Sqflite.firstIntValue(result) ?? 0;
  }
}