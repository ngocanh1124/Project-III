/**
 * ================================================================
 *  ESP32 Door Relay Controller — Real Hardware (Production)
 *  Phiên bản: 1.0  |  Board: ESP32 Pro 38-pin (WROOM-32)
 * ================================================================
 *
 *  ═══════════════════════════════════════════════════════════════
 *  SƠ ĐỒ KẾT NỐI PHẦN CỨNG
 *  ═══════════════════════════════════════════════════════════════
 *
 *  [ Nguồn điện ]
 *  Jack DC cái (+) ────────────────────── VIN ESP32 (5V)
 *                 └──────────────────────  VCC Relay Module (JD-VCC)
 *                 └──────────────────────  COM relay (phụ tải tương lai)
 *  Jack DC cái (−) ────────────────────── GND chung (ESP32 + Relay)
 *
 *  ┌─────────────────────────────────────────────────────────────┐
 *  │  ESP32 Pro 38-pin              │  Module Relay 2 kênh 5V    │
 *  │                                │  (active LOW: IN=LOW=ON)   │
 *  │  GPIO 26 ──────────────────────│── IN1  (kênh 1 – cửa 1)   │
 *  │  GPIO 27 ──────────────────────│── IN2  (kênh 2 – cửa 2)   │
 *  │  GND     ──────────────────────│── GND                      │
 *  │  5V/VIN  ──────────────────────│── VCC (hoặc JD-VCC)        │
 *  │                                │                            │
 *  │  GPIO  2 → LED built-in        │  COM1 ─→ (+) khóa từ 1    │
 *  │  GPIO 25 → LED xanh (cửa 1)   │  NO1  ─→ (−) khóa từ 1    │
 *  │  GPIO 33 → LED xanh (cửa 2)   │  COM2 ─→ (+) khóa từ 2    │
 *  │  GPIO 32 → Buzzer active       │  NO2  ─→ (−) khóa từ 2    │
 *  └─────────────────────────────────────────────────────────────┘
 *
 *  Relay active LOW (phổ biến với module TQ có optocoupler):
 *    IN = LOW  → relay ON  (NO đóng → mở khóa từ)
 *    IN = HIGH → relay OFF (NO hở  → khóa từ đóng cửa)
 *  ⚠  Đặt relay HIGH ngay trong setup() để tránh relay tự bật khi boot.
 *
 *  Chưa có khóa từ: để trống phần COM/NO relay, khi lắp khóa từ
 *  12V chỉ cần nối dây theo sơ đồ trên, không cần sửa code.
 *
 *  ═══════════════════════════════════════════════════════════════
 *  LUỒNG DỮ LIỆU
 *  ═══════════════════════════════════════════════════════════════
 *
 *  [Android nhận diện khuôn mặt OK]
 *         │
 *         ├─ BLE ─────────────────────────────────────→ ESP32 (local)
 *         │                                               ↓ relay ON
 *  [Spring Boot xác nhận OK]
 *         │
 *         └─ MQTT (HiveMQ Cloud) ─────────────────────→ ESP32 (remote)
 *                                                         ↓ relay ON
 *
 *  Nếu nhận diện THẤT BẠI: server KHÔNG gửi OPEN_DOOR → relay KHÔNG bật.
 *
 *  ═══════════════════════════════════════════════════════════════
 *  MQTT TOPICS
 *  ═══════════════════════════════════════════════════════════════
 *  Subscribe:
 *    cccd/devices/{DEVICE_CODE}/command
 *    Ví dụ payload: {"action":"OPEN_DOOR","channel":1,"duration_ms":3000,
 *                    "name":"Nguyen Van A","relay":true}
 *
 *  Publish (log):   cccd/devices/{DEVICE_CODE}/esp32/log
 *  Publish (status):cccd/devices/{DEVICE_CODE}/esp32/status  (mỗi 30s)
 *
 *  ═══════════════════════════════════════════════════════════════
 *  BLE
 *  ═══════════════════════════════════════════════════════════════
 *  Tên BLE:            DOOR_RELAY_01
 *  Service UUID:       4fafc201-1fb5-459e-8fcc-c5c9c331914b
 *  Characteristic UUID:beb5483e-36e1-4688-b7f5-ea07361b26a8
 *  (giữ nguyên để Android app kết nối đúng)
 *
 *  ═══════════════════════════════════════════════════════════════
 *  THƯ VIỆN CẦN CÀI (Arduino IDE > Tools > Manage Libraries)
 *  ═══════════════════════════════════════════════════════════════
 *    - PubSubClient   by Nick O'Leary    (v2.8+)
 *    - ArduinoJson    by Benoit Blanchon (v7.x)
 *    (WiFi, WiFiClientSecure, BLE: built-in với ESP32 Arduino Core)
 *
 *  BOARD SETTINGS (Arduino IDE):
 *    Board            : "ESP32 Dev Module"
 *    Partition Scheme : "Default 4MB with spiffs"  ← BLE cần nhiều flash
 *    Upload Speed     : 921600
 *    CPU Frequency    : 240MHz
 * ================================================================
 */

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ════════════════════════════════════════════════════════════════════════════════
//  ⚙  CẤU HÌNH — SỬA PHẦN NÀY TRƯỚC KHI NẠP FIRMWARE
// ════════════════════════════════════════════════════════════════════════════════

