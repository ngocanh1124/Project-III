/**
 * ================================================================
 *  ESP32 Door Relay Controller – MQTT Direct (Wokwi Simulation)
 * ================================================================
 *
 *  Mô phỏng Wokwi: https://wokwi.com
 *  Dùng khi linh kiện thực chưa về nhưng cần kiểm thử hệ thống.
 *
 *  SƠ ĐỒ PHẦN CỨNG MÔ PHỎNG:
 *  ┌─────────────────────────────────────────────────────────┐
 *  │  ESP32 DevKit v1                                        │
 *  │  GPIO2  → LED xanh dương  (trạng thái WiFi/MQTT)       │
 *  │  GPIO25 → LED xanh lá     (cửa 1 đang mở)              │
 *  │  GPIO26 → LED đỏ + R220Ω  (rơ-le kênh 1 ← khóa từ 1)  │
 *  │  GPIO27 → LED đỏ + R220Ω  (rơ-le kênh 2 ← khóa từ 2)  │
 *  │  GPIO33 → LED xanh lá     (cửa 2 đang mở)              │
 *  │  GPIO32 → Buzzer           (còi báo)                    │
 *  └─────────────────────────────────────────────────────────┘
 *
 *  KIẾN TRÚC LUỒNG DỮ LIỆU:
 *  ┌─────────────┐  CCCD scan  ┌───────────────┐  MQTT cmd  ┌──────────┐
 *  │ Flutter App │ ──────────▶ │ Spring Boot   │ ─────────▶ │ ESP32    │
 *  │ (Android)   │             │ Backend 8080  │            │ (Wokwi)  │
 *  └─────────────┘             └───────┬───────┘            └────┬─────┘
 *                                      │ WebSocket               │ MQTT log
 *                              ┌───────▼───────┐                 │
 *                              │ React Dashboard│◀────────────────┘
 *                              │ localhost:3000 │
 *                              └───────────────┘
 *
 *  MQTT TOPICS:
 *  Subscribe:  cccd/devices/{DEVICE_CODE}/command
 *    {"action":"OPEN_DOOR","duration_ms":3000,"channel":1,"name":"Nguyễn Văn A","relay":true}
 *    {"action":"CLOSE_DOOR","channel":1}
 *    {"action":"PING"}
 *    {"action":"STATUS"}
 *
 *  Publish:    cccd/devices/{DEVICE_CODE}/esp32/log
 *    {"deviceCode":"...","action":"OPEN_DOOR","channel":1,"name":"...","success":true}
 *
 *  Publish:    cccd/devices/{DEVICE_CODE}/esp32/status  (heartbeat mỗi 30s)
 *    {"deviceCode":"...","relay1":"CLOSED","relay2":"CLOSED","online":true}
 *
 *  THƯ VIỆN CẦN THIẾT (Library Manager):
 *   - PubSubClient by Nick O'Leary  (v2.8+)
 *   - ArduinoJson by Benoit Blanchon (v6 or v7)
 *
 * ================================================================
 */

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

// ── WiFi ─────────────────────────────────────────────────────────────────────
// Wokwi: dùng "Wokwi-GUEST" (không cần password).
// Phần cứng thật: đổi thành SSID và mật khẩu WiFi thực.
#define WIFI_SSID     "Wokwi-GUEST"
#define WIFI_PASSWORD ""

// ── MQTT HiveMQ Cloud ─────────────────────────────────────────────────────────
#define MQTT_HOST "d6d92a4a5dda42df88c5e1a584a37bca.s1.eu.hivemq.cloud"
#define MQTT_PORT  8883          // SSL/TLS
#define MQTT_USER "NgocAnh"
#define MQTT_PASS "i9NEe#ufX4r.yxx"

// ── Mã thiết bị (phải khớp với deviceCode trong DB và MQTT topic) ─────────────
// Đổi thành mã thiết bị thực trong hệ thống
#define DEVICE_CODE "DEV01"

