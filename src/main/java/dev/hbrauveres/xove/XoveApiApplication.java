package dev.hbrauveres.xove;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class XoveApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(XoveApiApplication.class, args);
    }
}
