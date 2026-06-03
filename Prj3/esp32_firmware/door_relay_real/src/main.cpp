#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define WIFI_SSID       "?"     
#define WIFI_PASSWORD   "nganh123"         

#define MQTT_HOST  "d6d92a4a5dda42df88c5e1a584a37bca.s1.eu.hivemq.cloud"
#define MQTT_PORT  8883
#define MQTT_USER  "NgocAnh"
#define MQTT_PASS  "i9NEe#ufX4r.yxx"

#define BLE_DEVICE_NAME "DOOR_RELAY_01"

struct DoorConfig {
    const char* deviceCode;
    int         relayPin;
    int         ledPin;
};

static const DoorConfig DOORS[] = {
    { "DEV01", 26, 25 },   
    { "DEV02", 27, 33 },   
};

static const int NUM_DOORS = sizeof(DOORS) / sizeof(DOORS[0]);

#define STATUS_LED    2   
#define BUZZER_PIN   32   

#define RELAY_ACTIVE_LOW 1

#define DEFAULT_OPEN_MS   3000
#define MAX_OPEN_MS      30000
#define HEARTBEAT_MS     30000
#define WIFI_TIMEOUT_MS  20000
#define MQTT_RETRY_MS     5000
#define WIFI_RETRY_MS    30000

#define BLE_SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define BLE_CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

char TOPIC_CMDS[NUM_DOORS][64];  
char TOPIC_LOG[64];
char TOPIC_STATUS[64];

struct RelayState {
    bool          active;
    unsigned long closeAt;
    char          opener[64];
    char          source[8];
};
RelayState relayStates[NUM_DOORS];  

WiFiClientSecure wifiClient;
PubSubClient     mqttClient(wifiClient);
unsigned long    lastHeartbeat = 0;
unsigned long    lastMqttRetry = 0;
unsigned long    lastWifiRetry = 0;
int              mqttRetryCount = 0;

BLEServer* pBleServer   = nullptr;
BLECharacteristic* pBleChar     = nullptr;
volatile bool      bleConnected = false;

static inline void relayWrite(int pin, bool turnOn) {
#if RELAY_ACTIVE_LOW
    digitalWrite(pin, turnOn ? LOW : HIGH);
#else
    digitalWrite(pin, turnOn ? HIGH : LOW);
#endif
}

void beep(int times, int onMs = 100, int offMs = 80) {
    for (int i = 0; i < times; i++) {
        digitalWrite(BUZZER_PIN, HIGH);
        delay(onMs);
        digitalWrite(BUZZER_PIN, LOW);
        if (i < times - 1) delay(offMs);
    }
}

void openDoor(int doorIdx, int durationMs, const char* openerName, const char* source) {
    if (doorIdx < 0 || doorIdx >= NUM_DOORS) doorIdx = 0;
    if (durationMs <= 0 || durationMs > MAX_OPEN_MS) durationMs = DEFAULT_OPEN_MS;

    RelayState& r = relayStates[doorIdx];
    int relayPin  = DOORS[doorIdx].relayPin;
    int ledPin    = DOORS[doorIdx].ledPin;

    r.active  = true;
    r.closeAt = millis() + (unsigned long)durationMs;
    strncpy(r.opener, openerName ? openerName : "Unknown", sizeof(r.opener) - 1);
    r.opener[sizeof(r.opener) - 1] = '\0';
    strncpy(r.source, source ? source : "?", sizeof(r.source) - 1);
    r.source[sizeof(r.source) - 1] = '\0';

    relayWrite(relayPin, true);
    if (ledPin >= 0) digitalWrite(ledPin, HIGH);
    beep(2, 80, 80);

    Serial.printf("[RELAY%d/%s] >>> MO CUA | %d ms | %s | nguon: %s\n",
                  doorIdx + 1, DOORS[doorIdx].deviceCode, durationMs, r.opener, r.source);
}

