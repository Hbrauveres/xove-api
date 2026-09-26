package dev.hbrauveres.xove.livekit;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Builds LiveKit access tokens: a JWT signed with the API secret that says
 * who you are and what you may do in which room. LiveKit checks the signature
 * and refuses anything it didn't expect.
 *
 * Format: https://docs.livekit.io/home/get-started/authentication/
 */
public class LiveKitTokens {

    /** Screen and the screen's own sound (a tab or system audio). Never camera or microphone. */
    static final List<String> PUBLISH_SOURCES = List.of("screen_share", "screen_share_audio");

    private final LiveKitProperties properties;
    private final Clock clock;

    public LiveKitTokens(LiveKitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * @param identity unique and stable per person, e.g. "user-42"
     * @param name     what other people see
     */
    public String create(String identity, String name) {
        if (!isConfigured()) {
            throw new IllegalStateException("LiveKit is not configured");
        }
        Instant now = clock.instant();

        Map<String, Object> video = Map.of(
                "room", properties.room(),
                "roomJoin", true,
                "canSubscribe", true,
                "canPublish", true,
                "canPublishSources", PUBLISH_SOURCES,
                "canPublishData", false,
                "canUpdateOwnMetadata", false);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.apiKey())
                .subject(identity)
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plus(properties.tokenTtl())))
                .claim("name", name)
                .claim("video", video)
                .build();

        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(properties.apiSecret().getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign the LiveKit token", e);
        }
    }
}
