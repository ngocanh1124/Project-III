import 'dart:convert';
import 'dart:typed_data';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'ConfigService.dart';

class FaceCompareService {
  static const String _baseUrl = "http://192.168.1.6:8080"; 
  static const String _apiUrl = "$_baseUrl/api/attendance/check";

  static Future<Map<String, dynamic>?> compareFaces({
    required Uint8List chipImage,
    required Uint8List selfieImage,
    String? cccd,
    String? fullname,
  }) async {
    try {
      String organizationId = await ConfigService.getOrgId();

      final request = http.MultipartRequest('POST', Uri.parse(_apiUrl));
      
      request.files.add(http.MultipartFile.fromBytes(
        'chip', chipImage, filename: 'chip.jpg', contentType: MediaType('image', 'jpeg')
      ));
      request.files.add(http.MultipartFile.fromBytes(
        'selfie', selfieImage, filename: 'selfie.jpg', contentType: MediaType('image', 'jpeg')
      ));

      request.fields['organizationId'] = organizationId;

      if (cccd != null && cccd.isNotEmpty) {
        request.fields['cccd'] = cccd;
      }
      if (fullname != null && fullname.isNotEmpty) {
        request.fields['fullname'] = fullname;
      }

      final streamedResponse = await request.send().timeout(const Duration(seconds: 60));
      final response = await http.Response.fromStream(streamedResponse);

      if (response.statusCode == 200) {
        return jsonDecode(response.body) as Map<String, dynamic>;
      } else {
        return null;
      }
    } catch (e) {
      return null;
    }
  }
}