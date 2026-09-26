package dev.kryora.live.config;

import dev.kryora.live.user.User;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under "kryora:" in application.yml. */
@ConfigurationProperties(prefix = "kryora")
public record KryoraProperties(List<String> adminEmails) {

    public KryoraProperties {
        adminEmails = adminEmails == null
                ? List.of()
                : adminEmails.stream()
                        .filter(e -> !e.isBlank())
                        .map(User::normalizeEmail)
                        .toList();
    }

    public boolean isAdmin(String email) {
        return email != null && adminEmails.contains(User.normalizeEmail(email));
    }
}
