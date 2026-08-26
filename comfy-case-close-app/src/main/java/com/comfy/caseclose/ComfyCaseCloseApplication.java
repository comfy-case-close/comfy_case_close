package com.comfy.caseclose;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling powers OrphanAttachmentCleanupJob's nightly sweep.
@SpringBootApplication
@EnableScheduling
public class ComfyCaseCloseApplication {

	public static void main(String[] args) {
		SpringApplication.run(ComfyCaseCloseApplication.class, args);
	}

}
