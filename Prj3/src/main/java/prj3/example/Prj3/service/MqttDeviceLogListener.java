package prj3.example.Prj3.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import prj3.example.Prj3.websocket.AttendanceWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

@Service
public class MqttDeviceLogListener {

    private static final Logger log = LoggerFactory.getLogger(MqttDeviceLogListener.class);

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private AttendanceWebSocketHandler webSocketHandler;

    

    @ServiceActivator(inputChannel = "mqttEsp32Channel")
    public void handleEsp32Message(Message<String> message) {
        String payload = message.getPayload();
        String topic   = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);

        log.debug("ESP32 MQTT received [{}]: {}", topic, payload);

        try {
            Map<String, Object> data = objectMapper.readValue(
                    payload, new TypeReference<Map<String, Object>>() {});

            
            if (!data.containsKey("deviceCode")) {
                data.put("deviceCode", extractSegment(topic, 2));
            }
            data.put("messageType", extractSegment(topic, 4)); 
            data.put("receivedAt",  System.currentTimeMillis());

            
            webSocketHandler.broadcastDeviceLog(data);

            String action = String.valueOf(data.getOrDefault("action", "STATUS"));
            String device = String.valueOf(data.getOrDefault("deviceCode", "?"));
            log.info("ESP32 [{}] action={} relay1={} relay2={}",
                    device, action,
                    data.getOrDefault("relay1", "?"),
                    data.getOrDefault("relay2", "?"));

        } catch (Exception e) {
            log.error("ESP32 MQTT parse error (topic={}): {}", topic, e.getMessage());
            
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("raw", payload);
            fallback.put("topic", topic);
            fallback.put("parseError", e.getMessage());
            fallback.put("receivedAt", System.currentTimeMillis());
            webSocketHandler.broadcastDeviceLog(fallback);
        }
    }

    

    private String extractSegment(String topic, int index) {
        if (topic == null) return "UNKNOWN";
        String[] parts = topic.split("/");
        return (index < parts.length) ? parts[index] : "UNKNOWN";
    }
}
