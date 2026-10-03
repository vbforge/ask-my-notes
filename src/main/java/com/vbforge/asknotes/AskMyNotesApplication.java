package com.vbforge.asknotes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AskMyNotesApplication {
    public static void main(String[] args) {
        SpringApplication.run(AskMyNotesApplication.class, args);
    }
}
