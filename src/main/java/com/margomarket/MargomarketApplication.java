package com.margomarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync // listeners of events between modules (@ApplicationModuleListener) run asynchronously
public class MargomarketApplication {

	public static void main(String[] args) {
		SpringApplication.run(MargomarketApplication.class, args);
	}

}
