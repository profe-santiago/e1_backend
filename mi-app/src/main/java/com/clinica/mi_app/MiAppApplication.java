package com.clinica.mi_app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MiAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(MiAppApplication.class, args);
	}

}