void closeDoor(int doorIdx) {
    if (doorIdx < 0 || doorIdx >= NUM_DOORS) return;

    RelayState& r = relayStates[doorIdx];
    int relayPin  = DOORS[doorIdx].relayPin;
    int ledPin    = DOORS[doorIdx].ledPin;

    r.active = false;
    relayWrite(relayPin, false);
    if (ledPin >= 0) digitalWrite(ledPin, LOW);
    beep(1, 50);

    Serial.printf("[RELAY%d/%s] <<< DONG CUA\n", doorIdx + 1, DOORS[doorIdx].deviceCode);
}

void publishLog(const char* action, int doorIdx, const char* name,
                bool success, const char* note, const char* source = "") {
    if (!mqttClient.connected()) return;

    const char* devCode = (doorIdx >= 0 && doorIdx < NUM_DOORS)
                          ? DOORS[doorIdx].deviceCode : DOORS[0].deviceCode;

    StaticJsonDocument<384> doc;
    doc["deviceCode"] = devCode;
    doc["action"]     = action;
    doc["channel"]    = (doorIdx >= 0) ? doorIdx + 1 : 0;
    doc["name"]       = name   ? name   : "";
    doc["source"]     = source ? source : "";
    doc["success"]    = success;
    doc["note"]       = note   ? note   : "";
    doc["uptime_ms"]  = millis();

    char buf[384];
    serializeJson(doc, buf, sizeof(buf));
    mqttClient.publish(TOPIC_LOG, buf, false);
    Serial.printf("[LOG->MQTT] %s\n", buf);
}

void publishStatus() {
    if (!mqttClient.connected()) return;

    StaticJsonDocument<512> doc;
    doc["type"]      = "STATUS";
    doc["online"]    = true;
    doc["bleConn"]   = bleConnected;
    doc["freeHeap"]  = ESP.getFreeHeap();
    doc["uptime_ms"] = millis();

    JsonArray doors = doc.createNestedArray("doors");
    for (int i = 0; i < NUM_DOORS; i++) {
        JsonObject d = doors.createNestedObject();
        d["deviceCode"] = DOORS[i].deviceCode;
        d["state"]      = relayStates[i].active ? "OPEN" : "CLOSED";
    }

    char buf[512];
    serializeJson(doc, buf, sizeof(buf));
    mqttClient.publish(TOPIC_STATUS, buf, false);
    Serial.printf("[STATUS->MQTT] heap=%u doors=%d\n",
                  (unsigned)ESP.getFreeHeap(), NUM_DOORS);
}

void handleCommand(const char* jsonStr, const char* source, int forceDoorIdx = -1) {
    Serial.printf("[CMD/%s] %s\n", source, jsonStr);

    if (strlen(jsonStr) > 511) {
        Serial.printf("[CMD/%s] Payload qua dai, bo qua.\n", source);
        return;
    }

    StaticJsonDocument<512> doc;
    DeserializationError err = deserializeJson(doc, jsonStr);
    if (err) {
        Serial.printf("[CMD/%s] JSON loi: %s\n", source, err.c_str());
        return;
    }

    const char* action = doc["action"]      | "UNKNOWN";
    int         durMs  = doc["duration_ms"] | DEFAULT_OPEN_MS;
    const char* name   = doc["name"]        | "Unknown";
    bool         relay  = doc["relay"]       | false;

    int doorIdx;
    if (forceDoorIdx >= 0) {
        doorIdx = forceDoorIdx;
    } else {
        int ch = doc["channel"] | 1;
        doorIdx = ch - 1;  
    }
    if (doorIdx < 0 || doorIdx >= NUM_DOORS) doorIdx = 0;
    if (durMs < 100 || durMs > MAX_OPEN_MS) durMs = DEFAULT_OPEN_MS;

    if (strcmp(action, "OPEN_DOOR") == 0) {
        if (!relay) {
            Serial.printf("[CMD/%s] OPEN_DOOR tu choi: relay=false\n", source);
            return;
        }
        openDoor(doorIdx, durMs, name, source);
        publishLog("OPEN_DOOR", doorIdx, name, true, "OK", source);

    } else if (strcmp(action, "CLOSE_DOOR") == 0) {
        int rawCh = doc["channel"] | 1;
        if (rawCh == 0 && forceDoorIdx < 0) {
            for (int i = 0; i < NUM_DOORS; i++) {
                if (relayStates[i].active) closeDoor(i);
            }
        } else {
            closeDoor(doorIdx);
        }
        publishLog("CLOSE_DOOR", doorIdx, name, true, "Manual close", source);

    } else if (strcmp(action, "PING") == 0 || strcmp(action, "STATUS") == 0) {
        publishStatus();

    } else {
        Serial.printf("[CMD/%s] Lenh khong xac dinh: %s\n", source, action);
        publishLog("UNKNOWN_CMD", -1, name, false, action, source);
    }
}

