package prj3.example.Prj3.dto;

public class CompareResponse {
    private boolean match;
    private double score;
    private String message;
    public boolean isMatch() { return match; }
    public void setMatch(boolean match) { this.match = match; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}