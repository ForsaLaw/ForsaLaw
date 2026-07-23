package com.forsalaw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ForsaLawApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForsaLawApplication.class, args);
    }
}