// ── WiFi ──────────────────────────────────────────────────────────────────────
#define WIFI_SSID       "TEN_WIFI_CUA_BAN"     // ← đổi thành SSID thực
#define WIFI_PASSWORD   "MAT_KHAU_WIFI"         // ← đổi thành mật khẩu thực

// ── MQTT HiveMQ Cloud (SSL/TLS port 8883) ─────────────────────────────────────
#define MQTT_HOST  "d6d92a4a5dda42df88c5e1a584a37bca.s1.eu.hivemq.cloud"
#define MQTT_PORT  8883
#define MQTT_USER  "NgocAnh"
#define MQTT_PASS  "i9NEe#ufX4r.yxx"

// ── Mã thiết bị (phải khớp với cột deviceCode trong database) ─────────────────
#define DEVICE_CODE "DEV01"

// ── Tên BLE (Android filter theo tên này) ─────────────────────────────────────
#define BLE_DEVICE_NAME "DOOR_RELAY_01"

// ════════════════════════════════════════════════════════════════════════════════
//  PINOUT — ESP32 Pro 38-pin
// ════════════════════════════════════════════════════════════════════════════════

#define RELAY1_PIN   26   // → IN1 module relay (kênh 1 – khóa từ cửa 1)
#define RELAY2_PIN   27   // → IN2 module relay (kênh 2 – khóa từ cửa 2)
#define STATUS_LED    2   // Built-in LED: sáng = MQTT đã kết nối
#define DOOR1_LED    25   // LED xanh báo cửa 1 đang mở
#define DOOR2_LED    33   // LED xanh báo cửa 2 đang mở
#define BUZZER_PIN   32   // Buzzer active (HIGH=kêu, LOW=tắt)

// Relay active LOW (1) hay active HIGH (0)?
// Hầu hết module relay TQ có optocoupler: ACTIVE LOW (IN=LOW → relay bật)
#define RELAY_ACTIVE_LOW 1

// ════════════════════════════════════════════════════════════════════════════════
//  THÔNG SỐ VẬN HÀNH
// ════════════════════════════════════════════════════════════════════════════════

#define DEFAULT_OPEN_MS   3000    // Mặc định mở cửa 3 giây
#define MAX_OPEN_MS      30000    // Tối đa 30 giây/lần
#define HEARTBEAT_MS     30000    // Gửi trạng thái MQTT mỗi 30 giây
#define WIFI_TIMEOUT_MS  20000    // Timeout kết nối WiFi
#define MQTT_RETRY_MS     5000    // Chờ giữa các lần retry MQTT
#define WIFI_RETRY_MS    30000    // Chờ giữa các lần retry WiFi

// ════════════════════════════════════════════════════════════════════════════════
//  BLE UUIDs — giữ nguyên để khớp với Android app
// ════════════════════════════════════════════════════════════════════════════════

#define BLE_SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define BLE_CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// ════════════════════════════════════════════════════════════════════════════════
//  BIẾN TOÀN CỤC
// ════════════════════════════════════════════════════════════════════════════════

// MQTT topics (khởi tạo trong setup)
char TOPIC_CMD[64];      // cccd/devices/DEV01/command
char TOPIC_LOG[64];      // cccd/devices/DEV01/esp32/log
char TOPIC_STATUS[64];   // cccd/devices/DEV01/esp32/status