static int findDoorByTopic(const char* topic) {
    for (int i = 0; i < NUM_DOORS; i++) {
        if (strcmp(topic, TOPIC_CMDS[i]) == 0) return i;
    }
    return -1;
}

void onMqttMessage(char* topic, byte* payload, unsigned int length) {
    char buf[512];
    unsigned int safeLen = (length < sizeof(buf) - 1) ? length : sizeof(buf) - 1;
    memcpy(buf, payload, safeLen);
    buf[safeLen] = '\0';
    int doorIdx = findDoorByTopic(topic);
    handleCommand(buf, "MQTT", doorIdx);
}

void connectWiFi() {
    Serial.printf("\n[WiFi] Ket noi SSID: %s ...\n", WIFI_SSID);
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
        Serial.println("[WiFi] THAT BAI — BLE van san sang nhan lenh local.");
    }
}

bool connectMQTT() {
    if (!WiFi.isConnected() || mqttClient.connected()) return mqttClient.connected();

    char clientId[56];
    snprintf(clientId, sizeof(clientId), "esp32-%s-%04X",
             DOORS[0].deviceCode, (unsigned)(millis() & 0xFFFF));
    Serial.printf("[MQTT] Ket noi HiveMQ id=%s...\n", clientId);

    if (mqttClient.connect(clientId, MQTT_USER, MQTT_PASS)) {
        Serial.println("[MQTT] OK — da ket noi HiveMQ Cloud!");
        for (int i = 0; i < NUM_DOORS; i++) {
            mqttClient.subscribe(TOPIC_CMDS[i], 1);
            Serial.printf("[MQTT] Subscribe [%d/%d]: %s\n", i + 1, NUM_DOORS, TOPIC_CMDS[i]);
        }
        digitalWrite(STATUS_LED, HIGH);
        publishStatus();
        beep(3, 80);
        mqttRetryCount = 0;
        return true;
    }

    Serial.printf("[MQTT] THAT BAI state=%d (lan %d)\n",
                  mqttClient.state(), ++mqttRetryCount);
    return false;
}

class BleServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pSvr) override {
        bleConnected = true;
        Serial.println("[BLE] Android ket noi!");
        beep(1, 150);
    }
    void onDisconnect(BLEServer* pSvr) override {
        bleConnected = false;
        Serial.println("[BLE] Android ngat ket noi — quang ba lai...");
        delay(500);
        BLEDevice::startAdvertising();
    }
};

class BleCharCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pChr) override {
        std::string val = pChr->getValue();
        if (val.empty()) return;

        char buf[512];
        size_t safeLen = (val.size() < sizeof(buf) - 1) ? val.size() : sizeof(buf) - 1;
        memcpy(buf, val.data(), safeLen);
        buf[safeLen] = '\0';

        handleCommand(buf, "BLE");

        bool anyOpen = false;
        for (int i = 0; i < NUM_DOORS; i++) { if (relayStates[i].active) { anyOpen = true; break; } }
        String resp = "{\"ok\":true,\"doors\":[";
        for (int i = 0; i < NUM_DOORS; i++) {
            resp += "{\"d\":\"" + String(DOORS[i].deviceCode) + "\",\"s\":\""
                  + (relayStates[i].active ? "OPEN" : "CLOSED") + "\"}";
            if (i < NUM_DOORS - 1) resp += ",";
        }
        resp += "]}";
        pChr->setValue(resp.c_str());
        pChr->notify();
    }
};

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
    pAdv->setMinPreferred(0x06);
    BLEDevice::startAdvertising();

    Serial.printf("[BLE] Dang quang ba: \"%s\"\n", BLE_DEVICE_NAME);
}

