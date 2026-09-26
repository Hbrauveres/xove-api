package dev.kryora.live.screen;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kryora.live.TestcontainersConfiguration;
import dev.kryora.live.user.User;
import dev.kryora.live.user.UserRepository;
import dev.kryora.live.user.UserService;
import dev.kryora.live.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "kryora.admin-emails=admin@example.com")
@Transactional
class ScreenControllerTest {

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired ScreenSlot slot;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        slot.clear();

        userService.recordGoogleLogin("sub-admin", "admin@example.com", "Admin", null);
        User friend = userService.recordGoogleLogin("sub-friend", "friend@example.com", "Friend", "https://img/friend");
        friend.changeStatus(UserStatus.MEMBER);
        users.save(friend);
        userService.recordGoogleLogin("sub-stranger", "stranger@example.com", "Stranger", null);
    }

    // ---- who gets in ----

    @Test
    void signedOutGets401() throws Exception {
        mvc.perform(get("/api/screen")).andExpect(status().isUnauthorized());
    }

    @Test
    void nonMembersGet403() throws Exception {
        mvc.perform(get("/api/screen").with(as("sub-stranger"))).andExpect(status().isForbidden());
        take("sub-stranger").andExpect(status().isForbidden());
    }

    @Test
    void takingWithoutTheCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/screen/take").with(as("sub-friend"))).andExpect(status().isForbidden());
    }

    // ---- the slot through HTTP ----

    @Test
    void nobodyIsSharingAtFirst() throws Exception {
        mvc.perform(get("/api/screen").with(as("sub-friend")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holder").doesNotExist())
                .andExpect(jsonPath("$.mine").value(false));
    }

    @Test
    void aMemberCanTakeTheScreenAndEveryoneSeesIt() throws Exception {
        take("sub-friend")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holder.name").value("Friend"))
                .andExpect(jsonPath("$.holder.avatarUrl").value("https://img/friend"))
                .andExpect(jsonPath("$.holder.since").isString())
                .andExpect(jsonPath("$.mine").value(true));

        mvc.perform(get("/api/screen").with(as("sub-admin")))
                .andExpect(jsonPath("$.holder.name").value("Friend"))
                .andExpect(jsonPath("$.mine").value(false));
    }

    @Test
    void anotherMemberCanTakeOver() throws Exception {
        take("sub-friend");

        take("sub-admin")
                .andExpect(jsonPath("$.holder.name").value("Admin"))
                .andExpect(jsonPath("$.mine").value(true));
    }

    @Test
    void theHolderCanStopSharing() throws Exception {
        take("sub-friend");

        release("sub-friend")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holder").doesNotExist());
    }

    @Test
    void someoneElseCannotStopTheShare() throws Exception {
        take("sub-friend");

        release("sub-admin")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only the person sharing can stop the share."));

        mvc.perform(get("/api/screen").with(as("sub-friend")))
                .andExpect(jsonPath("$.holder.name").value("Friend"));
    }

    // ---- helpers ----

    private ResultActions take(String subject) throws Exception {
        return mvc.perform(post("/api/screen/take").with(as(subject)).with(csrf()));
    }

    private ResultActions release(String subject) throws Exception {
        return mvc.perform(post("/api/screen/release").with(as(subject)).with(csrf()));
    }

    private RequestPostProcessor as(String subject) {
        return oidcLogin().idToken(token -> token.subject(subject));
    }
}
