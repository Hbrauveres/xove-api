package dev.kryora.live;

import org.springframework.boot.SpringApplication;

public class TestKryoraLiveApplication {

    public static void main(String[] args) {
        SpringApplication.from(KryoraLiveApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
