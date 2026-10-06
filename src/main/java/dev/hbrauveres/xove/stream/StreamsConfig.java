package dev.hbrauveres.xove.stream;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One list of streams for the whole app: Spring creates it once and hands the same one to everyone. */
@Configuration
class StreamsConfig {

    @Bean
    Streams streams() {
        return new Streams(Clock.systemUTC());
    }

    /** Who has which stream on their stage (spec 0104). */
    @Bean
    Watching watching() {
        return new Watching();
    }
}
