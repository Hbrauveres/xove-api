package dev.hbrauveres.xove.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hbrauveres.xove.config.XoveProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/** Plain unit test: no Spring, no database. */
class AdminAuthoritiesMapperTest {

    private final AdminAuthoritiesMapper mapper =
            new AdminAuthoritiesMapper(new XoveProperties(List.of("Admin@Example.com"), null));

    @Test
    void adminEmailsGetTheAdminRole() {
        assertThat(rolesFor("admin@example.com", true)).contains("ROLE_ADMIN");
    }

    @Test
    void otherEmailsDoNot() {
        assertThat(rolesFor("friend@example.com", true)).doesNotContain("ROLE_ADMIN");
    }

    @Test
    void unverifiedEmailsNeverGetAdmin() {
        assertThat(rolesFor("admin@example.com", false)).doesNotContain("ROLE_ADMIN");
    }

    private List<String> rolesFor(String email, boolean verified) {
        var idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "sub-1", "email", email, "email_verified", verified));
        return mapper.mapAuthorities(List.of(new OidcUserAuthority(idToken))).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
