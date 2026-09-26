package dev.kryora.live.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kryora.live.TestcontainersConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "kryora.admin-emails=admin@example.com")
@Transactional
class LoginSuccessHandlerTest {

    @Autowired LoginSuccessHandler handler;

    @Test
    void nonMembersGoToRequestAccess() throws Exception {
        assertThat(redirectAfterLogin("friend@example.com", true)).isEqualTo("/request-access");
    }

    @Test
    void membersGoToTheRoom() throws Exception {
        assertThat(redirectAfterLogin("admin@example.com", true)).isEqualTo("/room");
    }

    @Test
    void unverifiedEmailsAreTurnedAway() throws Exception {
        assertThat(redirectAfterLogin("someone@example.com", false)).isEqualTo("/?error=email-not-verified");
    }

    private String redirectAfterLogin(String email, boolean emailVerified) throws Exception {
        var idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), Map.of(
                "sub", "sub-" + email,
                "email", email,
                "email_verified", emailVerified,
                "name", "Test User"));
        var authorities = List.of(new SimpleGrantedAuthority("OIDC_USER"));
        var googleUser = new DefaultOidcUser(authorities, idToken);
        var authentication = new OAuth2AuthenticationToken(googleUser, authorities, "google");

        var response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);
        return response.getRedirectedUrl();
    }
}
