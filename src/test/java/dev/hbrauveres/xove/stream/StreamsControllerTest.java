package dev.hbrauveres.xove.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hbrauveres.xove.TestcontainersConfiguration;
import dev.hbrauveres.xove.room.RoomSeats;
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
class StreamsControllerTest {

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired Streams streams;
    @Autowired RoomSeats seats;
    @Autowired Watching watching;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        streams.clear();
        seats.clear();
        watching.reset();

        userService.recordGoogleLogin("sub-admin", "admin@example.com", "Admin", null);
        member("sub-friend", "friend@example.com", "Friend", "https://img/friend");
        for (int i = 1; i <= 3; i++) {
            member("sub-m" + i, "m" + i + "@example.com", "M" + i, null);
        }
        userService.recordGoogleLogin("sub-stranger", "stranger@example.com", "Stranger", null);
    }

    // ---- who gets in ----

    @Test
    void signedOutGets401() throws Exception {
        mvc.perform(get("/api/streams")).andExpect(status().isUnauthorized());
    }

    @Test
    void nonMembersGet403() throws Exception {
        mvc.perform(get("/api/streams").with(as("sub-stranger"))).andExpect(status().isForbidden());
        start("sub-stranger", "screen").andExpect(status().isForbidden());
    }

    @Test
    void startingWithoutTheCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/streams").with(as("sub-friend"))
                        .contentType("application/json").content(body("sub-friend", "screen", "")))
                .andExpect(status().isForbidden());
    }

    // ---- streams through HTTP ----

    @Test
    void nobodyIsSharingAtFirst() throws Exception {
        mvc.perform(get("/api/streams").with(as("sub-friend")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streams").isEmpty())
                .andExpect(jsonPath("$.free").value(6));
    }

    // Spec 0060: only someone in the room can start a stream, and the poll says if you're in.
    @Test
    void startingWithoutASeatIsRefused() throws Exception {
        mvc.perform(get("/api/streams").with(as("sub-friend"))).andExpect(jsonPath("$.seated").value(false));

        mvc.perform(post("/api/streams").with(as("sub-friend")).with(csrf())
                        .contentType("application/json").content(body("sub-friend", "screen", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Enter the room first."));

        mvc.perform(post("/api/room/enter").with(as("sub-friend")).with(csrf()));
        mvc.perform(get("/api/streams").with(as("sub-friend"))).andExpect(jsonPath("$.seated").value(true));
    }

    @Test
    void aMemberStartsAStreamAndEveryoneSeesIt() throws Exception {
        start("sub-friend", "screen")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streams[0].kind").value("screen"))
                .andExpect(jsonPath("$.streams[0].name").value("Friend"))
                .andExpect(jsonPath("$.streams[0].avatarUrl").value("https://img/friend"))
                .andExpect(jsonPath("$.streams[0].userId").isNumber())
                .andExpect(jsonPath("$.streams[0].since").isString())
                .andExpect(jsonPath("$.streams[0].mine").value(true))
                .andExpect(jsonPath("$.free").value(5));

        mvc.perform(get("/api/streams").with(as("sub-admin")))
                .andExpect(jsonPath("$.streams[0].name").value("Friend"))
                .andExpect(jsonPath("$.streams[0].mine").value(false));
    }

    // AC-2
    @Test
    void aScreenAndACameraTakeTwoPlaces() throws Exception {
        start("sub-friend", "screen");

        start("sub-friend", "camera")
                .andExpect(jsonPath("$.streams.length()").value(2))
                .andExpect(jsonPath("$.streams[1].kind").value("camera"))
                .andExpect(jsonPath("$.streams[1].settings.quality").value("720p"))
                .andExpect(jsonPath("$.free").value(4));
    }

    // AC-1
    @Test
    void aSeventhStreamIsRefused() throws Exception {
        for (String who : new String[] {"sub-m1", "sub-m2", "sub-m3"}) {
            start(who, "screen").andExpect(status().isOk());
            start(who, "camera").andExpect(status().isOk());
        }

        start("sub-friend", "screen")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The room already has 6 streams."))
                .andExpect(jsonPath("$.reason").value("full"));
        assertThat(streams.all()).hasSize(6);
    }

    @Test
    void nobodyIsPushedOut() throws Exception {
        start("sub-friend", "screen");

        start("sub-admin", "screen");

        mvc.perform(get("/api/streams").with(as("sub-friend")))
                .andExpect(jsonPath("$.streams.length()").value(2));
    }

    @Test
    void youStopYourOwnStream() throws Exception {
        start("sub-friend", "screen");
        start("sub-friend", "camera");

        stop("sub-friend", "camera")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streams.length()").value(1))
                .andExpect(jsonPath("$.streams[0].kind").value("screen"));
    }

    @Test
    void stoppingSomeoneElsesStreamChangesNothing() throws Exception {
        start("sub-friend", "screen");

        stop("sub-admin", "screen").andExpect(status().isOk());

        assertThat(streams.all()).hasSize(1);
    }

    @Test
    void anUnknownKindIsRefused() throws Exception {
        start("sub-friend", "microphone")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A stream is a screen or a camera."));
        stop("sub-friend", "microphone").andExpect(status().isBadRequest());
        assertThat(streams.all()).isEmpty();
    }

    // ---- the connection (spec 0038) ----

    @Test
    void startingSaysWhichConnectionAndNobodySeesIt() throws Exception {
        start("sub-friend", "screen")
                .andExpect(content().string(not(containsString("PA_sub-friend"))))
                .andExpect(content().string(not(containsString("TR_sub-friend"))));

        Long friend = streams.all().getFirst().userId();
        assertThat(streams.connectionLeft(friend, "PA_phone")).isFalse();
        assertThat(streams.connectionLeft(friend, "PA_sub-friend")).isTrue();
    }

    @Test
    void startingWithoutSayingWhichConnectionIsRefused() throws Exception {
        mvc.perform(post("/api/streams").with(as("sub-friend")).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/streams").with(as("sub-friend")).with(csrf())
                        .contentType("application/json").content("{\"kind\":\"screen\",\"participantSid\":\"PA_laptop\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Your browser didn't say which video connection is sharing. Reload the page and try again."));

        assertThat(streams.all()).isEmpty();
    }

    // ---- what each stream is sent with (spec 0086) ----

    @Test
    void everyoneSeesTheSettingsAStreamStartedWith() throws Exception {
        start("sub-friend", "screen", ",\"quality\":\"720p\",\"mode\":\"sharp\"")
                .andExpect(jsonPath("$.streams[0].settings.quality").value("720p"))
                .andExpect(jsonPath("$.streams[0].settings.mode").value("sharp"));

        mvc.perform(get("/api/streams").with(as("sub-admin")))
                .andExpect(jsonPath("$.streams[0].settings.quality").value("720p"));
    }

    @Test
    void aMissingSettingTakesItsDefault() throws Exception {
        start("sub-friend", "screen", ",\"mode\":\"sharp\"")
                .andExpect(jsonPath("$.streams[0].settings.quality").value("1080p"))
                .andExpect(jsonPath("$.streams[0].settings.mode").value("sharp"));
        start("sub-friend", "camera")
                .andExpect(jsonPath("$.streams[1].settings.quality").value("720p"))
                .andExpect(jsonPath("$.streams[1].settings.mode").value("smooth"));
    }

    @Test
    void aCameraCannotStartAt1080p() throws Exception {
        start("sub-friend", "camera", ",\"quality\":\"1080p\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Pick a quality of 720p or 480p, and a mode of smooth or sharp."));
    }

    @Test
    void youChangeTheSettingsOfYourStreamAndEveryoneSeesIt() throws Exception {
        start("sub-friend", "screen");

        settings("sub-friend", "screen", "{\"quality\":\"480p\",\"mode\":\"sharp\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streams[0].settings.quality").value("480p"));

        mvc.perform(get("/api/streams").with(as("sub-admin")))
                .andExpect(jsonPath("$.streams[0].settings.quality").value("480p"))
                .andExpect(jsonPath("$.streams[0].settings.mode").value("sharp"));
    }

    @Test
    void someoneElseCannotChangeTheSettings() throws Exception {
        start("sub-friend", "screen");

        settings("sub-admin", "screen", "{\"quality\":\"480p\",\"mode\":\"smooth\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only the person sharing can change how their stream is sent."));
    }

    @Test
    void unknownSettingsAreRefused() throws Exception {
        start("sub-friend", "screen");

        settings("sub-friend", "screen", "{\"quality\":\"4k\",\"mode\":\"smooth\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Pick a quality of 1080p, 720p or 480p, and a mode of smooth or sharp."));
        settings("sub-friend", "screen", "").andExpect(status().isBadRequest());
    }

    @Test
    void changingTheSettingsNeedsTheCsrfToken() throws Exception {
        start("sub-friend", "screen");

        mvc.perform(post("/api/streams/screen/settings").with(as("sub-friend"))
                        .contentType("application/json").content("{\"quality\":\"480p\",\"mode\":\"smooth\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- the room's seats and who watches what (spec 0104) ----

    @Test
    void theViewSaysHowFullTheRoomIs() throws Exception {
        start("sub-m1", "screen").andExpect(status().isOk());
        mvc.perform(post("/api/room/enter").with(as("sub-m2")).with(csrf()));

        mvc.perform(get("/api/streams").with(as("sub-friend")))
                .andExpect(jsonPath("$.seats.total").value(20))
                .andExpect(jsonPath("$.seats.taken").value(2))
                .andExpect(jsonPath("$.seats.waiting").value(0))
                .andExpect(jsonPath("$.watching").isEmpty());
    }

    @Test
    void everyoneSeesWhoWatchesWhichStream() throws Exception {
        start("sub-m1", "screen");
        Long m1 = idOf("sub-m1");
        mvc.perform(post("/api/room/enter").with(as("sub-m2")).with(csrf()));

        watch("sub-m2", "{\"sharerId\":" + m1 + ",\"kind\":\"screen\"}").andExpect(status().isNoContent());

        mvc.perform(get("/api/streams").with(as("sub-friend")))
                .andExpect(jsonPath("$.watching.length()").value(1))
                .andExpect(jsonPath("$.watching[0].userId").value(idOf("sub-m2")))
                .andExpect(jsonPath("$.watching[0].sharerId").value(m1))
                .andExpect(jsonPath("$.watching[0].kind").value("screen"))
                .andExpect(jsonPath("$.watching[0].mine").value(false));
        mvc.perform(get("/api/streams").with(as("sub-m2"))).andExpect(jsonPath("$.watching[0].mine").value(true));
    }

    @Test
    void anEmptyStageClearsWhatYouWatch() throws Exception {
        start("sub-m1", "screen");
        mvc.perform(post("/api/room/enter").with(as("sub-m2")).with(csrf()));
        watch("sub-m2", "{\"sharerId\":" + idOf("sub-m1") + ",\"kind\":\"screen\"}");

        watch("sub-m2", "{}").andExpect(status().isNoContent());

        mvc.perform(get("/api/streams").with(as("sub-friend"))).andExpect(jsonPath("$.watching").isEmpty());
    }

    @Test
    void aWatchFromSomeoneWithoutASeatIsNotListed() throws Exception {
        start("sub-m1", "screen");

        watch("sub-m3", "{\"sharerId\":" + idOf("sub-m1") + ",\"kind\":\"screen\"}").andExpect(status().isNoContent());

        mvc.perform(get("/api/streams").with(as("sub-friend"))).andExpect(jsonPath("$.watching").isEmpty());
        // Not even kept for later: seating them afterwards lists nothing.
        mvc.perform(post("/api/room/enter").with(as("sub-m3")).with(csrf()));
        mvc.perform(get("/api/streams").with(as("sub-friend"))).andExpect(jsonPath("$.watching").isEmpty());
    }

    @Test
    void aWatchNeedsAKnownKindAndBothParts() throws Exception {
        mvc.perform(post("/api/room/enter").with(as("sub-m2")).with(csrf()));

        watch("sub-m2", "{\"sharerId\":1,\"kind\":\"microphone\"}").andExpect(status().isBadRequest());
        watch("sub-m2", "{\"kind\":\"screen\"}").andExpect(status().isBadRequest());
    }

    @Test
    void reportingAWatchNeedsAMemberAndTheCsrfToken() throws Exception {
        mvc.perform(put("/api/streams/watching").with(as("sub-m2"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        watch("sub-stranger", "{}").andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private ResultActions watch(String subject, String json) throws Exception {
        return mvc.perform(put("/api/streams/watching").with(as(subject)).with(csrf())
                .contentType("application/json").content(json));
    }

    private Long idOf(String subject) {
        return users.findByGoogleSubject(subject).orElseThrow().getId();
    }


    private ResultActions start(String subject, String kind) throws Exception {
        return start(subject, kind, "");
    }

    private ResultActions start(String subject, String kind, String extra) throws Exception {
        mvc.perform(post("/api/room/enter").with(as(subject)).with(csrf()));
        return mvc.perform(post("/api/streams").with(as(subject)).with(csrf())
                .contentType("application/json").content(body(subject, kind, extra)));
    }

    private static String body(String subject, String kind, String extra) {
        return "{\"kind\":\"" + kind + "\",\"participantSid\":\"PA_" + subject + "\",\"trackSid\":\"TR_" + subject
                + "_" + kind + "\"" + extra + "}";
    }

    private ResultActions settings(String subject, String kind, String json) throws Exception {
        var request = post("/api/streams/" + kind + "/settings").with(as(subject)).with(csrf());
        return mvc.perform(json.isEmpty() ? request : request.contentType("application/json").content(json));
    }

    private ResultActions stop(String subject, String kind) throws Exception {
        return mvc.perform(post("/api/streams/" + kind + "/stop").with(as(subject)).with(csrf()));
    }

    private RequestPostProcessor as(String subject) {
        return oidcLogin().idToken(token -> token.subject(subject));
    }

    private void member(String subject, String email, String name, String avatarUrl) {
        User user = userService.recordGoogleLogin(subject, email, name, avatarUrl);
        user.changeStatus(UserStatus.MEMBER);
        users.save(user);
    }
}
