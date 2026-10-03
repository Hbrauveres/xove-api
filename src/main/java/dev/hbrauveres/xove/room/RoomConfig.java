package dev.hbrauveres.xove.room;

import dev.hbrauveres.xove.config.XoveProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One room for the whole app: Spring creates it once and hands the same one to everyone.
 * Its size comes from {@code XOVE_ROOM_SEATS} (20 when not set).
 */
@Configuration
class RoomConfig {

    @Bean
    RoomSeats roomSeats(XoveProperties properties) {
        return new RoomSeats(properties.room().seats(), Clock.systemUTC());
    }
}
