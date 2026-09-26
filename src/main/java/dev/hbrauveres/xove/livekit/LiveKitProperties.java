package dev.hbrauveres.xove.livekit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under "xove.livekit:" in application.yml.
 *
 * @param url       where browsers connect, e.g. wss://rtc-stage.hbrauveres.dev
 * @param apiKey    same key LiveKit was started with (LIVEKIT_KEYS)
 * @param apiSecret same secret; signs the tokens, never leaves the server
 * @param room      the one room everybody joins
 * @param tokenTtl  how long a token can be used to connect
 */
@ConfigurationProperties(prefix = "xove.livekit")
public record LiveKitProperties(String url, String apiKey, String apiSecret, String room, Duration tokenTtl) {

    /** HS256 needs a secret of at least 256 bits. */
    static final int MIN_SECRET_LENGTH = 32;

    public LiveKitProperties {
        room = room == null || room.isBlank() ? "xove" : room;
        tokenTtl = tokenTtl == null ? Duration.ofHours(1) : tokenTtl;
    }

    public boolean isConfigured() {
        return url != null && !url.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && apiSecret.length() >= MIN_SECRET_LENGTH;
    }
}
