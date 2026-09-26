package dev.hbrauveres.xove;

import org.springframework.boot.SpringApplication;

public class TestXoveApiApplication {

    public static void main(String[] args) {
        SpringApplication.from(XoveApiApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
