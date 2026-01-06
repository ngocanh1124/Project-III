package prj3.example.Prj3.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import prj3.example.Prj3.model.AppUser;
import prj3.example.Prj3.repository.EmployeeRepository;
import prj3.example.Prj3.service.AttendanceService;

@Controller
@RequestMapping("/employees")
public class EmployeeController {

    @Autowired
    private EmployeeRepository empRepo;
    
    @Autowired
    private AttendanceService service;

    @GetMapping("")
    public String listEmployees(Model model, 
                                @RequestParam(required = false) String cccd,
                                @RequestParam(required = false) String name,
                                HttpSession session) {
        AppUser user = (AppUser) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        model.addAttribute("employees", empRepo.findByOrgId(user.getOrg().getId()));
        model.addAttribute("preloadCccd", cccd);
        model.addAttribute("preloadName", name);
        model.addAttribute("user", user);
        
        return "employee_manager";
    }

    @PostMapping("/add")
    public String addEmployee(@RequestParam String name,
                              @RequestParam String cccd,
                              @RequestParam String position,
                              HttpSession session) {
        AppUser user = (AppUser) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        service.addNewEmployee(name, cccd, position, user.getOrg().getId());
        return "redirect:/employees";
    }

    @GetMapping("/delete/{id}")
    public String deleteEmployee(@PathVariable Long id, HttpSession session) {
        AppUser user = (AppUser) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";
        
        service.deleteEmployee(id);
        return "redirect:/employees";
    }
}