package com.chatbot.proxy.end;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Gateway 실행 후 실행되는 proxy.
 * 종료 시간등을 측정하기 위함
 */
@SpringBootApplication
public class EndApplication {

	public static void main(String[] args) {
		System.setProperty("spring.config.location",
				"classpath:/proxy/end/application-end.yml");
		SpringApplication.run(EndApplication.class, args);
	}

}
