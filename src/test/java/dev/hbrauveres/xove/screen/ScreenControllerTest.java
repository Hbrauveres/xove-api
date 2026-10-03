package dev.hbrauveres.xove.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hbrauveres.xove.TestcontainersConfiguration;
import dev.hbrauveres.xove.user.User;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "xove.admin-emails=admin@example.com")
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

    // ---- the sharing connection (spec 0038) ----

    @Test
    void takingCanSayWhichConnectionIsSharingAndNobodySeesIt() throws Exception {
        mvc.perform(post("/api/screen/take").with(as("sub-friend")).with(csrf())
                        .contentType("application/json")
                        .content("{\"participantSid\":\"PA_laptop\",\"trackSid\":\"TR_screen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holder.name").value("Friend"))
                .andExpect(content().string(not(containsString("PA_laptop"))))
                .andExpect(content().string(not(containsString("TR_screen"))));

        Long friend = slot.current().orElseThrow().userId();
        assertThat(slot.connectionLeft(friend, "PA_phone")).isFalse();
        assertThat(slot.connectionLeft(friend, "PA_laptop")).isTrue();
    }

    @Test
    void takingWithoutSayingWhichConnectionIsRefused() throws Exception {
        mvc.perform(post("/api/screen/take").with(as("sub-friend")).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/screen/take").with(as("sub-friend")).with(csrf())
                        .contentType("application/json").content("{\"participantSid\":\"PA_laptop\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Your browser didn't say which video connection is sharing. Reload the page and try again."));

        assertThat(slot.current()).isEmpty();
    }

    // ---- what the stream is sent with (spec 0086) ----

    @Test
    void everyoneSeesTheSettingsTheShareStartedWith() throws Exception {
        mvc.perform(post("/api/screen/take").with(as("sub-friend")).with(csrf())
                        .contentType("application/json")
                        .content("{\"participantSid\":\"PA_l\",\"trackSid\":\"TR_s\",\"quality\":\"720p\",\"mode\":\"sharp\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.quality").value("720p"))
                .andExpect(jsonPath("$.settings.mode").value("sharp"));

        mvc.perform(get("/api/screen").with(as("sub-admin")))
                .andExpect(jsonPath("$.settings.quality").value("720p"))
                .andExpect(jsonPath("$.settings.mode").value("sharp"));
    }

    @Test
    void aShareWithoutSettingsIs1080pSmooth() throws Exception {
        take("sub-friend")
                .andExpect(jsonPath("$.settings.quality").value("1080p"))
                .andExpect(jsonPath("$.settings.mode").value("smooth"));
    }

    @Test
    void aMissingSettingTakesItsDefault() throws Exception {
        mvc.perform(post("/api/screen/take").with(as("sub-friend")).with(csrf())
                        .contentType("application/json")
                        .content("{\"participantSid\":\"PA_l\",\"trackSid\":\"TR_s\",\"mode\":\"sharp\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.quality").value("1080p"))
                .andExpect(jsonPath("$.settings.mode").value("sharp"));
    }

    @Test
    void nobodySharingMeansNoSettings() throws Exception {
        mvc.perform(get("/api/screen").with(as("sub-friend")))
                .andExpect(jsonPath("$.settings").doesNotExist());
    }

    @Test
    void theSharerChangesTheSettingsAndEveryoneSeesIt() throws Exception {
        take("sub-friend");

        settings("sub-friend", "{\"quality\":\"480p\",\"mode\":\"sharp\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.quality").value("480p"));

        mvc.perform(get("/api/screen").with(as("sub-admin")))
                .andExpect(jsonPath("$.holder.name").value("Friend"))
                .andExpect(jsonPath("$.settings.quality").value("480p"))
                .andExpect(jsonPath("$.settings.mode").value("sharp"));
    }

    @Test
    void someoneElseCannotChangeTheSettings() throws Exception {
        take("sub-friend");

        settings("sub-admin", "{\"quality\":\"480p\",\"mode\":\"smooth\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only the person sharing can change how their screen is sent."));
        mvc.perform(get("/api/screen").with(as("sub-friend")))
                .andExpect(jsonPath("$.settings.quality").value("1080p"));
    }

    @Test
    void unknownSettingsAreRefused() throws Exception {
        take("sub-friend");

        settings("sub-friend", "{\"quality\":\"4k\",\"mode\":\"smooth\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Pick a quality of 1080p, 720p or 480p, and a mode of smooth or sharp."));
        mvc.perform(post("/api/screen/take").with(as("sub-admin")).with(csrf())
                        .contentType("application/json")
                        .content("{\"participantSid\":\"PA_l\",\"trackSid\":\"TR_s\",\"quality\":\"720p\",\"mode\":\"blurry\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingTheSettingsNeedsTheCsrfToken() throws Exception {
        take("sub-friend");

        mvc.perform(post("/api/screen/settings").with(as("sub-friend"))
                        .contentType("application/json").content("{\"quality\":\"480p\",\"mode\":\"smooth\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private ResultActions take(String subject) throws Exception {
        return mvc.perform(post("/api/screen/take").with(as(subject)).with(csrf())
                .contentType("application/json")
                .content("{\"participantSid\":\"PA_" + subject + "\",\"trackSid\":\"TR_" + subject + "\"}"));
    }

    private ResultActions settings(String subject, String json) throws Exception {
        return mvc.perform(post("/api/screen/settings").with(as(subject)).with(csrf())
                .contentType("application/json").content(json));
    }

    private ResultActions release(String subject) throws Exception {
        return mvc.perform(post("/api/screen/release").with(as(subject)).with(csrf()));
    }

    private RequestPostProcessor as(String subject) {
        return oidcLogin().idToken(token -> token.subject(subject));
    }
}
