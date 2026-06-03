package prj3.example.Prj3.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import prj3.example.Prj3.dto.AttendanceRecordRequestDTO;
import prj3.example.Prj3.dto.AttendanceRecordResponseDTO;

@Service
public class MqttAttendanceListener {

    private static final Logger log = LoggerFactory.getLogger(MqttAttendanceListener.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules(); 

    @Autowired
    private AttendanceService attendanceService;

    

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handleAttendanceRecord(Message<String> message) {
        String payload = message.getPayload();
        log.info("MQTT attendance received: {}", payload);

        try {
            AttendanceRecordRequestDTO request =
                    objectMapper.readValue(payload, AttendanceRecordRequestDTO.class);

            if (request.getCccd() == null || request.getDeviceCode() == null) {
                log.warn("MQTT attendance: thiếu cccd hoặc deviceCode, bỏ qua.");
                return;
            }

            
            if (request.getMethod() == null) {
                request.setMethod("MQTT_SYNC");
            }

            AttendanceRecordResponseDTO result =
                    attendanceService.processAttendanceWithLayer2Validation(request);

            log.info("MQTT attendance processed: cccd={}, granted={}, score={}",
                    request.getCccd(),
                    result.getAccessGranted(),
                    result.getMatchScore());

        } catch (Exception e) {
            log.error("MQTT attendance: lỗi xử lý payload: {}", e.getMessage());
        }
    }
}
