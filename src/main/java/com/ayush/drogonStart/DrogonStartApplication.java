package com.ayush.drogonStart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DrogonStartApplication {


	public static void main(String[] args) {
		SpringApplication.run(DrogonStartApplication.class, args);
	}

}