// ── GPIO pinout ───────────────────────────────────────────────────────────────
#define RELAY1_PIN   26   // Rơ-le kênh 1 (LED đỏ = relay cuộn đang kích)
#define RELAY2_PIN   27   // Rơ-le kênh 2 (LED đỏ = relay cuộn đang kích)
#define DOOR1_LED    25   // Đèn báo cửa 1 đang mở (LED xanh lá)
#define DOOR2_LED    33   // Đèn báo cửa 2 đang mở (LED xanh lá)
#define STATUS_LED    2   // Built-in LED: sáng = MQTT connected
#define BUZZER_PIN   32   // Còi báo

// ── Thông số hoạt động ────────────────────────────────────────────────────────
#define DEFAULT_OPEN_MS  3000    // Mặc định mở cửa 3 giây
#define MAX_OPEN_MS     30000    // Tối đa 30 giây
#define HEARTBEAT_MS    30000    // Gửi status mỗi 30 giây

// ── Topics (khởi tạo trong setup) ─────────────────────────────────────────────
char CMD_TOPIC[64];      // cccd/devices/DEVICE_CODE/command
char LOG_TOPIC[64];      // cccd/devices/DEVICE_CODE/esp32/log
char STATUS_TOPIC[64];   // cccd/devices/DEVICE_CODE/esp32/status

// ── Trạng thái rơ-le ─────────────────────────────────────────────────────────
struct RelayState {
    bool          active;
    unsigned long closeTime;
    int           durationMs;
    char          lastOpener[64];
};

RelayState relay1 = {false, 0, 0, ""}; // Kênh 1
RelayState relay2 = {false, 0, 0, ""}; // Kênh 2

// ── Biến toàn cục ─────────────────────────────────────────────────────────────
unsigned long lastHeartbeat   = 0;
unsigned long lastMqttRetry   = 0;
int           mqttRetryCount  = 0;

WiFiClientSecure wifiClient;
PubSubClient     mqttClient(wifiClient);


// ═══════════════════════════════════════════════════════════════════════════════
//  TIỆN ÍCH
// ═══════════════════════════════════════════════════════════════════════════════

void beep(int times, int ms = 100) {
    for (int i = 0; i < times; i++) {
        digitalWrite(BUZZER_PIN, HIGH);
        delay(ms);
        digitalWrite(BUZZER_PIN, LOW);
        if (i < times - 1) delay(80);
    }
}

void setStatusLed(bool on) {
    digitalWrite(STATUS_LED, on ? HIGH : LOW);
}


// ═══════════════════════════════════════════════════════════════════════════════
//  ĐIỀU KHIỂN RƠ-LE
// ═══════════════════════════════════════════════════════════════════════════════

void openRelay(int ch, int durationMs, const char* openerName) {
    RelayState& r = (ch == 2) ? relay2 : relay1;
    int relayPin  = (ch == 2) ? RELAY2_PIN : RELAY1_PIN;
    int doorLed   = (ch == 2) ? DOOR2_LED  : DOOR1_LED;

    if (durationMs <= 0 || durationMs > MAX_OPEN_MS) durationMs = DEFAULT_OPEN_MS;

    r.active    = true;
    r.closeTime = millis() + (unsigned long)durationMs;
    r.durationMs = durationMs;
    strncpy(r.lastOpener, openerName ? openerName : "Unknown", 63);
    r.lastOpener[63] = '\0';

    digitalWrite(relayPin, HIGH);
    digitalWrite(doorLed,  HIGH);
    beep(2, 80);   // 2 bíp ngắn = cửa đang mở

    Serial.printf("[RELAY%d] >>> MỞ CỬA %d ms — %s\n", ch, durationMs, r.lastOpener);
    Serial.printf("[RELAY%d]     Pin %d=HIGH, LED %d=HIGH\n", ch, relayPin, doorLed);
}

void closeRelay(int ch) {
    RelayState& r = (ch == 2) ? relay2 : relay1;
    int relayPin  = (ch == 2) ? RELAY2_PIN : RELAY1_PIN;
    int doorLed   = (ch == 2) ? DOOR2_LED  : DOOR1_LED;

    r.active = false;
    digitalWrite(relayPin, LOW);
    digitalWrite(doorLed,  LOW);
    beep(1, 50);   // 1 bíp = cửa đã đóng

    Serial.printf("[RELAY%d] <<< ĐÓNG CỬA — Pin %d=LOW\n", ch, relayPin);
}


