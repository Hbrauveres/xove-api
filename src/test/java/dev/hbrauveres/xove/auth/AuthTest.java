package dev.hbrauveres.xove.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hbrauveres.xove.TestcontainersConfiguration;
import dev.hbrauveres.xove.user.UserRepository;
import dev.hbrauveres.xove.user.UserService;
import dev.hbrauveres.xove.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "xove.admin-emails=admin@example.com")
@Transactional
class AuthTest {

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired UserRepository users;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void meRequiresLogin() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginStartsAtGoogleWithOurCallback() throws Exception {
        var result = mvc.perform(get("/api/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(location).contains("redirect_uri=http://localhost/api/login/oauth2/code/google");
    }

    @Test
    void meReturnsTheSignedInUser() throws Exception {
        userService.recordGoogleLogin("sub-1", "Friend@Example.com", "Friend", "https://pic");

        mvc.perform(get("/api/me").with(oidcLogin().idToken(t -> t.subject("sub-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("friend@example.com"))
                .andExpect(jsonPath("$.status").value("NONE"))
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void adminEmailsBecomeMembers() {
        var admin = userService.recordGoogleLogin("sub-2", "Admin@Example.com", "Admin", null);

        assertThat(admin.getStatus()).isEqualTo(UserStatus.MEMBER);
        assertThat(userService.isAdmin(admin)).isTrue();
    }

    @Test
    void repeatedLoginsUpdateTheSameUser() {
        userService.recordGoogleLogin("sub-3", "c@example.com", "Old name", null);
        userService.recordGoogleLogin("sub-3", "c@example.com", "New name", null);

        assertThat(users.findByGoogleSubject("sub-3")).get()
                .extracting("name").isEqualTo("New name");
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void logoutRequiresCsrfToken() throws Exception {
        mvc.perform(post("/api/auth/logout").with(oidcLogin()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/auth/logout").with(oidcLogin()).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
