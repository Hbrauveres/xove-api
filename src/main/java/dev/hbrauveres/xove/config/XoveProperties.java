package dev.hbrauveres.xove.config;

import dev.hbrauveres.xove.user.User;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under "xove:" in application.yml. */
@ConfigurationProperties(prefix = "xove")
public record XoveProperties(List<String> adminEmails) {

    public XoveProperties {
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
