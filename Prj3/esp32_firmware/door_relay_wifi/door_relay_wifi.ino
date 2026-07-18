/**
 * ============================================================
 *  ESP32 Door Relay Controller — WiFi (TCP Socket)
 * ============================================================
 *
 * Chức năng:
 *   - ESP32 kết nối WiFi và lắng nghe TCP trên port 7777.
 *   - Android (sau khi nhận lệnh OPEN_DOOR từ MQTT Server) kết nối TCP
 *     đến IP của ESP32 trên cùng mạng WiFi và gửi lệnh JSON.
 *   - Relay đóng mạch → mở khóa cửa trong N giây.
 *
 * Phần cứng:
 *   - ESP32 DevKit
 *   - Relay Module 5V (kết nối GPIO 26)
 *   - LED trạng thái (GPIO 2)
 *
 * Thư viện:
 *   - WiFi.h (built-in ESP32 core)
 *   - ArduinoJson (Library Manager: "ArduinoJson" by Benoit Blanchon)
 *
 * Giao thức lệnh (JSON trên TCP, kết thúc bằng '\n'):
 *   {"action":"OPEN_DOOR","duration_ms":3000}\n  → mở cửa 3 giây
 *   {"action":"CLOSE_DOOR"}\n                   → đóng ngay
 *   {"action":"PING"}\n                         → kiểm tra kết nối
 *
 * Phản hồi (JSON + '\n'):
 *   {"ok":true,"action":"OPEN_DOOR","duration_ms":3000}\n
 *
 * Lưu ý:
 *   - Android cần biết IP của ESP32 (xem Serial Monitor khi khởi động).
 *   - Để IP cố định, cài đặt trong router (DHCP static lease) hoặc dùng
 *     mDNS với hostname "door-relay.local" (xem option ở cuối file).
 *
 * ============================================================
 */

#include <WiFi.h>
#include <ArduinoJson.h>

// ── Cấu hình WiFi ─────────────────────────────────────────────────────────────
// ** SỬA HAI DÒNG NÀY trước khi nạp firmware **
const char* WIFI_SSID     = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// ── Cấu hình server TCP ───────────────────────────────────────────────────────
#define TCP_PORT 7777

// ── Cấu hình phần cứng ───────────────────────────────────────────────────────
#define RELAY_PIN          26
#define STATUS_LED_PIN      2
#define RELAY_ACTIVE_HIGH  true

// ── Hằng số ───────────────────────────────────────────────────────────────────
#define DEFAULT_OPEN_DURATION_MS  3000
#define MAX_OPEN_DURATION_MS     30000

// ── Trạng thái toàn cục ───────────────────────────────────────────────────────
WiFiServer tcpServer(TCP_PORT);
bool          relayActive    = false;
unsigned long relayCloseTime = 0;

// ── Điều khiển relay ─────────────────────────────────────────────────────────
void activateRelay(int duration_ms) {
    relayActive    = true;
    relayCloseTime = millis() + duration_ms;
    digitalWrite(RELAY_PIN, RELAY_ACTIVE_HIGH ? HIGH : LOW);
    Serial.printf("[RELAY] MỞ CỬA trong %d ms\n", duration_ms);
}

void deactivateRelay() {
    relayActive = false;
    digitalWrite(RELAY_PIN, RELAY_ACTIVE_HIGH ? LOW : HIGH);
    Serial.println("[RELAY] ĐÓNG CỬA");
}

// ── Xử lý lệnh JSON ──────────────────────────────────────────────────────────
String handleCommand(const String& jsonStr) {
    StaticJsonDocument<256> doc;
    DeserializationError err = deserializeJson(doc, jsonStr);
    if (err) {
        return String("{\"ok\":false,\"error\":\"JSON_PARSE_ERROR\"}\n");
    }

    const char* action = doc["action"];
    if (!action) return String("{\"ok\":false,\"error\":\"MISSING_ACTION\"}\n");

    if (strcmp(action, "OPEN_DOOR") == 0) {
        int duration = doc["duration_ms"] | DEFAULT_OPEN_DURATION_MS;
        if (duration > MAX_OPEN_DURATION_MS) duration = MAX_OPEN_DURATION_MS;
        activateRelay(duration);

        StaticJsonDocument<128> resp;
        resp["ok"]          = true;
        resp["action"]      = "OPEN_DOOR";
        resp["duration_ms"] = duration;
        String s;
        serializeJson(resp, s);
        return s + "\n";

    } else if (strcmp(action, "CLOSE_DOOR") == 0) {
        deactivateRelay();
        return String("{\"ok\":true,\"action\":\"CLOSE_DOOR\"}\n");

    } else if (strcmp(action, "PING") == 0) {
        StaticJsonDocument<128> resp;
        resp["ok"]     = true;
        resp["action"] = "PONG";
        resp["relay"]  = relayActive ? "ON" : "OFF";
        resp["ip"]     = WiFi.localIP().toString();
        String s;
        serializeJson(resp, s);
        return s + "\n";
    }

    return String("{\"ok\":false,\"error\":\"UNKNOWN_ACTION\"}\n");
}

// ── Setup ─────────────────────────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    Serial.println("=== ESP32 Door Relay WiFi Controller ===");

    pinMode(RELAY_PIN, OUTPUT);
    deactivateRelay();
    pinMode(STATUS_LED_PIN, OUTPUT);
    digitalWrite(STATUS_LED_PIN, LOW);

    // Kết nối WiFi
    Serial.printf("Đang kết nối WiFi: %s ...\n", WIFI_SSID);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 30) {
        delay(500);
        Serial.print(".");
        attempts++;
    }

    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("\n[WiFi] KẾT NỐI THẤT BẠI! Khởi động lại...");
        delay(3000);
        ESP.restart();
    }

    Serial.println("\n[WiFi] Đã kết nối!");
    Serial.print("[WiFi] IP: ");
    Serial.println(WiFi.localIP());

    // Khởi động TCP server
    tcpServer.begin();
    Serial.printf("[TCP] Lắng nghe trên port %d\n", TCP_PORT);
    digitalWrite(STATUS_LED_PIN, HIGH);
}

// ── Loop ──────────────────────────────────────────────────────────────────────
void loop() {
    // Tự động đóng relay
    if (relayActive && millis() >= relayCloseTime) {
        deactivateRelay();
    }

    // Chấp nhận kết nối TCP mới
    WiFiClient client = tcpServer.available();
    if (!client) return;

    Serial.printf("[TCP] Android kết nối từ %s\n", client.remoteIP().toString().c_str());
    String lineBuffer = "";
    unsigned long timeout = millis() + 5000;  // 5 giây timeout

    while (client.connected() && millis() < timeout) {
        while (client.available()) {
            char c = client.read();
            if (c == '\n') {
                lineBuffer.trim();
                if (lineBuffer.length() > 0) {
                    Serial.print("[TCP] Nhận: ");
                    Serial.println(lineBuffer);
                    String resp = handleCommand(lineBuffer);
                    client.print(resp);
                    Serial.print("[TCP] Phản hồi: ");
                    Serial.print(resp);
                }
                lineBuffer = "";
                timeout = millis() + 2000;  // gia hạn timeout sau mỗi lệnh
            } else {
                lineBuffer += c;
            }
        }
        delay(1);
    }
    client.stop();
    Serial.println("[TCP] Android ngắt kết nối");
}
