package dev.kryora.live;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KryoraLiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(KryoraLiveApplication.class, args);
    }
}
