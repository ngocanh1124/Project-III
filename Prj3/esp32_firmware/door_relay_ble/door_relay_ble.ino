/**
 * ============================================================
 *  ESP32 Door Relay Controller — BLE (Bluetooth Low Energy)
 * ============================================================
 *
 * Chức năng:
 *   - ESP32 hoạt động như một thiết bị BLE Peripheral.
 *   - Android (sau khi nhận lệnh OPEN_DOOR từ MQTT Server) kết nối BLE
 *     và ghi lệnh JSON vào Characteristic để kích hoạt relay.
 *   - Relay đóng mạch → mở khóa cửa trong N giây, sau đó tự động mở mạch.
 *
 * Phần cứng:
 *   - ESP32 DevKit
 *   - Relay Module 5V (kết nối GPIO 26)
 *   - LED trạng thái (GPIO 2 — built-in LED)
 *
 * Thư viện cần cài (Arduino Library Manager):
 *   - "ESP32 BLE Arduino" (by Neil Kolban, built into ESP32 core)
 *
 * Cài đặt Arduino IDE:
 *   Board: "ESP32 Dev Module" (hoặc tương đương)
 *   Partition Scheme: "Default 4MB with spiffs"
 *
 * UUIDs (giữ nguyên để Android kết nối đúng):
 *   Service UUID  : 4fafc201-1fb5-459e-8fcc-c5c9c331914b
 *   Characteristic: beb5483e-36e1-4688-b7f5-ea07361b26a8
 *
 * Giao thức lệnh (JSON, Android gửi qua BLE Write):
 *   {"action":"OPEN_DOOR","duration_ms":3000}  → mở cửa 3 giây
 *   {"action":"CLOSE_DOOR"}                    → đóng ngay
 *   {"action":"PING"}                          → kiểm tra kết nối
 *
 * ============================================================
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <ArduinoJson.h>  // Cài từ Library Manager: "ArduinoJson" by Benoit Blanchon

// ── Cấu hình phần cứng ──────────────────────────────────────────────────────
#define RELAY_PIN      26    // GPIO kết nối cuộn dây relay (HIGH = relay kích hoạt)
#define STATUS_LED_PIN  2    // LED trạng thái tích hợp trên board (thường GPIO 2)
#define RELAY_ACTIVE_HIGH true   // true nếu relay kích hoạt ở mức HIGH; false nếu LOW

// ── BLE UUIDs ────────────────────────────────────────────────────────────────
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// ── Tên thiết bị BLE (phải khớp với filter trong Android app) ───────────────
#define DEVICE_NAME "DOOR_RELAY_01"

// ── Hằng số ──────────────────────────────────────────────────────────────────
#define DEFAULT_OPEN_DURATION_MS 3000   // Mặc định giữ relay 3 giây
#define MAX_OPEN_DURATION_MS    30000   // Tối đa 30 giây (an toàn)

// ── Trạng thái toàn cục ───────────────────────────────────────────────────────
BLEServer*         pServer     = nullptr;
BLECharacteristic* pCharact    = nullptr;
bool               deviceConnected = false;
bool               relayActive    = false;
unsigned long      relayCloseTime = 0;  // Thời điểm cần tắt relay (ms)

// ── Callback kết nối BLE ──────────────────────────────────────────────────────
class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pSvr) override {
        deviceConnected = true;
        digitalWrite(STATUS_LED_PIN, HIGH);
        Serial.println("[BLE] Android đã kết nối");
    }
    void onDisconnect(BLEServer* pSvr) override {
        deviceConnected = false;
        digitalWrite(STATUS_LED_PIN, LOW);
        Serial.println("[BLE] Android ngắt kết nối — đang quảng bá lại...");
        pSvr->startAdvertising();
    }
};

// ── Callback nhận lệnh từ Android ────────────────────────────────────────────
class CommandCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pChr) override {
        String value = pChr->getValue().c_str();
        Serial.print("[BLE] Nhận lệnh: ");
        Serial.println(value);

        StaticJsonDocument<256> doc;
        DeserializationError err = deserializeJson(doc, value);
        if (err) {
            Serial.print("[BLE] JSON lỗi: ");
            Serial.println(err.c_str());
            // Gửi phản hồi lỗi
            String resp = "{\"ok\":false,\"error\":\"JSON_PARSE_ERROR\"}";
            pChr->setValue(resp.c_str());
            pChr->notify();
            return;
        }

        const char* action = doc["action"];
        if (!action) {
            pChr->setValue("{\"ok\":false,\"error\":\"MISSING_ACTION\"}");
            pChr->notify();
            return;
        }

        if (strcmp(action, "OPEN_DOOR") == 0) {
            int duration = doc["duration_ms"] | DEFAULT_OPEN_DURATION_MS;
            if (duration > MAX_OPEN_DURATION_MS) duration = MAX_OPEN_DURATION_MS;
            activateRelay(duration);

            StaticJsonDocument<128> resp;
            resp["ok"] = true;
            resp["action"] = "OPEN_DOOR";
            resp["duration_ms"] = duration;
            String respStr;
            serializeJson(resp, respStr);
            pChr->setValue(respStr.c_str());
            pChr->notify();

        } else if (strcmp(action, "CLOSE_DOOR") == 0) {
            deactivateRelay();
            pChr->setValue("{\"ok\":true,\"action\":\"CLOSE_DOOR\"}");
            pChr->notify();

        } else if (strcmp(action, "PING") == 0) {
            StaticJsonDocument<128> resp;
            resp["ok"] = true;
            resp["action"] = "PONG";
            resp["relay"] = relayActive ? "ON" : "OFF";
            String respStr;
            serializeJson(resp, respStr);
            pChr->setValue(respStr.c_str());
            pChr->notify();

        } else {
            pChr->setValue("{\"ok\":false,\"error\":\"UNKNOWN_ACTION\"}");
            pChr->notify();
        }
    }
};

// ── Điều khiển relay ──────────────────────────────────────────────────────────
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

// ── Setup ─────────────────────────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    Serial.println("=== ESP32 Door Relay BLE Controller ===");

    // Khởi tạo GPIO
    pinMode(RELAY_PIN, OUTPUT);
    deactivateRelay();  // Đảm bảo relay tắt khi khởi động
    pinMode(STATUS_LED_PIN, OUTPUT);
    digitalWrite(STATUS_LED_PIN, LOW);

    // Khởi tạo BLE
    BLEDevice::init(DEVICE_NAME);
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    BLEService* pService = pServer->createService(SERVICE_UUID);
    pCharact = pService->createCharacteristic(
        CHARACTERISTIC_UUID,
        BLECharacteristic::PROPERTY_READ  |
        BLECharacteristic::PROPERTY_WRITE |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pCharact->addDescriptor(new BLE2902());
    pCharact->setCallbacks(new CommandCallbacks());
    pCharact->setValue("{\"status\":\"READY\"}");

    pService->start();

    BLEAdvertising* pAdv = BLEDevice::getAdvertising();
    pAdv->addServiceUUID(SERVICE_UUID);
    pAdv->setScanResponse(true);
    BLEDevice::startAdvertising();

    Serial.printf("[BLE] Đang quảng bá với tên: %s\n", DEVICE_NAME);
    Serial.printf("[BLE] Service UUID: %s\n", SERVICE_UUID);
}

// ── Loop ──────────────────────────────────────────────────────────────────────
void loop() {
    // Tự động đóng relay sau khoảng thời gian đã cài đặt
    if (relayActive && millis() >= relayCloseTime) {
        deactivateRelay();
    }
    delay(10);
}
