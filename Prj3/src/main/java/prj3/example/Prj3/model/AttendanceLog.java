package prj3.example.Prj3.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "attendance_log")
public class AttendanceLog {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "employee_id") 
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "org_id")
    private Organization org;

    private String cccd;     
    @Column(name = "captured_name") 
    private String capturedName; 
    private LocalDateTime timestamp;
    private boolean matched;        
    private double score;           

    public AttendanceLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public Organization getOrg() { return org; }
    public void setOrg(Organization org) { this.org = org; }

    public String getCccd() { return cccd; }
    public void setCccd(String cccd) { this.cccd = cccd; }

    public String getCapturedName() { return capturedName; }
    public void setCapturedName(String capturedName) { this.capturedName = capturedName; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public boolean isMatched() { return matched; }
    public void setMatched(boolean matched) { this.matched = matched; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}