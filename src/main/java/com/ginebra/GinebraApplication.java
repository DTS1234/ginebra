package com.ginebra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GinebraApplication {

    public static void main(String[] args) {
        SpringApplication.run(GinebraApplication.class, args);
    }
}
