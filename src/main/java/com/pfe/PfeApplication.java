package com.pfe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class PfeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PfeApplication.class, args);
    }

}
