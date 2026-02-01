package com.example.lateMng;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LateMngApplication {

	public static void main(String[] args) {
		// Загрузка .env в system properties
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
		for (String key : new String[]{
				"BOT_TOKEN", "BOT_USERNAME",
				"SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD"
		}) {
			String value = dotenv.get(key);
			if (value != null && !value.isBlank()) {
				System.setProperty(key, value);
			}
		}
		SpringApplication.run(LateMngApplication.class, args);
	}

}
