package com.margomarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MargomarketApplication {

	public static void main(String[] args) {
		SpringApplication.run(MargomarketApplication.class, args);
	}

}