// Trạng thái relay
struct RelayState {
    bool          active;       // Đang kích hoạt?
    unsigned long closeAt;      // millis() khi tự động đóng
    char          opener[64];   // Tên người được mở (để log)
    char          source[8];    // "MQTT" hoặc "BLE"
};
RelayState relay1 = {false, 0, "", ""};
RelayState relay2 = {false, 0, "", ""};

// WiFi + MQTT
WiFiClientSecure wifiClient;
PubSubClient     mqttClient(wifiClient);
unsigned long    lastHeartbeat = 0;
unsigned long    lastMqttRetry = 0;
unsigned long    lastWifiRetry = 0;
int              mqttRetryCount = 0;

// BLE
BLEServer*         pBleServer  = nullptr;
BLECharacteristic* pBleChar    = nullptr;
volatile bool      bleConnected = false;

// ════════════════════════════════════════════════════════════════════════════════
//  HELPER: RELAY (thống nhất active-LOW và active-HIGH qua macro)
// ════════════════════════════════════════════════════════════════════════════════

/**
 * Ghi trạng thái relay, tự động đảo logic nếu là active-LOW module.
 * turnOn=true  → đóng mạch NO (mở khóa từ)
 * turnOn=false → mở mạch NO  (đóng khóa từ)
 */
static inline void relayWrite(int pin, bool turnOn) {
#if RELAY_ACTIVE_LOW
    digitalWrite(pin, turnOn ? LOW : HIGH);
#else
    digitalWrite(pin, turnOn ? HIGH : LOW);
#endif
}

// ════════════════════════════════════════════════════════════════════════════════
//  HELPER: BUZZER
// ════════════════════════════════════════════════════════════════════════════════

