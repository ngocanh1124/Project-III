package prj3.example.Prj3.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Slf4j
@Service
public class FaceComparisonService {
    
    private final RestTemplate restTemplate;
    
    @Value("${face-ai.server.url:http://localhost:5000}")
    private String aiServerUrl;
    
    @Value("${face-ai.comparison.threshold.primary:0.55}")
    private double thresholdPrimary;
    
    public FaceComparisonService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    

    public FaceComparisonResult compareFaces(String face1Base64, String face2Base64) {
        try {
            String url = aiServerUrl + "/api/v1/face/compare";
            
            Map<String, String> request = new HashMap<>();
            request.put("face1_base64", face1Base64);
            request.put("face2_base64", face2Base64);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
            
            log.info("Calling Python AI service: {}", url);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                
                boolean success = (boolean) body.getOrDefault("success", false);
                if (!success) {
                    log.warn("AI service returned failure: {}", body.get("error"));
                    return FaceComparisonResult.failure("AI service error");
                }
                
                double similarity = ((Number) body.get("similarity")).doubleValue();
                boolean matched = (boolean) body.getOrDefault("matched", false);
                double confidence = ((Number) body.get("confidence")).doubleValue();
                
                return FaceComparisonResult.success(similarity, matched, confidence);
            } else {
                log.error("AI service returned status: {}", response.getStatusCode());
                return FaceComparisonResult.failure("AI service error: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Error comparing faces", e);
            return FaceComparisonResult.failure("Face comparison error: " + e.getMessage());
        }
    }
    
    

    public FaceComparisonBatchResult compareAgainstMany(
            String selfieBase64, 
            List<EmployeeVector> employeeVectors) {
        try {
            String url = aiServerUrl + "/api/v1/face/compare-many";
            
            Map<String, Object> request = new HashMap<>();
            request.put("selfie_base64", selfieBase64);
            request.put("employee_vectors", employeeVectors);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            log.info("Calling batch comparison: {} employees", employeeVectors.size());
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                
                boolean success = (boolean) body.getOrDefault("success", false);
                if (!success) {
                    log.warn("Batch comparison failed: {}", body.get("error"));
                    return FaceComparisonBatchResult.failure("Batch comparison error");
                }
                
                Map<String, Object> bestMatch = (Map<String, Object>) body.get("best_match");
                double bestSimilarity = ((Number) body.get("best_similarity")).doubleValue();
                
                return FaceComparisonBatchResult.success(
                    (String) bestMatch.get("cccd"),
                    bestSimilarity,
                    (List<Map<String, Object>>) body.get("matches")
                );
            } else {
                log.error("Batch comparison returned status: {}", response.getStatusCode());
                return FaceComparisonBatchResult.failure("Service error");
            }
            
        } catch (Exception e) {
            log.error("Error in batch comparison", e);
            return FaceComparisonBatchResult.failure("Comparison error: " + e.getMessage());
        }
    }
    
    

    public FaceVectorResult extractFaceVector(String imageBase64, String cccd) {
        try {
            String url = aiServerUrl + "/api/v1/face/extract";
            
            Map<String, String> request = new HashMap<>();
            request.put("image_base64", imageBase64);
            request.put("cccd", cccd);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
            
            log.info("Extracting face vector for {}", cccd);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                
                boolean success = (boolean) body.getOrDefault("success", false);
                if (!success) {
                    log.warn("Vector extraction failed: {}", body.get("error"));
                    return FaceVectorResult.failure("Extraction error");
                }
                
                String vectorBase64 = (String) body.get("vector_base64");
                double quality = ((Number) body.get("quality")).doubleValue();
                
                return FaceVectorResult.success(vectorBase64, quality);
            } else {
                log.error("Vector extraction returned status: {}", response.getStatusCode());
                return FaceVectorResult.failure("Service error");
            }
            
        } catch (Exception e) {
            log.error("Error extracting face vector", e);
            return FaceVectorResult.failure("Extraction error: " + e.getMessage());
        }
    }
    
    

    public FaceComparisonResult compareFaceWithVector(String selfieBase64, String faceVectorJson) {
        try {
            String url = aiServerUrl + "/api/v1/face/compare-vector";

            Map<String, String> request = new HashMap<>();
            request.put("selfie_base64", selfieBase64);
            request.put("face_vector_json", faceVectorJson);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);

            log.info("Calling compare-vector endpoint: {}", url);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                boolean success = (boolean) body.getOrDefault("success", false);
                if (!success) {
                    return FaceComparisonResult.failure("compare-vector error: " + body.get("error"));
                }
                double similarity = ((Number) body.get("similarity")).doubleValue();
                boolean matched   = (boolean) body.getOrDefault("matched", false);
                double confidence = body.containsKey("confidence")
                        ? ((Number) body.get("confidence")).doubleValue() : similarity;
                return FaceComparisonResult.success(similarity, matched, confidence);
            }
            return FaceComparisonResult.failure("compare-vector HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("compareFaceWithVector error", e);
            return FaceComparisonResult.failure("compare-vector exception: " + e.getMessage());
        }
    }

    

    public boolean checkHealth() {
        try {
            String url = aiServerUrl + "/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            boolean healthy = response.getStatusCode().is2xxSuccessful();
            if (healthy) {
                log.info("AI service is healthy");
            } else {
                log.warn("AI service health check failed");
            }
            return healthy;
        } catch (Exception e) {
            log.warn("AI service not available: {}", e.getMessage());
            return false;
        }
    }
    
    
    
    public static class FaceComparisonResult {
        public boolean success;
        public double similarity;
        public boolean matched;
        public double confidence;
        public String message;
        
        public static FaceComparisonResult success(double similarity, boolean matched, double confidence) {
            FaceComparisonResult result = new FaceComparisonResult();
            result.success = true;
            result.similarity = similarity;
            result.matched = matched;
            result.confidence = confidence;
            return result;
        }
        
        public static FaceComparisonResult failure(String message) {
            FaceComparisonResult result = new FaceComparisonResult();
            result.success = false;
            result.message = message;
            result.similarity = 0;
            result.matched = false;
            return result;
        }
    }
    
    public static class FaceComparisonBatchResult {
        public boolean success;
        public String bestMatchCccd;
        public double bestSimilarity;
        public List<Map<String, Object>> matches;
        public String message;
        
        public static FaceComparisonBatchResult success(String cccd, double similarity, List<Map<String, Object>> matches) {
            FaceComparisonBatchResult result = new FaceComparisonBatchResult();
            result.success = true;
            result.bestMatchCccd = cccd;
            result.bestSimilarity = similarity;
            result.matches = matches;
            return result;
        }
        
        public static FaceComparisonBatchResult failure(String message) {
            FaceComparisonBatchResult result = new FaceComparisonBatchResult();
            result.success = false;
            result.message = message;
            return result;
        }
    }
    
    public static class FaceVectorResult {
        public boolean success;
        public String vectorBase64;
        public double quality;
        public String message;
        
        public static FaceVectorResult success(String vectorBase64, double quality) {
            FaceVectorResult result = new FaceVectorResult();
            result.success = true;
            result.vectorBase64 = vectorBase64;
            result.quality = quality;
            return result;
        }
        
        public static FaceVectorResult failure(String message) {
            FaceVectorResult result = new FaceVectorResult();
            result.success = false;
            result.message = message;
            return result;
        }
    }
    
    public static class EmployeeVector {
        public String cccd;
        public String vector; 
        
        public EmployeeVector(String cccd, String vector) {
            this.cccd = cccd;
            this.vector = vector;
        }
    }
}
