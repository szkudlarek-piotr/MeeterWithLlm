package org.example.meeter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MeeterApplication {

	public static void main(String[] args) {
		SpringApplication.run(MeeterApplication.class, args);
	}

}
