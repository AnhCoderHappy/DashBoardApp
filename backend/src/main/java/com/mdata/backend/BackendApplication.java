package com.mdata.backend;

import com.mdata.backend.config.EnvInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BackendApplication.class);
        application.addInitializers(new EnvInitializer());
        application.run(args);
    }
}