// ═══════════════════════════════════════════════════════════════════════════════
//  MQTT: PUBLISH
// ═══════════════════════════════════════════════════════════════════════════════

void publishLog(const char* action, int ch, const char* openerName,
                bool success, const char* note = "") {
    if (!mqttClient.connected()) return;

    StaticJsonDocument<384> doc;
    doc["deviceCode"] = DEVICE_CODE;
    doc["action"]     = action;
    doc["channel"]    = ch;
    doc["name"]       = openerName ? openerName : "";
    doc["success"]    = success;
    doc["relay1"]     = relay1.active ? "OPEN" : "CLOSED";
    doc["relay2"]     = relay2.active ? "OPEN" : "CLOSED";
    doc["note"]       = note;
    doc["uptime_ms"]  = millis();

    char buf[384];
    serializeJson(doc, buf, sizeof(buf));

    bool published = mqttClient.publish(LOG_TOPIC, buf, false);
    Serial.printf("[LOG] %s (%s) → %s\n",
                  action, published ? "OK" : "FAIL", buf);
}

void publishStatus() {
    if (!mqttClient.connected()) return;

    StaticJsonDocument<256> doc;
    doc["deviceCode"]  = DEVICE_CODE;
    doc["type"]        = "STATUS";
    doc["online"]      = true;
    doc["relay1"]      = relay1.active ? "OPEN" : "CLOSED";
    doc["relay2"]      = relay2.active ? "OPEN" : "CLOSED";
    doc["retries"]     = mqttRetryCount;
    doc["freeHeap"]    = ESP.getFreeHeap();
    doc["uptime_ms"]   = millis();

    char buf[256];
    serializeJson(doc, buf, sizeof(buf));
    mqttClient.publish(STATUS_TOPIC, buf, false);

    Serial.printf("[STATUS] relay1=%s relay2=%s heap=%u\n",
                  relay1.active ? "OPEN" : "CLOSED",
                  relay2.active ? "OPEN" : "CLOSED",
                  (unsigned)ESP.getFreeHeap());
}


// ═══════════════════════════════════════════════════════════════════════════════
//  MQTT: XỬ LÝ LỆNH ĐẾN
// ═══════════════════════════════════════════════════════════════════════════════

void handleCommand(const char* payload, unsigned int length) {
    // Copy payload vào buffer an toàn
    char buf[512];
    unsigned int safeLen = (length < sizeof(buf) - 1) ? length : sizeof(buf) - 1;
    memcpy(buf, payload, safeLen);
    buf[safeLen] = '\0';

    Serial.printf("\n[CMD] Nhận lệnh: %s\n", buf);

    StaticJsonDocument<512> doc;
    DeserializationError err = deserializeJson(doc, buf);
    if (err) {
        Serial.printf("[CMD] JSON lỗi: %s\n", err.c_str());
        publishLog("PARSE_ERROR", 0, "", false, err.c_str());
        return;
    }

    const char* action   = doc["action"]      | "UNKNOWN";
    int         channel  = doc["channel"]     | 1;
    int         durMs    = doc["duration_ms"] | DEFAULT_OPEN_MS;
    const char* name     = doc["name"]        | "Unknown";
    bool        relayOn  = doc["relay"]       | true;

    // Sanity-check
    if (channel < 1 || channel > 2) channel = 1;
    if (durMs   < 0 || durMs > MAX_OPEN_MS)  durMs = DEFAULT_OPEN_MS;

    // ── START_AUTH_FLOW ──────────────────────────────────────────────────────
    if (strcmp(action, "START_AUTH_FLOW") == 0) {
        Serial.printf("[CMD] START_AUTH_FLOW: %s — Thiết bị Android đang yêu cầu xác minh.\n",
                      doc["reason"] | "Admin yêu cầu");
        // Beep 1 lần ngắn: báo hiệu người dùng cần quét CCCD
        beep(1, 150);
        publishLog("START_AUTH_FLOW", 0, name, true, "Auth flow triggered on Android");

    // ── OPEN_DOOR ────────────────────────────────────────────────────────────
    } else if (strcmp(action, "OPEN_DOOR") == 0) {
        if (!relayOn) {
            Serial.println("[CMD] OPEN_DOOR: relay=false → bỏ qua relay vật lý.");
            return;
        }
        openRelay(channel, durMs, name);
        publishLog("OPEN_DOOR", channel, name, true, "Relay activated");

    // ── CLOSE_DOOR ───────────────────────────────────────────────────────────
    } else if (strcmp(action, "CLOSE_DOOR") == 0) {
        bool closed = false;
        if (relay1.active) { closeRelay(1); closed = true; }
        if (relay2.active) { closeRelay(2); closed = true; }
        if (!closed && channel >= 1 && channel <= 2) { closeRelay(channel); closed = true; }
        publishLog("CLOSE_DOOR", channel, name, true, closed ? "Relay off" : "Was already closed");

    // ── PING / STATUS ─────────────────────────────────────────────────────────
    } else if (strcmp(action, "PING") == 0 || strcmp(action, "STATUS") == 0) {
        publishStatus();
        Serial.println("[CMD] PING/STATUS → đã gửi status.");

    // ── Không rõ ─────────────────────────────────────────────────────────────
    } else {
        Serial.printf("[CMD] Lệnh không xác định: %s\n", action);
        publishLog("UNKNOWN_CMD", 0, name, false, action);
    }
}

