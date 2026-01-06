package prj3.example.Prj3.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FaceSDKService {
    private static final String PYTHON_API_URL = "http://localhost:5000/compare";
    private final RestTemplate restTemplate = new RestTemplate();
    public Map<String, Object> compareFaces(MultipartFile chipFile, MultipartFile selfieFile) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("chip", new ByteArrayResource(chipFile.getBytes()) {
                @Override
                public String getFilename() {
                    return chipFile.getOriginalFilename() != null ? chipFile.getOriginalFilename() : "chip.jpg";
                }
            });
            body.add("selfie", new ByteArrayResource(selfieFile.getBytes()) {
                @Override
                public String getFilename() {
                    return selfieFile.getOriginalFilename() != null ? selfieFile.getOriginalFilename() : "selfie.jpg";
                }
            });
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(PYTHON_API_URL, requestEntity, Map.class);

            if (response.getBody() != null) {
                return response.getBody();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("match", false);
        fallback.put("score", 0.0);
        return fallback;
    }
}