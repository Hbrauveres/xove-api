package dev.hbrauveres.xove.livekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hbrauveres.xove.TestcontainersConfiguration;
import dev.hbrauveres.xove.screen.ScreenSlot;
import dev.hbrauveres.xove.screen.SharingConnection;
import dev.hbrauveres.xove.user.User;
import dev.hbrauveres.xove.user.UserRepository;
import dev.hbrauveres.xove.user.UserService;
import dev.hbrauveres.xove.user.UserStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** LiveKit tells the API who left; the slot follows (spec 0038). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "xove.livekit.url=wss://rtc.example.com",
        "xove.livekit.api-key=APItest",
        "xove.livekit.api-secret=0123456789abcdef0123456789abcdef0123456789abcdef", // gitleaks:allow (fake test value)
})
@Transactional
class LiveKitWebhookControllerTest {

    private static final String ISSUER = "APItest";
    private static final String SECRET = "0123456789abcdef".repeat(3); // gitleaks:allow (fake test value)

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired ScreenSlot slot;

    MockMvc mvc;
    User ana;
    User bruno;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ana = member("sub-ana", "ana@example.com", "Ana");
        bruno = member("sub-bruno", "bruno@example.com", "Bruno");
        slot.release(slot.current().map(h -> h.userId()).orElse(null));
    }

    // AC-1
    @Test
    void freesTheSlotWhenTheSharingConnectionLeaves() throws Exception {
        anaShares();

        send(left(ana, "PA_ana")).andExpect(status().isOk());

        mvc.perform(get("/api/screen").with(oidcLogin().idToken(t -> t.subject("sub-bruno"))))
                .andExpect(jsonPath("$.holder").doesNotExist());
    }

    // AC-2
    @Test
    void freesTheSlotWhenTheScreenTrackIsUnpublished() throws Exception {
        anaShares();

        send(unpublished(ana, "PA_ana", "TR_ana_screen", "SCREEN_SHARE")).andExpect(status().isOk());

        assertThat(slot.current()).isEmpty();
    }

    // AC-3
    @Test
    void ignoresEventsAboutSomeoneElse() throws Exception {
        anaShares();

        send(left(bruno, "PA_bruno")).andExpect(status().isOk());
        send(unpublished(bruno, "PA_bruno", "TR_bruno_screen", "SCREEN_SHARE")).andExpect(status().isOk());

        assertThat(holder()).isEqualTo(ana.getId());
    }

    // AC-4
    @Test
    void ignoresOtherTracksOfTheSharer() throws Exception {
        anaShares();

        send(unpublished(ana, "PA_ana", "TR_ana_audio", "SCREEN_SHARE_AUDIO")).andExpect(status().isOk());
        send(unpublished(ana, "PA_ana", "TR_ana_screen", "CAMERA")).andExpect(status().isOk());

        assertThat(holder()).isEqualTo(ana.getId());
    }

    // AC-5
    @Test
    void refusesUnsignedOrForgedEventsAndChangesNothing() throws Exception {
        anaShares();
        String body = left(ana, "PA_ana");

        mvc.perform(post("/api/livekit/webhook").contentType("application/webhook+json").content(body))
                .andExpect(status().isUnauthorized());
        send(body, WebhookSigner.sign(body, ISSUER, "f".repeat(48), Instant.now()))
                .andExpect(status().isUnauthorized());
        send(body, WebhookSigner.sign(body, "APIother", SECRET, Instant.now()))
                .andExpect(status().isUnauthorized());
        send(body, WebhookSigner.sign(left(bruno, "PA_bruno"), ISSUER, SECRET, Instant.now()))
                .andExpect(status().isUnauthorized());

        assertThat(holder()).isEqualTo(ana.getId());
    }

    // AC-6
    @Test
    void acceptsEventsWhenNobodyShares() throws Exception {
        send(left(ana, "PA_ana")).andExpect(status().isOk());

        assertThat(slot.current()).isEmpty();
    }

    // AC-7
    @Test
    void ignoresAnotherConnectionOfTheSharer() throws Exception {
        anaShares();

        send(left(ana, "PA_ana_phone")).andExpect(status().isOk());

        assertThat(holder()).isEqualTo(ana.getId());
    }

    @Test
    void ignoresOtherEventsAndStrangeIdentities() throws Exception {
        anaShares();

        send("{\"event\":\"room_started\",\"room\":{\"name\":\"xove\"}}").andExpect(status().isOk());
        send("{\"event\":\"participant_left\",\"participant\":{\"identity\":\"bot\",\"sid\":\"PA_ana\"}}")
                .andExpect(status().isOk());

        assertThat(holder()).isEqualTo(ana.getId());
    }

    @Test
    void acceptsASignedEventWithoutANameAndChangesNothing() throws Exception {
        anaShares();

        send("{\"participant\":{\"identity\":\"user-%d\",\"sid\":\"PA_ana\"}}".formatted(ana.getId()))
                .andExpect(status().isOk());

        assertThat(holder()).isEqualTo(ana.getId());
    }

    // ---- helpers ----

    private void anaShares() {
        slot.take(ana.getId(), "Ana", null, new SharingConnection("PA_ana", "TR_ana_screen"));
    }

    private Long holder() {
        return slot.current().orElseThrow().userId();
    }

    private ResultActions send(String body) throws Exception {
        return send(body, WebhookSigner.sign(body, ISSUER, SECRET, Instant.now()));
    }

    private ResultActions send(String body, String authorization) throws Exception {
        return mvc.perform(post("/api/livekit/webhook")
                .header("Authorization", authorization)
                .contentType("application/webhook+json")
                .content(body));
    }

    private static String left(User who, String participantSid) {
        return """
                {"event":"participant_left","room":{"name":"xove"},\
                "participant":{"identity":"user-%d","sid":"%s","state":"DISCONNECTED"},\
                "id":"EV_1","createdAt":"1790545830"}""".formatted(who.getId(), participantSid);
    }

    private static String unpublished(User who, String participantSid, String trackSid, String source) {
        return """
                {"event":"track_unpublished","room":{"name":"xove"},\
                "participant":{"identity":"user-%d","sid":"%s"},\
                "track":{"sid":"%s","type":"VIDEO","source":"%s"},\
                "id":"EV_2","createdAt":"1790545830"}""".formatted(who.getId(), participantSid, trackSid, source);
    }

    private User member(String subject, String email, String name) {
        User user = userService.recordGoogleLogin(subject, email, name, null);
        user.changeStatus(UserStatus.MEMBER);
        return users.save(user);
    }
}
