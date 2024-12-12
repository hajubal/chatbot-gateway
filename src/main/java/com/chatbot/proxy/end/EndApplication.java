package com.chatbot.proxy.end;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EndApplication {

	public static void main(String[] args) {
		System.setProperty("spring.config.location",
				"classpath:/proxy/end/application-end.yml");
		SpringApplication.run(EndApplication.class, args);
	}

}
