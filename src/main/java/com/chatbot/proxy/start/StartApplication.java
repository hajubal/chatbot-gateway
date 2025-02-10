package com.chatbot.proxy.start;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Gateway 앞단에 실행되는 proxy.
 * 시작 시간등을 측정하기 위함
 */
@SpringBootApplication
public class StartApplication {

	public static void main(String[] args) {
		System.setProperty("spring.config.location",
				"classpath:/proxy/start/application-start.yml");
		SpringApplication.run(StartApplication.class, args);
	}

}
