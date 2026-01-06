import 'package:shared_preferences/shared_preferences.dart';

class ConfigService {
  static Future<void> saveOrgId(String id) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('org_id', id);
  }

  static Future<String> getOrgId() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString('org_id') ?? "1";
  }
}