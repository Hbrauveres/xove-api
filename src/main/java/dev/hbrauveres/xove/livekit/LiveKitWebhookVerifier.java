package dev.hbrauveres.xove.livekit;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Checks that a webhook really comes from our LiveKit. LiveKit sends
 * "Authorization: <JWT>": HS256 with the API secret, issued by the API key,
 * valid for a few minutes, with a "sha256" claim holding the base64 SHA-256
 * of the body. All of it must hold, or the request is refused.
 */
public class LiveKitWebhookVerifier {

    /** Room for the two clocks to disagree a little. */
    static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final LiveKitProperties properties;
    private final Clock clock;

    public LiveKitWebhookVerifier(LiveKitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** @return true only if the header is a valid LiveKit signature for exactly this body */
    public boolean verify(String authorization, byte[] body) {
        if (!properties.isConfigured() || authorization == null || authorization.isBlank()) {
            return false;
        }
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        try {
            SignedJWT jwt = SignedJWT.parse(token.trim());
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new MACVerifier(properties.apiSecret().getBytes(StandardCharsets.UTF_8)))) {
                return false;
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            return properties.apiKey().equals(claims.getIssuer())
                    && isCurrent(claims)
                    && matchesBody(claims.getStringClaim("sha256"), body);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isCurrent(JWTClaimsSet claims) {
        Instant now = clock.instant();
        Date expires = claims.getExpirationTime();
        Date notBefore = claims.getNotBeforeTime();
        return expires != null
                && now.isBefore(expires.toInstant().plus(CLOCK_SKEW))
                && (notBefore == null || !now.isBefore(notBefore.toInstant().minus(CLOCK_SKEW)));
    }

    private static boolean matchesBody(String expected, byte[] body) throws Exception {
        if (expected == null) {
            return false;
        }
        byte[] actual = MessageDigest.getInstance("SHA-256").digest(body);
        return MessageDigest.isEqual(Base64.getDecoder().decode(expected), actual);
    }
}
