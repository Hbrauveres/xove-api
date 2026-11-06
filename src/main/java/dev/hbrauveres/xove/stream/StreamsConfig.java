package dev.hbrauveres.xove.stream;

import dev.hbrauveres.xove.config.XoveProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One list of streams for the whole app: Spring creates it once and hands the same one to everyone. */
@Configuration
class StreamsConfig {

    /** As many places as the room's setting says (spec 0191). */
    @Bean
    Streams streams(XoveProperties properties) {
        return new Streams(properties.room().streamPlaces(), Clock.systemUTC());
    }

    /** Who has which stream on their stage (spec 0104). */
    @Bean
    Watching watching() {
        return new Watching();
    }
}
