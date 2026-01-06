package prj3.example.Prj3.controller;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;
import prj3.example.Prj3.model.AppUser;
import prj3.example.Prj3.model.AttendanceLog;
import prj3.example.Prj3.repository.AttendanceLogRepository;
import prj3.example.Prj3.service.AttendanceService;

@Controller 
public class AttendanceController {
    @Autowired
    private AttendanceService attendanceService;
    
    @Autowired
    private AttendanceLogRepository logRepo;

    @PostMapping("/api/attendance/check")
    @ResponseBody
    public ResponseEntity<?> checkAttendance(
            @RequestParam(value = "cccd", required = false) String cccd,
            @RequestParam(value = "fullname", required = false) String fullname,
            @RequestParam(value = "organizationId", defaultValue = "1") Long organizationId,
            @RequestParam("chip") MultipartFile chip,
            @RequestParam("selfie") MultipartFile selfie) {
        
        AttendanceLog log = attendanceService.processAttendance(cccd, fullname, chip, selfie, organizationId);

        if (log != null) {
            return ResponseEntity.ok()
                    .body("{\"match\": " + log.isMatched() + ", \"score\": " + log.getScore() + "}");
        } else {
            return ResponseEntity.status(500).body("{\"error\": \"Lỗi hệ thống\"}");
        }
    }

    @GetMapping("/")
    public String index(Model model, HttpSession session) {
        AppUser user = (AppUser) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        List<AttendanceLog> allLogs = logRepo.findByOrgId(user.getOrg().getId());
        
        List<AttendanceLog> passedLogs = allLogs.stream()
                .filter(AttendanceLog::isMatched) 
                .sorted(Comparator.comparing(AttendanceLog::getTimestamp).reversed())
                .collect(Collectors.toList());

        Map<String, List<AttendanceLog>> groupedLogs = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        for (AttendanceLog log : passedLogs) {
            String dateKey = log.getTimestamp().format(formatter);
            groupedLogs.computeIfAbsent(dateKey, k -> new java.util.ArrayList<>()).add(log);
        }

        model.addAttribute("groupedLogs", groupedLogs);
        model.addAttribute("user", user);
        return "index";
    }
}