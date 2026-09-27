package dev.hbrauveres.xove.livekit;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/** Signs a webhook body the way LiveKit does, so tests can play LiveKit. */
final class WebhookSigner {

    private WebhookSigner() {
    }

    /** The Authorization header LiveKit would send for this body, valid for 5 minutes from {@code at}. */
    static String sign(String body, String apiKey, String apiSecret, Instant at) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(apiKey)
                    .issueTime(Date.from(at))
                    .notBeforeTime(Date.from(at))
                    .expirationTime(Date.from(at.plusSeconds(300)))
                    .claim("sha256", Base64.getEncoder().encodeToString(hash))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(apiSecret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
