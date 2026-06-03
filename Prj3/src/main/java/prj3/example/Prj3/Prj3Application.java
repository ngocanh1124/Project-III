package prj3.example.Prj3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan(basePackages = {"prj3.example.Prj3", "prj3.example.Prj3.model"})
@EnableAsync
@EnableScheduling
public class Prj3Application {

	public static void main(String[] args) {
		SpringApplication.run(Prj3Application.class, args);
	}

}
