package org.app_financeiro.backend;

import org.app_financeiro.backend.config.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@EnableConfigurationProperties(RateLimitProperties.class)
public class BackendApplication {

	public static void main(String[] args) {
		// Garante que LocalDate.now() e crons usem horário de Brasília em qualquer servidor.
		// Sem isso, servidores UTC (Render) disparariam lembretes 3h adiantados.
		TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
		SpringApplication.run(BackendApplication.class, args);
	}

}
