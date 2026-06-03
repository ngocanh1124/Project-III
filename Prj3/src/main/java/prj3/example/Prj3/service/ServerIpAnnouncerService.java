package prj3.example.Prj3.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Service
public class ServerIpAnnouncerService {

    private static final Logger logger = LoggerFactory.getLogger(ServerIpAnnouncerService.class);
    private static final String TOPIC_SERVER_IP = "cccd/server/ip";

    @Autowired
    private MqttGateway mqttGateway;

    @Value("${server.port:8080}")
    private int serverPort;

    private String lastPublishedIp = null;

    @EventListener(ApplicationReadyEvent.class)
    public void announceIpOnStartup() {
        publishCurrentIp(true);
    }

    
    @Scheduled(fixedDelay = 30_000)
    public void checkAndRepublishIfChanged() {
        publishCurrentIp(false);
    }

    private void publishCurrentIp(boolean force) {
        try {
            String localIp = getLocalIpAddress();
            if (localIp == null) {
                logger.warn("ServerIpAnnouncer: Không tìm thấy địa chỉ IP local.");
                return;
            }

            String ipWithPort = localIp + ":" + serverPort;
            if (!force && ipWithPort.equals(lastPublishedIp)) return; 

            Map<String, String> payload = new HashMap<>();
            payload.put("ip", ipWithPort);
            String json = new ObjectMapper().writeValueAsString(payload);

            mqttGateway.sendRetained(json, TOPIC_SERVER_IP, true);
            lastPublishedIp = ipWithPort;
            logger.info("ServerIpAnnouncer: Đã publish IP server lên MQTT [{}] -> {}", TOPIC_SERVER_IP, ipWithPort);
        } catch (Exception e) {
            logger.error("ServerIpAnnouncer: Lỗi khi publish IP: {}", e.getMessage());
        }
    }

    

    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("ServerIpAnnouncer: Lỗi đọc network interfaces: {}", e.getMessage());
        }
        return null;
    }
}
