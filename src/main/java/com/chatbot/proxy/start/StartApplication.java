package com.chatbot.proxy.start;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StartApplication {

	public static void main(String[] args) {
		System.setProperty("spring.config.location",
				"classpath:/proxy/start/application-start.yml");
		SpringApplication.run(StartApplication.class, args);
	}

}