void setup() {
    Serial.begin(115200);
    delay(500);
    Serial.println("\n\n========== ESP32 DOOR RELAY — REAL HARDWARE v1.0 ==========");
    Serial.printf("          So cua: %d\n", NUM_DOORS);
    for (int i = 0; i < NUM_DOORS; i++) {
        Serial.printf("           Cua %d: %s | relay GPIO%d | LED GPIO%d\n",
                      i + 1, DOORS[i].deviceCode, DOORS[i].relayPin, DOORS[i].ledPin);
    }
    Serial.println();

    for (int i = 0; i < NUM_DOORS; i++) {
        pinMode(DOORS[i].relayPin, OUTPUT);
        relayWrite(DOORS[i].relayPin, false);
        if (DOORS[i].ledPin >= 0) {
            pinMode(DOORS[i].ledPin, OUTPUT);
            digitalWrite(DOORS[i].ledPin, LOW);
        }
    }
    pinMode(STATUS_LED, OUTPUT);
    pinMode(BUZZER_PIN, OUTPUT);
    digitalWrite(STATUS_LED, LOW);
    digitalWrite(BUZZER_PIN, LOW);

    Serial.printf("[INIT] GPIO OK — %d relay(s): OFF\n", NUM_DOORS);

    for (int i = 0; i < NUM_DOORS; i++) {
        snprintf(TOPIC_CMDS[i], sizeof(TOPIC_CMDS[i]),
                 "cccd/devices/%s/command", DOORS[i].deviceCode);
    }
    snprintf(TOPIC_LOG,    sizeof(TOPIC_LOG),    "cccd/devices/%s/esp32/log",    DOORS[0].deviceCode);
    snprintf(TOPIC_STATUS, sizeof(TOPIC_STATUS), "cccd/devices/%s/esp32/status", DOORS[0].deviceCode);

    initBLE();

    connectWiFi();

    wifiClient.setInsecure();
    mqttClient.setServer(MQTT_HOST, MQTT_PORT);
    mqttClient.setCallback(onMqttMessage);
    mqttClient.setBufferSize(1024);
    mqttClient.setKeepAlive(60);

    if (WiFi.isConnected()) connectMQTT();

    beep(1, 400);
    Serial.println("[INIT] === San sang — WiFi+MQTT va BLE dang lang nghe ===\n");
}

void loop() {
    unsigned long now = millis();

    if (!WiFi.isConnected()) {
        if (now - lastWifiRetry >= WIFI_RETRY_MS) {
            lastWifiRetry = now;
            Serial.println("[WiFi] Mat ket noi — thu lai...");
            connectWiFi();
        }
    }

    if (WiFi.isConnected()) {
        if (!mqttClient.connected()) {
            if (now - lastMqttRetry >= MQTT_RETRY_MS) {
                lastMqttRetry = now;
                if (!connectMQTT()) digitalWrite(STATUS_LED, LOW);
            }
        } else {
            mqttClient.loop();
        }
    }

    for (int i = 0; i < NUM_DOORS; i++) {
        if (relayStates[i].active && now >= relayStates[i].closeAt) {
            char opener[64];
            strncpy(opener, relayStates[i].opener, sizeof(opener) - 1);
            opener[sizeof(opener) - 1] = '\0';
            char src[8];
            strncpy(src, relayStates[i].source, sizeof(src) - 1);
            src[sizeof(src) - 1] = '\0';
            closeDoor(i);
            publishLog("AUTO_CLOSE", i, opener, true, "Duration expired", src);
        }
    }

    if (mqttClient.connected() && (now - lastHeartbeat >= HEARTBEAT_MS)) {
        lastHeartbeat = now;
        publishStatus();
    }

    delay(10);
}