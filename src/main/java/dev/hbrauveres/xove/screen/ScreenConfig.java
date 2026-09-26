package dev.hbrauveres.xove.screen;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One slot for the whole app: Spring creates it once and hands the same one to everyone. */
@Configuration
class ScreenConfig {

    @Bean
    ScreenSlot screenSlot() {
        return new ScreenSlot(Clock.systemUTC());
    }
}
