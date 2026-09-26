package dev.kryora.live.livekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveKitTokensTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-09-26T20:00:00Z");

    private final LiveKitProperties properties = new LiveKitProperties(
            "wss://rtc.example.com", "APItest", SECRET, "kryora", Duration.ofHours(1));
    private final LiveKitTokens tokens = new LiveKitTokens(properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void theTokenIsSignedWithTheSecret() throws Exception {
        SignedJWT jwt = SignedJWT.parse(tokens.create("user-1", "Ana"));

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        assertThat(jwt.verify(new MACVerifier(SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
    }

    @Test
    void aTokenSignedWithAnotherSecretDoesNotVerify() throws Exception {
        SignedJWT jwt = SignedJWT.parse(tokens.create("user-1", "Ana"));
        byte[] otherSecret = "ffffffffffffffffffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8);

        assertThat(jwt.verify(new MACVerifier(otherSecret))).isFalse();
    }

    @Test
    void itSaysWhoYouAreAndWhoIssuedIt() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(tokens.create("user-1", "Ana")).getJWTClaimsSet();

        assertThat(claims.getIssuer()).isEqualTo("APItest");
        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.getStringClaim("name")).isEqualTo("Ana");
    }

    @Test
    void itIsValidFromNowForTheConfiguredTime() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(tokens.create("user-1", "Ana")).getJWTClaimsSet();

        assertThat(claims.getNotBeforeTime().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void itOnlyAllowsJoiningOurRoomAndSharingAScreen() throws Exception {
        Map<String, Object> video = SignedJWT.parse(tokens.create("user-1", "Ana")).getJWTClaimsSet().getJSONObjectClaim("video");

        assertThat(video)
                .containsEntry("room", "kryora")
                .containsEntry("roomJoin", true)
                .containsEntry("canSubscribe", true)
                .containsEntry("canPublish", true)
                .containsEntry("canPublishData", false);
        assertThat(video.get("canPublishSources")).asList().containsExactly("screen_share", "screen_share_audio");
    }

    @Test
    void aShortOrMissingSecretMeansNotConfigured() {
        var weak = new LiveKitTokens(
                new LiveKitProperties("wss://rtc.example.com", "APItest", "too-short", null, null), Clock.systemUTC());
        var missing = new LiveKitTokens(new LiveKitProperties(null, null, null, null, null), Clock.systemUTC());

        assertThat(weak.isConfigured()).isFalse();
        assertThat(missing.isConfigured()).isFalse();
        assertThatThrownBy(() -> weak.create("user-1", "Ana")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void roomAndLifetimeHaveDefaults() {
        var defaults = new LiveKitProperties("wss://rtc.example.com", "APItest", SECRET, null, null);

        assertThat(defaults.room()).isEqualTo("kryora");
        assertThat(defaults.tokenTtl()).isEqualTo(Duration.ofHours(1));
    }
}
