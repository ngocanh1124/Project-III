package prj3.example.Prj3.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class FaceAIService {
    private static final Logger logger = LoggerFactory.getLogger(FaceAIService.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_RETRIES = 2;
    
    @Value("${face-ai.server.url:http://localhost:5000}")
    private String faceAiServerUrl;
    
    @Value("${face-ai.server.timeout:30}")
    private int timeoutSeconds;
    
    @Value("${face-ai.server.retries:2}")
    private int maxRetries;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
            .build();

    

    public double[] extractVector(String base64Image) throws Exception {
        if (base64Image == null || base64Image.trim().isEmpty()) {
            throw new IllegalArgumentException("Base64 image cannot be null or empty");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("image", base64Image);

        String response = callPythonAPI("/extract-vector", payload);
        if (response == null) {
            logger.error("Failed to extract vector: null response from server");
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            boolean success = root.path("success").asBoolean(false);
            
            if (!success) {
                logger.warn("Vector extraction failed: {}", root.path("error").asText());
                return null;
            }

            JsonNode embedding = root.path("embedding");
            if (embedding.isArray()) {
                double[] vector = new double[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = embedding.get(i).asDouble();
                }
                return vector;
            }
        } catch (Exception e) {
            logger.error("Error parsing extract-vector response", e);
        }
        return null;
    }

    

    public Map<String, Object> compareTwoImages(String chipImage, String selfieImage, double threshold) throws Exception {
        if (chipImage == null || selfieImage == null) {
            throw new IllegalArgumentException("Both images are required");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("image1", chipImage);
        payload.put("image2", selfieImage);
        payload.put("threshold", Math.max(0.0, Math.min(1.0, threshold))); 

        return callPythonAPIAndParse("/compare", payload);
    }

    

    public Map<String, Object> compareVectorWithImage(double[] embedding, String selfieImage, double threshold) throws Exception {
        if (embedding == null || selfieImage == null) {
            throw new IllegalArgumentException("Both embedding and image are required");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("embedding", Arrays.asList(embedding));
        payload.put("image", selfieImage);
        payload.put("threshold", Math.max(0.0, Math.min(1.0, threshold)));

        return callPythonAPIAndParse("/compare", payload);
    }

    

    private String callPythonAPI(String endpoint, Map<String, Object> payload) throws Exception {
        String url = faceAiServerUrl + endpoint;
        int retries = 0;

        while (retries <= maxRetries) {
            try {
                String jsonBody = objectMapper.writeValueAsString(payload);
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                logger.debug("Calling Python API: {} (Attempt {}/{})", url, retries + 1, maxRetries + 1);
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    logger.debug("Python API response received successfully");
                    return response.body();
                } else {
                    logger.warn("Python API returned status {}: {}", response.statusCode(), response.body());
                    if (response.statusCode() >= 500 && retries < maxRetries) {
                        retries++;
                        Thread.sleep(1000 * retries); 
                        continue;
                    }
                    return null;
                }
            } catch (java.net.ConnectException e) {
                logger.warn("Connection error to Python API (Attempt {}/{}): {}", retries + 1, maxRetries + 1, e.getMessage());
                if (retries < maxRetries) {
                    retries++;
                    Thread.sleep(1000 * retries);
                    continue;
                }
                throw new RuntimeException("Failed to connect to Face AI service after " + (maxRetries + 1) + " attempts", e);
            } catch (java.net.SocketTimeoutException e) {
                logger.warn("Timeout calling Python API (Attempt {}/{})", retries + 1, maxRetries + 1);
                if (retries < maxRetries) {
                    retries++;
                    Thread.sleep(1000 * retries);
                    continue;
                }
                throw new RuntimeException("Face AI service timeout after " + (maxRetries + 1) + " attempts", e);
            }
        }
        
        return null;
    }

    

    private Map<String, Object> callPythonAPIAndParse(String endpoint, Map<String, Object> payload) throws Exception {
        String response = callPythonAPI(endpoint, payload);
        Map<String, Object> result = new HashMap<>();
        
        if (response == null) {
            result.put("matched", false);
            result.put("score", 0.0);
            result.put("message", "Không thể kết nối đến dịch vụ AI");
            result.put("error", true);
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            result.put("matched", root.path("matched").asBoolean(false));
            result.put("score", root.path("score").asDouble(0.0));
            result.put("threshold", root.path("threshold").asDouble(0.55));
            result.put("message", root.path("message").asText(""));
            result.put("error", root.path("error") != null);
            return result;
        } catch (Exception e) {
            logger.error("Error parsing comparison response", e);
            result.put("matched", false);
            result.put("score", 0.0);
            result.put("message", "Lỗi xử lý kết quả từ dịch vụ AI");
            result.put("error", true);
            return result;
        }
    }

    

    public boolean isServiceHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(faceAiServerUrl + "/health"))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            logger.warn("Face AI service health check failed: {}", e.getMessage());
            return false;
        }
    }
}