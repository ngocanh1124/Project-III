package prj3.example.Prj3.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import prj3.example.Prj3.entity.SystemConfig;
import prj3.example.Prj3.repository.SystemConfigRepository;

@Service
public class ConfigService {
    @Autowired private SystemConfigRepository configRepository;

    public String getMasterPin() {
        return configRepository.findByConfigKey("MASTER_PIN")
                .map(SystemConfig::getConfigValue)
                .orElse("000000"); 
    }

    public void updateMasterPin(String newPin) {
        SystemConfig config = configRepository.findByConfigKey("MASTER_PIN")
                .orElse(new SystemConfig());
        config.setConfigKey("MASTER_PIN");
        config.setConfigValue(newPin);
        config.setGroup("SECURITY");
        configRepository.save(config);
    }
}