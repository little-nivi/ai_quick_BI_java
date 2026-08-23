package com.nl2sql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Nl2sqlApplication {

    public static void main(String[] args) {
        SpringApplication.run(Nl2sqlApplication.class, args);
    }
}
