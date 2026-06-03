package prj3.example.Prj3.service;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;

@MessagingGateway(defaultRequestChannel = "mqttOutboundChannel")
public interface MqttGateway {
    
    
    void sendToMqtt(String data, @Header(MqttHeaders.TOPIC) String topic);

    
    void sendRetained(String data,
                      @Header(MqttHeaders.TOPIC) String topic,
                      @Header(MqttHeaders.RETAINED) boolean retained);
}