// MQTT message callback
void onMqttMessage(char* topic, byte* payload, unsigned int length) {
    handleCommand((const char*)payload, length);
}


// ═══════════════════════════════════════════════════════════════════════════════
//  KẾT NỐI WiFi & MQTT
// ═══════════════════════════════════════════════════════════════════════════════

void connectWiFi() {
    Serial.printf("\n[WiFi] Đang kết nối SSID: %s\n", WIFI_SSID);
    setStatusLed(false);

    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    unsigned long deadline = millis() + 20000; // 20s timeout
    while (WiFi.status() != WL_CONNECTED && millis() < deadline) {
        delay(500);
        Serial.print(".");
    }
    Serial.println();

    if (WiFi.status() == WL_CONNECTED) {
        Serial.printf("[WiFi] ✔ Kết nối thành công! IP: %s\n",
                      WiFi.localIP().toString().c_str());
    } else {
        Serial.println("[WiFi] ✘ THẤT BẠI — Khởi động lại sau 3s...");
        delay(3000);
        ESP.restart();
    }
}

bool connectMQTT() {
    if (mqttClient.connected()) return true;

    char clientId[56];
    snprintf(clientId, sizeof(clientId), "esp32-%s-%lu",
             DEVICE_CODE, millis() % 100000UL);

    Serial.printf("[MQTT] Kết nối %s:%d (id: %s)\n",
                  MQTT_HOST, MQTT_PORT, clientId);

    if (mqttClient.connect(clientId, MQTT_USER, MQTT_PASS)) {
        Serial.println("[MQTT] ✔ Kết nối thành công!");
        mqttClient.subscribe(CMD_TOPIC, 1);
        Serial.printf("[MQTT] Subscribe: %s\n", CMD_TOPIC);

        setStatusLed(true);
        publishStatus();          // Thông báo online ngay lập tức
        beep(3, 100);             // 3 bíp = MQTT thành công
        mqttRetryCount = 0;
        return true;

    } else {
        int state = mqttClient.state();
        Serial.printf("[MQTT] ✘ THẤT BẠI! State: %d\n", state);
        // State codes: -4=TIMEOUT, -3=LOST, -2=FAILED, -1=DISCONNECTED,
        //               1=BAD_PROTOCOL, 2=BAD_ID, 3=UNAVAILABLE,
        //               4=BAD_CREDENTIALS, 5=UNAUTHORIZED
        mqttRetryCount++;
        setStatusLed(false);
        return false;
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
//  SETUP & LOOP
// ═══════════════════════════════════════════════════════════════════════════════

void setup() {
    Serial.begin(115200);
    delay(500);
    Serial.println("\n========================================");
    Serial.printf("  ESP32 Door Relay — MQTT (Wokwi Sim)\n");
    Serial.printf("  Device: %s\n", DEVICE_CODE);
    Serial.println("========================================\n");

    // ── GPIO init ──────────────────────────────────────────────────────────
    const int outputs[] = {RELAY1_PIN, RELAY2_PIN, DOOR1_LED, DOOR2_LED,
                            STATUS_LED, BUZZER_PIN};
    for (int pin : outputs) {
        pinMode(pin, OUTPUT);
        digitalWrite(pin, LOW);
    }

    // ── Startup blink ──────────────────────────────────────────────────────
    for (int i = 0; i < 3; i++) {
        digitalWrite(STATUS_LED, HIGH); delay(200);
        digitalWrite(STATUS_LED, LOW);  delay(200);
    }

    // ── Build MQTT topics ──────────────────────────────────────────────────
    snprintf(CMD_TOPIC,    sizeof(CMD_TOPIC),
             "cccd/devices/%s/command",     DEVICE_CODE);
    snprintf(LOG_TOPIC,    sizeof(LOG_TOPIC),
             "cccd/devices/%s/esp32/log",   DEVICE_CODE);
    snprintf(STATUS_TOPIC, sizeof(STATUS_TOPIC),
             "cccd/devices/%s/esp32/status",DEVICE_CODE);

    Serial.printf("[TOPIC] CMD:    %s\n", CMD_TOPIC);
    Serial.printf("[TOPIC] LOG:    %s\n", LOG_TOPIC);
    Serial.printf("[TOPIC] STATUS: %s\n", STATUS_TOPIC);

    // ── WiFi ───────────────────────────────────────────────────────────────
    connectWiFi();

    // ── MQTT setup ─────────────────────────────────────────────────────────
    // setInsecure() = bỏ qua xác minh certificate cho Wokwi simulation.
    // Phần cứng thật: dùng setCACert(HIVEMQ_CA_CERT) thay thế.
    wifiClient.setInsecure();
    mqttClient.setServer(MQTT_HOST, MQTT_PORT);
    mqttClient.setCallback(onMqttMessage);
    mqttClient.setBufferSize(1024);
    mqttClient.setKeepAlive(30);

    // ── MQTT kết nối lần đầu ───────────────────────────────────────────────
    for (int attempt = 0; attempt < 5 && !mqttClient.connected(); attempt++) {
        if (!connectMQTT()) {
            Serial.printf("[MQTT] Thử lại sau 3s... (lần %d/5)\n", attempt + 1);
            delay(3000);
        }
    }

    if (!mqttClient.connected()) {
        Serial.println("[MQTT] Không thể kết nối sau 5 lần thử. Tiếp tục chạy offline.");
        beep(5, 100);  // 5 bíp nhanh = lỗi MQTT
    }

    Serial.println("\n[SETUP] Hoàn tất. Đang lắng nghe lệnh...\n");
}

void loop() {
    unsigned long now = millis();

    // ── Kiểm tra WiFi ──────────────────────────────────────────────────────
    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("[WiFi] Mất kết nối! Reconnect...");
        setStatusLed(false);
        connectWiFi();
        return;
    }

    // ── Kiểm tra MQTT & reconnect ─────────────────────────────────────────
    if (!mqttClient.connected()) {
        if (now - lastMqttRetry >= 5000) {
            lastMqttRetry = now;
            Serial.println("[MQTT] Mất kết nối! Reconnect...");
            connectMQTT();
        }
    }

    mqttClient.loop();  // Xử lý incoming messages

    // ── Tự động đóng relay sau hết thời gian ─────────────────────────────
    if (relay1.active && now >= relay1.closeTime) {
        closeRelay(1);
        publishLog("AUTO_CLOSE", 1, relay1.lastOpener, true, "Duration expired");
    }
    if (relay2.active && now >= relay2.closeTime) {
        closeRelay(2);
        publishLog("AUTO_CLOSE", 2, relay2.lastOpener, true, "Duration expired");
    }

    // ── Heartbeat status ──────────────────────────────────────────────────
    if (mqttClient.connected() && (now - lastHeartbeat >= HEARTBEAT_MS)) {
        publishStatus();
        lastHeartbeat = now;
    }
}
