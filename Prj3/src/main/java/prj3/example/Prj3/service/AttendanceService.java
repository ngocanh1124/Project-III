package prj3.example.Prj3.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

import prj3.example.Prj3.dto.CompareResponse;
import prj3.example.Prj3.model.AttendanceLog;
import prj3.example.Prj3.model.Employee;
import prj3.example.Prj3.model.Organization;
import prj3.example.Prj3.repository.AttendanceLogRepository;
import prj3.example.Prj3.repository.EmployeeRepository;
import prj3.example.Prj3.repository.OrganizationRepository;

@Service
public class AttendanceService {

    private final AttendanceLogRepository logRepo;
    private final EmployeeRepository empRepo;
    private final OrganizationRepository orgRepo;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String PYTHON_API_URL = "http://localhost:5000/compare";

    public AttendanceService(AttendanceLogRepository logRepo, EmployeeRepository empRepo, OrganizationRepository orgRepo) {
        this.logRepo = logRepo;
        this.empRepo = empRepo;
        this.orgRepo = orgRepo;
    }

    public List<AttendanceLog> getAllLogs() {
        return logRepo.findAll();
    }

    public void addNewEmployee(String name, String cccd, String position, Long orgId) {
        Organization org = orgRepo.findById(orgId).orElse(null);
        if (org != null) {
            Employee emp = new Employee();
            emp.setName(name);
            emp.setCccd(cccd);
            emp.setPosition(position);
            emp.setOrg(org);
            empRepo.save(emp);
        }
    }

    public void deleteEmployee(Long id) {
        List<AttendanceLog> logs = logRepo.findByEmployeeId(id);
        for (AttendanceLog log : logs) {
            log.setEmployee(null);
            logRepo.save(log);
        }
        empRepo.deleteById(id);
    }

    public AttendanceLog processAttendance(String cccd, String fullname, MultipartFile chipImg, MultipartFile selfieImg, Long orgId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            if (chipImg != null) {
                body.add("chip", new ByteArrayResource(chipImg.getBytes()) {
                    @Override
                    public String getFilename() {
                        return "chip.jpg";
                    }
                });
            }
            if (selfieImg != null) {
                body.add("selfie", new ByteArrayResource(selfieImg.getBytes()) {
                    @Override
                    public String getFilename() {
                        return "selfie.jpg";
                    }
                });
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            double score = 0.0;
            boolean matched = false;

            try {
                ResponseEntity<CompareResponse> response = restTemplate.postForEntity(
                        PYTHON_API_URL, requestEntity, CompareResponse.class);
                if (response.getBody() != null) {
                    score = response.getBody().getScore();
                    matched = response.getBody().isMatch();
                }
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }

            AttendanceLog log = new AttendanceLog();
            log.setCccd(cccd);
            log.setCapturedName(fullname);
            log.setTimestamp(LocalDateTime.now());
            log.setScore(score);
            log.setMatched(matched);

            Organization org = null;
            if (orgId != null) {
                org = orgRepo.findById(orgId).orElse(null);
                log.setOrg(org);
            }

            if (cccd != null && org != null) {
                Optional<Employee> emp = empRepo.findByCccdAndOrgId(cccd, orgId);
                if (emp.isPresent()) {
                    log.setEmployee(emp.get());
                }
            }

            return logRepo.save(log);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}