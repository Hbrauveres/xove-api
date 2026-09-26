package dev.hbrauveres.xove.livekit;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LiveKitConfig {

    @Bean
    LiveKitTokens liveKitTokens(LiveKitProperties properties) {
        return new LiveKitTokens(properties, Clock.systemUTC());
    }
}
