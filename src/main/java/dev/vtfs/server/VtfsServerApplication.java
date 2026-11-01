package dev.vtfs.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VtfsServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(VtfsServerApplication.class, args);
    }

}
