package dev.hbrauveres.xove.livekit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LiveKitWebhookVerifierTest {

    // Test-only values, built so they don't look like real keys.
    private static final String ISSUER = "APItest";
    private static final String SECRET = "0123456789abcdef".repeat(3); // gitleaks:allow (fake test value)
    private static final Instant NOW = Instant.parse("2026-09-27T20:00:00Z");
    private static final String BODY = "{\"event\":\"participant_left\"}";

    private final LiveKitWebhookVerifier verifier = new LiveKitWebhookVerifier(
            new LiveKitProperties("wss://rtc.example.com", ISSUER, SECRET, "xove", Duration.ofHours(1)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsWhatLiveKitSigned() {
        assertThat(verify(WebhookSigner.sign(BODY, ISSUER, SECRET, NOW), BODY)).isTrue();
    }

    @Test
    void refusesAMissingHeader() {
        assertThat(verify(null, BODY)).isFalse();
        assertThat(verify("", BODY)).isFalse();
        assertThat(verify("not-a-jwt", BODY)).isFalse();
    }

    @Test
    void refusesAnotherSecret() {
        assertThat(verify(WebhookSigner.sign(BODY, ISSUER, "f".repeat(48), NOW), BODY)).isFalse();
    }

    @Test
    void refusesAnotherKey() {
        assertThat(verify(WebhookSigner.sign(BODY, "APIother", SECRET, NOW), BODY)).isFalse();
    }

    @Test
    void refusesAnExpiredSignature() {
        assertThat(verify(WebhookSigner.sign(BODY, ISSUER, SECRET, NOW.minusSeconds(600)), BODY)).isFalse();
    }

    @Test
    void refusesASignatureFromTheFuture() {
        assertThat(verify(WebhookSigner.sign(BODY, ISSUER, SECRET, NOW.plusSeconds(600)), BODY)).isFalse();
    }

    @Test
    void refusesABodyChangedAfterSigning() {
        String header = WebhookSigner.sign(BODY, ISSUER, SECRET, NOW);

        assertThat(verify(header, "{\"event\":\"track_unpublished\"}")).isFalse();
    }

    @Test
    void refusesEverythingWhenLiveKitIsNotConfigured() {
        LiveKitWebhookVerifier unconfigured = new LiveKitWebhookVerifier(
                new LiveKitProperties("", "", "", "xove", Duration.ofHours(1)), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(unconfigured.verify(WebhookSigner.sign(BODY, ISSUER, SECRET, NOW),
                BODY.getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    private boolean verify(String authorization, String body) {
        return verifier.verify(authorization, body.getBytes(StandardCharsets.UTF_8));
    }
}
