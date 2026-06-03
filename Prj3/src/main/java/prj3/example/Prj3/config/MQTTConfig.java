package prj3.example.Prj3.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
public class MQTTConfig {

    private static final Logger logger = LoggerFactory.getLogger(MQTTConfig.class);

    @Value("${mqtt.broker.url}")
    private String mqttBrokerUrl;

    @Value("${mqtt.broker.username}")
    private String mqttUsername;

    @Value("${mqtt.broker.password}")
    private String mqttPassword;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] { mqttBrokerUrl });
        options.setUserName(mqttUsername);
        options.setPassword(mqttPassword.toCharArray());
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);          
        options.setKeepAliveInterval(30);        
        options.setConnectionTimeout(10);
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {
        MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler("serverAdmin", mqttClientFactory());
        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic("devices/command");
        
        messageHandler.setAsyncEvents(true);
        return messageHandler;
    }

    

    @Bean
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        "serverInbound",
                        mqttClientFactory(),
                        "cccd/devices/+/attendance"   
                );
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInboundChannel());
        return adapter;
    }

    

    @Bean
    public MessageChannel mqttEsp32Channel() {
        return new DirectChannel();
    }

    

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttEsp32Inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        "serverEsp32Inbound",
                        mqttClientFactory(),
                        "cccd/devices/+/esp32/+"
                );
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttEsp32Channel());
        return adapter;
    }
}