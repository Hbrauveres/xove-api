package dev.kryora.live.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kryora.live.TestcontainersConfiguration;
import dev.kryora.live.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Checks that /api/me hands the frontend its XSRF-TOKEN cookie.
 *
 * Runs in a fresh Spring context on purpose: spring-security-test's csrf() helper, used by
 * other test classes, swaps the app's CSRF token storage for a test version that never
 * writes cookies. Sharing a context with those tests would make this one fail depending
 * on test order.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class CsrfCookieTest {

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;

    @Test
    void meSetsTheXsrfCookieForTheFrontend() throws Exception {
        userService.recordGoogleLogin("sub-csrf", "csrf@example.com", "Csrf", null);
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        mvc.perform(get("/api/me").with(oidcLogin().idToken(t -> t.subject("sub-csrf"))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }
}
