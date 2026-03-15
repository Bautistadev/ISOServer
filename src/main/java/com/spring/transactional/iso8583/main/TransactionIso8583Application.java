package com.spring.transactional.iso8583.main;

import com.spring.transactional.iso8583.main.TransactionalPackage.IsoBanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.stereotype.Component;

@SpringBootApplication
@ComponentScan(basePackages = "com.spring.transactional.iso8583.main")
@Slf4j
public class TransactionIso8583Application implements CommandLineRunner {

	public static void main(String[] args) {

		SpringApplication app = new SpringApplication(TransactionIso8583Application.class);
		app.setBanner(new IsoBanner());
		app.run(args);
	}

	@Override
	public void run(String... args) throws Exception {
		Thread.currentThread().join();
	}
}
