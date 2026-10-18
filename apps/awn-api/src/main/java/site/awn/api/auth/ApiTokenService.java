package site.awn.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/** Short-lived, stateless bearer tokens for authenticated buyer/seller API calls. */
@Component
public class ApiTokenService {

    private static final long TOKEN_LIFETIME_SECONDS = 3600;
    private final byte[] secret;

    public ApiTokenService(@Value("${awn.api.token-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalArgumentException("awn.api.token-secret must be at least 32 bytes");
        }
    }

    public String issue(long userId) {
        String payload = userId + "." + (Instant.now().getEpochSecond() + TOKEN_LIFETIME_SECONDS);
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return "v1." + encoded + "." + signature(encoded);
    }

    public long verify(String token) {
        if (token == null) throw new InvalidApiTokenException();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !"v1".equals(parts[0])
                || !MessageDigest.isEqual(signature(parts[1]).getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII))) {
            throw new InvalidApiTokenException();
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String[] claims = payload.split("\\.", -1);
            if (claims.length != 2) throw new InvalidApiTokenException();
            long userId = Long.parseLong(claims[0]);
            long expiresAt = Long.parseLong(claims[1]);
            if (userId <= 0 || expiresAt <= Instant.now().getEpochSecond()) {
                throw new InvalidApiTokenException();
            }
            return userId;
        } catch (IllegalArgumentException ex) {
            throw new InvalidApiTokenException();
        }
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
        }
    }
}
