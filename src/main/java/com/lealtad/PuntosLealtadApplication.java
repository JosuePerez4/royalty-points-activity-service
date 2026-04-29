package com.lealtad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PuntosLealtadApplication {

    public static void main(String[] args) {
        SpringApplication.run(PuntosLealtadApplication.class, args);
    }
}
