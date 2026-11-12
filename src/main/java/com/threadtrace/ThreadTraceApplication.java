package com.threadtrace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ThreadTraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThreadTraceApplication.class, args);
    }
}
