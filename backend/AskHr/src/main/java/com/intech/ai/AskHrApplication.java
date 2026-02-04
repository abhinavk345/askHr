package com.intech.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class AskHrApplication {

	public static void main(String[] args) {
		SpringApplication.run(AskHrApplication.class, args);
	}

}