void beep(int times, int onMs = 100, int offMs = 80) {
    for (int i = 0; i < times; i++) {
        digitalWrite(BUZZER_PIN, HIGH);
        delay(onMs);
        digitalWrite(BUZZER_PIN, LOW);
        if (i < times - 1) delay(offMs);
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  ĐIỀU KHIỂN RƠ-LE
// ════════════════════════════════════════════════════════════════════════════════

/**
 * Mở cửa: kích relay kênh ch (1 hoặc 2) trong durationMs mili-giây.
 * Loop() sẽ tự đóng relay sau khi hết thời gian.
 */
void openDoor(int ch, int durationMs, const char* openerName, const char* source) {
    if (ch < 1 || ch > 2) ch = 1;
    if (durationMs <= 0 || durationMs > MAX_OPEN_MS) durationMs = DEFAULT_OPEN_MS;

    RelayState& r  = (ch == 2) ? relay2   : relay1;
    int relayPin   = (ch == 2) ? RELAY2_PIN : RELAY1_PIN;
    int doorLedPin = (ch == 2) ? DOOR2_LED  : DOOR1_LED;

    r.active  = true;
    r.closeAt = millis() + (unsigned long)durationMs;
    strncpy(r.opener, openerName ? openerName : "Unknown", sizeof(r.opener) - 1);
    r.opener[sizeof(r.opener) - 1] = '\0';
    strncpy(r.source, source ? source : "?", sizeof(r.source) - 1);
    r.source[sizeof(r.source) - 1] = '\0';

    relayWrite(relayPin, true);        // Đóng mạch NO → mở khóa từ
    digitalWrite(doorLedPin, HIGH);    // Đèn báo cửa đang mở
    beep(2, 80, 80);                   // 2 bíp ngắn = cửa mở

    Serial.printf("[RELAY%d] >>> MỞ CỬA | %d ms | %s | nguồn: %s\n",
                  ch, durationMs, r.opener, r.source);
}

/**
 * Đóng cửa: tắt relay kênh ch ngay lập tức.
 */
void closeDoor(int ch) {
    RelayState& r  = (ch == 2) ? relay2   : relay1;
    int relayPin   = (ch == 2) ? RELAY2_PIN : RELAY1_PIN;
    int doorLedPin = (ch == 2) ? DOOR2_LED  : DOOR1_LED;

    r.active = false;
    relayWrite(relayPin, false);       // Ngắt mạch NO → khóa từ đóng lại
    digitalWrite(doorLedPin, LOW);
    beep(1, 50);

    Serial.printf("[RELAY%d] <<< ĐÓNG CỬA\n", ch);
}

// ════════════════════════════════════════════════════════════════════════════════
//  MQTT: PUBLISH
// ════════════════════════════════════════════════════════════════════════════════

void publishLog(const char* action, int ch, const char* name,
                bool success, const char* note, const char* source = "") {
    if (!mqttClient.connected()) return;

    StaticJsonDocument<384> doc;
    doc["deviceCode"] = DEVICE_CODE;
    doc["action"]     = action;
    doc["channel"]    = ch;
    doc["name"]       = name  ? name  : "";
    doc["source"]     = source ? source : "";
    doc["success"]    = success;
    doc["relay1"]     = relay1.active ? "OPEN" : "CLOSED";
    doc["relay2"]     = relay2.active ? "OPEN" : "CLOSED";
    doc["note"]       = note  ? note  : "";
    doc["uptime_ms"]  = millis();

    char buf[384];
    serializeJson(doc, buf, sizeof(buf));
    mqttClient.publish(TOPIC_LOG, buf, false);
    Serial.printf("[LOG→MQTT] %s\n", buf);
}

void publishStatus() {
    if (!mqttClient.connected()) return;

    StaticJsonDocument<256> doc;
    doc["deviceCode"] = DEVICE_CODE;
    doc["type"]       = "STATUS";
    doc["online"]     = true;
    doc["relay1"]     = relay1.active ? "OPEN" : "CLOSED";
    doc["relay2"]     = relay2.active ? "OPEN" : "CLOSED";
    doc["bleConn"]    = bleConnected;
    doc["freeHeap"]   = ESP.getFreeHeap();
    doc["uptime_ms"]  = millis();

    char buf[256];
    serializeJson(doc, buf, sizeof(buf));
    mqttClient.publish(TOPIC_STATUS, buf, false);
    Serial.printf("[STATUS→MQTT] heap=%u r1=%s r2=%s\n",
                  (unsigned)ESP.getFreeHeap(),
                  relay1.active ? "OPEN" : "CLOSED",
                  relay2.active ? "OPEN" : "CLOSED");
}

// ════════════════════════════════════════════════════════════════════════════════
//  XỬ LÝ LỆNH CHUNG (dùng chung cho MQTT và BLE)
// ════════════════════════════════════════════════════════════════════════════════

/**
 * Phân tích JSON và thực hiện lệnh.
 * source: "MQTT" hoặc "BLE" — để log và debug.
 *
 * Payload hợp lệ:
 *   {"action":"OPEN_DOOR","channel":1,"duration_ms":3000,
 *    "name":"Nguyen Van A","relay":true}
 *   {"action":"CLOSE_DOOR","channel":1}
 *   {"action":"PING"}
 *   {"action":"STATUS"}
 *
 * CHÚ Ý AN TOÀN:
 *   - OPEN_DOOR chỉ thực hiện khi "relay":true (server chỉ set true sau
 *     khi nhận diện khuôn mặt thành công).
 *   - Nếu "relay":false hoặc field bị thiếu → bỏ qua, không mở cửa.
 */
void handleCommand(const char* jsonStr, const char* source) {
    Serial.printf("[CMD/%s] %s\n", source, jsonStr);

    // Giới hạn độ dài để tránh stack overflow
    if (strlen(jsonStr) > 511) {
        Serial.printf("[CMD/%s] Payload quá dài, bỏ qua.\n", source);
        return;
    }

    StaticJsonDocument<512> doc;
    DeserializationError err = deserializeJson(doc, jsonStr);
    if (err) {
        Serial.printf("[CMD/%s] JSON lỗi: %s\n", source, err.c_str());
        return;
    }

    const char* action  = doc["action"]      | "UNKNOWN";
    int         channel = doc["channel"]     | 1;
    int         durMs   = doc["duration_ms"] | DEFAULT_OPEN_MS;
    const char* name    = doc["name"]        | "Unknown";
    bool        relay   = doc["relay"].as<bool>() || false; // Chắc chắn lấy kiểu bool

    // Giới hạn giá trị hợp lệ
    if (channel < 1 || channel > 2) channel = 1;
    if (durMs   < 100 || durMs > MAX_OPEN_MS) durMs = DEFAULT_OPEN_MS;

    // ── OPEN_DOOR ─────────────────────────────────────────────────────────────
    if (strcmp(action, "OPEN_DOOR") == 0) {
        // Bảo vệ: chỉ mở cửa khi server xác nhận relay=true
        // Trường hợp này nghĩa là face recognition đã thành công phía server
        if (!relay) {
            Serial.printf("[CMD/%s] OPEN_DOOR từ chối: relay=false "
                          "(nhận diện khuôn mặt chưa xác nhận)\n", source);
            return;
        }
        openDoor(channel, durMs, name, source);
        publishLog("OPEN_DOOR", channel, name, true, "OK", source);

    // ── CLOSE_DOOR ────────────────────────────────────────────────────────────
    } else if (strcmp(action, "CLOSE_DOOR") == 0) {
        // channel=0 hoặc không có → đóng cả 2
        if (channel == 0) {
            if (relay1.active) closeDoor(1);
            if (relay2.active) closeDoor(2);
        } else {
            closeDoor(channel);
        }
        publishLog("CLOSE_DOOR", channel, name, true, "Manual close", source);

    // ── PING / STATUS ─────────────────────────────────────────────────────────
    } else if (strcmp(action, "PING") == 0 || strcmp(action, "STATUS") == 0) {
        publishStatus();
        Serial.printf("[CMD/%s] PING → đã gửi STATUS\n", source);

    // ── Lệnh không rõ ─────────────────────────────────────────────────────────
    } else {
        Serial.printf("[CMD/%s] Lệnh không xác định: %s\n", source, action);
        publishLog("UNKNOWN_CMD", 0, name, false, action, source);
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  MQTT: CALLBACK KHI NHẬN TIN NHẮN
// ════════════════════════════════════════════════════════════════════════════════

void onMqttMessage(char* topic, byte* payload, unsigned int length) {
    // Copy an toàn vào buffer có null-terminator
    char buf[512];
    unsigned int safeLen = (length < sizeof(buf) - 1) ? length : sizeof(buf) - 1;
    memcpy(buf, payload, safeLen);
    buf[safeLen] = '\0';

    handleCommand(buf, "MQTT");
}

// ════════════════════════════════════════════════════════════════════════════════
//  KẾT NỐI WiFi
// ════════════════════════════════════════════════════════════════════════════════

void connectWiFi() {
    Serial.printf("\n[WiFi] Kết nối SSID: %s ...\n", WIFI_SSID);
    digitalWrite(STATUS_LED, LOW);

    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    unsigned long deadline = millis() + WIFI_TIMEOUT_MS;
    while (WiFi.status() != WL_CONNECTED && millis() < deadline) {
        delay(500);
        Serial.print(".");
    }
    Serial.println();

    if (WiFi.status() == WL_CONNECTED) {
        Serial.printf("[WiFi] OK — IP: %s\n", WiFi.localIP().toString().c_str());
        beep(2, 100);
    } else {
        // Không restart — BLE vẫn hoạt động độc lập khi không có WiFi
        Serial.println("[WiFi] THẤT BẠI — BLE vẫn sẵn sàng nhận lệnh local.");
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  KẾT NỐI MQTT
// ════════════════════════════════════════════════════════════════════════════════

bool connectMQTT() {
    if (!WiFi.isConnected() || mqttClient.connected()) return mqttClient.connected();

    char clientId[56];
    // ClientId ngẫu nhiên để tránh xung đột nếu nhiều ESP32 cùng DEVICE_CODE
    snprintf(clientId, sizeof(clientId), "esp32-%s-%04X",
             DEVICE_CODE, (unsigned)(millis() & 0xFFFF));

    Serial.printf("[MQTT] Kết nối HiveMQ... id=%s\n", clientId);

    if (mqttClient.connect(clientId, MQTT_USER, MQTT_PASS)) {
        Serial.println("[MQTT] OK — đã kết nối HiveMQ Cloud!");
        mqttClient.subscribe(TOPIC_CMD, 1);  // QoS 1
        Serial.printf("[MQTT] Subscribe: %s\n", TOPIC_CMD);
        digitalWrite(STATUS_LED, HIGH);
        publishStatus();
        beep(3, 80);
        mqttRetryCount = 0;
        return true;
    }

    int state = mqttClient.state();
    // state: -4=TIMEOUT -3=LOST -2=FAILED -1=DISCONNECTED
    //         1=BAD_PROTOCOL 2=BAD_ID 3=UNAVAILABLE 4=BAD_CREDENTIALS
    Serial.printf("[MQTT] THẤT BẠI state=%d (lần %d)\n", state, ++mqttRetryCount);
    return false;
}

// ════════════════════════════════════════════════════════════════════════════════
//  BLE: SERVER CALLBACKS
// ════════════════════════════════════════════════════════════════════════════════

class BleServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pSvr) override {
        bleConnected = true;
        Serial.println("[BLE] Android kết nối!");
        beep(1, 150);
    }
    void onDisconnect(BLEServer* pSvr) override {
        bleConnected = false;
        Serial.println("[BLE] Android ngắt kết nối — quảng bá lại...");
        // Restart advertising để app có thể kết nối lại
        delay(500);
        BLEDevice::startAdvertising();
    }
};

// ════════════════════════════════════════════════════════════════════════════════
//  BLE: CHARACTERISTIC CALLBACKS (nhận lệnh từ Android)
// ════════════════════════════════════════════════════════════════════════════════

class BleCharCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pChr) override {
        std::string val = pChr->getValue();
        if (val.empty()) return;

        // Copy an toàn
        char buf[512];
        size_t safeLen = (val.size() < sizeof(buf) - 1) ? val.size() : sizeof(buf) - 1;
        memcpy(buf, val.data(), safeLen);
        buf[safeLen] = '\0';

        handleCommand(buf, "BLE");

        // Phản hồi lại Android qua BLE Notify
        String resp;
        if (relay1.active || relay2.active) {
            resp = "{\"ok\":true,\"relay\":\"OPEN\",\"r1\":" +
                   String(relay1.active ? "1" : "0") + ",\"r2\":" +
                   String(relay2.active ? "1" : "0") + "}";
        } else {
            resp = "{\"ok\":true,\"relay\":\"CLOSED\"}";
        }
        pChr->setValue(resp.c_str());
        pChr->notify();
    }
};

// ════════════════════════════════════════════════════════════════════════════════
//  KHỞI TẠO BLE
// ════════════════════════════════════════════════════════════════════════════════

void initBLE() {
    BLEDevice::init(BLE_DEVICE_NAME);

    pBleServer = BLEDevice::createServer();
    pBleServer->setCallbacks(new BleServerCallbacks());

    BLEService* pService = pBleServer->createService(BLE_SERVICE_UUID);

    pBleChar = pService->createCharacteristic(
        BLE_CHARACTERISTIC_UUID,
        BLECharacteristic::PROPERTY_READ   |
        BLECharacteristic::PROPERTY_WRITE  |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pBleChar->addDescriptor(new BLE2902());
    pBleChar->setCallbacks(new BleCharCallbacks());
    pBleChar->setValue("DOOR_RELAY_READY");

    pService->start();

    BLEAdvertising* pAdv = BLEDevice::getAdvertising();
    pAdv->addServiceUUID(BLE_SERVICE_UUID);
    pAdv->setScanResponse(true);
    pAdv->setMinPreferred(0x06);   // iOS compatibility
    BLEDevice::startAdvertising();

    Serial.printf("[BLE] Đang quảng bá tên: \"%s\"\n", BLE_DEVICE_NAME);
}

// ════════════════════════════════════════════════════════════════════════════════
//  SETUP
// ════════════════════════════════════════════════════════════════════════════════

void setup() {
    Serial.begin(115200);
    delay(500);
    Serial.println("\n\n========== ESP32 DOOR RELAY — REAL HARDWARE v1.0 ==========");
    Serial.printf(  "           DEVICE_CODE: %s\n\n", DEVICE_CODE);

    // ── GPIO khởi tạo NGAY LẬP TỨC ────────────────────────────────────────────
    // ⚠ Đặt relay OFF trước tiên (active-LOW → HIGH = OFF)
    // để tránh relay tự kích trong ~1 giây đầu khi ESP32 boot.
    pinMode(RELAY1_PIN, OUTPUT);
    pinMode(RELAY2_PIN, OUTPUT);
    relayWrite(RELAY1_PIN, false);   // Relay 1 OFF
    relayWrite(RELAY2_PIN, false);   // Relay 2 OFF

    pinMode(STATUS_LED, OUTPUT);
    pinMode(DOOR1_LED,  OUTPUT);
    pinMode(DOOR2_LED,  OUTPUT);
    pinMode(BUZZER_PIN, OUTPUT);
    digitalWrite(STATUS_LED, LOW);
    digitalWrite(DOOR1_LED,  LOW);
    digitalWrite(DOOR2_LED,  LOW);
    digitalWrite(BUZZER_PIN, LOW);

    Serial.println("[INIT] GPIO OK — Relay 1 & 2: OFF (an toàn)");

    // ── Build MQTT topics ──────────────────────────────────────────────────────
    snprintf(TOPIC_CMD,    sizeof(TOPIC_CMD),    "cccd/devices/%s/command",      DEVICE_CODE);
    snprintf(TOPIC_LOG,    sizeof(TOPIC_LOG),    "cccd/devices/%s/esp32/log",    DEVICE_CODE);
    snprintf(TOPIC_STATUS, sizeof(TOPIC_STATUS), "cccd/devices/%s/esp32/status", DEVICE_CODE);
    Serial.printf("[INIT] MQTT CMD topic: %s\n", TOPIC_CMD);

    // ── Khởi tạo BLE trước WiFi (BLE hoạt động độc lập) ──────────────────────
    initBLE();

    // ── Kết nối WiFi ──────────────────────────────────────────────────────────
    connectWiFi();

    // ── Cấu hình MQTT client ──────────────────────────────────────────────────
    // setInsecure(): bỏ qua verify CA cert — đủ cho môi trường nội bộ/phát triển.
    // Để nâng cấp bảo mật production: thay bằng wifiClient.setCACert(hivemq_root_ca)
    wifiClient.setInsecure();
    mqttClient.setServer(MQTT_HOST, MQTT_PORT);
    mqttClient.setCallback(onMqttMessage);
    mqttClient.setBufferSize(1024);   // Buffer lớn hơn cho payload JSON
    mqttClient.setKeepAlive(60);

    // ── Kết nối MQTT lần đầu ───────────────────────────────────────────────────
    if (WiFi.isConnected()) connectMQTT();

    // ── Bíp báo sẵn sàng ──────────────────────────────────────────────────────
    beep(1, 400);
    Serial.println("[INIT] === Sẵn sàng — WiFi+MQTT và BLE đang lắng nghe ===\n");
}

// ════════════════════════════════════════════════════════════════════════════════
//  LOOP
// ════════════════════════════════════════════════════════════════════════════════

void loop() {
    unsigned long now = millis();

    // ── 1. Duy trì kết nối WiFi ───────────────────────────────────────────────
    if (!WiFi.isConnected()) {
        if (now - lastWifiRetry >= WIFI_RETRY_MS) {
            lastWifiRetry = now;
            Serial.println("[WiFi] Mất kết nối — thử lại...");
            connectWiFi();
        }
        // Không cần delay — tiếp tục xử lý BLE bên dưới
    }

    // ── 2. Duy trì kết nối MQTT ───────────────────────────────────────────────
    if (WiFi.isConnected()) {
        if (!mqttClient.connected()) {
            if (now - lastMqttRetry >= MQTT_RETRY_MS) {
                lastMqttRetry = now;
                if (!connectMQTT()) {
                    digitalWrite(STATUS_LED, LOW);
                }
            }
        } else {
            mqttClient.loop();   // Xử lý tin nhắn MQTT đến
        }
    }

    // ── 3. Tự động đóng relay sau khi hết thời gian mở ───────────────────────
    if (relay1.active && now >= relay1.closeAt) {
        char opener[64];
        strncpy(opener, relay1.opener, sizeof(opener));   // Lưu trước khi closeDoor xóa
        closeDoor(1);
        publishLog("AUTO_CLOSE", 1, opener, true, "Duration expired", relay1.source);
    }
    if (relay2.active && now >= relay2.closeAt) {
        char opener[64];
        strncpy(opener, relay2.opener, sizeof(opener));
        closeDoor(2);
        publishLog("AUTO_CLOSE", 2, opener, true, "Duration expired", relay2.source);
    }

    // ── 4. Heartbeat MQTT mỗi 30 giây ────────────────────────────────────────
    if (mqttClient.connected() && now - lastHeartbeat >= HEARTBEAT_MS) {
        lastHeartbeat = now;
        publishStatus();
    }

    // yield() — cho BLE stack và WiFi stack xử lý background tasks
    delay(10);
}
