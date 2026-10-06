package dev.hbrauveres.xove.livekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hbrauveres.xove.TestcontainersConfiguration;
import dev.hbrauveres.xove.room.RoomSeats;
import dev.hbrauveres.xove.stream.SharingConnection;
import dev.hbrauveres.xove.stream.Stream;
import dev.hbrauveres.xove.stream.StreamKind;
import dev.hbrauveres.xove.stream.StreamSettings;
import dev.hbrauveres.xove.stream.Streams;
import dev.hbrauveres.xove.stream.Watching;
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

/** LiveKit tells the API who left and which tracks stopped; the streams follow (specs 0038, 0060). */
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
    @Autowired Streams streams;
    @Autowired RoomSeats seats;
    @Autowired Watching watching;

    MockMvc mvc;
    User ana;
    User bruno;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ana = member("sub-ana", "ana@example.com", "Ana");
        bruno = member("sub-bruno", "bruno@example.com", "Bruno");
        streams.all().forEach(s -> streams.stop(s.userId(), s.kind()));
        seats.clear();
    }

    // 0038 AC-1; 0060 AC-4: all of a person's streams end when their connection leaves.
    @Test
    void endsAllOfAPersonsStreamsWhenTheirConnectionLeaves() throws Exception {
        anaShares();
        anaTurnsOnHerCamera();

        send(left(ana, "PA_ana")).andExpect(status().isOk());

        mvc.perform(get("/api/streams").with(oidcLogin().idToken(t -> t.subject("sub-bruno"))))
                .andExpect(jsonPath("$.streams").isEmpty());
    }

    // 0038 AC-2
    @Test
    void endsTheScreenWhenItsTrackIsUnpublished() throws Exception {
        anaShares();

        send(unpublished(ana, "PA_ana", "TR_ana_screen", "SCREEN_SHARE")).andExpect(status().isOk());

        assertThat(streams.all()).isEmpty();
    }

    // 0060: a camera ends the same way, and only it.
    @Test
    void endsOnlyTheCameraWhenItsTrackIsUnpublished() throws Exception {
        anaShares();
        anaTurnsOnHerCamera();

        send(unpublished(ana, "PA_ana", "TR_ana_camera", "CAMERA")).andExpect(status().isOk());

        assertThat(streams.all()).extracting(Stream::kind).containsExactly(StreamKind.SCREEN);
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
        send(unpublished(ana, "PA_ana", "TR_ana_screen", "MICROPHONE")).andExpect(status().isOk());

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

        assertThat(streams.all()).isEmpty();
    }

    // AC-7
    @Test
    void ignoresAnotherConnectionOfTheSharer() throws Exception {
        anaShares();

        send(left(ana, "PA_ana_phone")).andExpect(status().isOk());

        assertThat(holder()).isEqualTo(ana.getId());
    }

    // Spec 0044, AC-5: each environment only acts on its own room.
    @Test
    void ignoresEventsFromAnotherRoom() throws Exception {
        anaShares();

        send(left(ana, "PA_ana").replace("\"name\":\"xove\"", "\"name\":\"xove-stage\""))
                .andExpect(status().isOk());

        assertThat(holder()).isEqualTo(ana.getId());
    }

    @Test
    void ignoresEventsWithoutARoom() throws Exception {
        anaShares();

        send(left(ana, "PA_ana").replace("\"room\":{\"name\":\"xove\"},", "")).andExpect(status().isOk());

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

    // ---- who watches what (spec 0104) ----

    @Test
    void leavingTheRoomForgetsWhatYouWatched() throws Exception {
        watching.reset();
        streams.start(bruno.getId(), "Bruno", null, StreamKind.SCREEN, new SharingConnection("PA_bruno", "TR_b"),
                StreamSettings.defaultFor(StreamKind.SCREEN));
        seats.enter(ana.getId());
        send(joined(ana, "PA_ana"));
        watching.watch(ana.getId(), bruno.getId(), StreamKind.SCREEN);
        assertThat(watching.current(id -> true, streams)).hasSize(1);

        send(left(ana, "PA_ana")).andExpect(status().isOk());

        assertThat(watching.current(id -> true, streams)).isEmpty();
    }

    // ---- seats (spec 0060) ----

    // AC-4: the streams end at once, the seat is kept for 30 seconds after a closed tab.
    @Test
    void closingTheTabEndsTheStreamsAndKeepsTheSeatFor30Seconds() throws Exception {
        seats.enter(ana.getId());
        send(joined(ana, "PA_ana")).andExpect(status().isOk());
        assertThat(seats.keptUntil(ana.getId())).isEmpty();
        anaShares();

        Instant before = Instant.now();
        send(left(ana, "PA_ana", "CLIENT_INITIATED")).andExpect(status().isOk());

        assertThat(streams.all()).isEmpty();
        assertThat(seats.isSeated(ana.getId())).isTrue();
        assertThat(seats.keptUntil(ana.getId())).hasValueSatisfying(until ->
                assertThat(until).isBetween(before.plusSeconds(30), Instant.now().plusSeconds(30)));
    }

    @Test
    void aDroppedConnectionKeepsTheSeatFor60Seconds() throws Exception {
        seats.enter(ana.getId());
        send(joined(ana, "PA_ana"));

        Instant before = Instant.now();
        send(left(ana, "PA_ana")).andExpect(status().isOk());

        assertThat(seats.keptUntil(ana.getId())).hasValueSatisfying(until ->
                assertThat(until).isBetween(before.plusSeconds(60), Instant.now().plusSeconds(60)));
    }

    @Test
    void anotherConnectionLeavingKeepsTheSeatAsItIs() throws Exception {
        seats.enter(ana.getId());
        send(joined(ana, "PA_ana_new"));

        send(left(ana, "PA_ana_old", "CLIENT_INITIATED")).andExpect(status().isOk());

        assertThat(seats.keptUntil(ana.getId())).isEmpty();
    }

    // ---- helpers ----

    private void anaShares() {
        streams.start(ana.getId(), "Ana", null, StreamKind.SCREEN, new SharingConnection("PA_ana", "TR_ana_screen"),
                StreamSettings.defaultFor(StreamKind.SCREEN));
    }

    private void anaTurnsOnHerCamera() {
        streams.start(ana.getId(), "Ana", null, StreamKind.CAMERA, new SharingConnection("PA_ana", "TR_ana_camera"),
                StreamSettings.defaultFor(StreamKind.CAMERA));
    }

    /** Whose stream is live: there's only ever Ana's in these tests. */
    private Long holder() {
        return streams.all().getFirst().userId();
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

    /** A connection that dropped: LiveKit gives a reason other than the client leaving. */
    private static String left(User who, String participantSid) {
        return left(who, participantSid, "SIGNAL_CLOSE");
    }

    private static String left(User who, String participantSid, String reason) {
        return """
                {"event":"participant_left","room":{"name":"xove"},\
                "participant":{"identity":"user-%d","sid":"%s","state":"DISCONNECTED","disconnectReason":"%s"},\
                "id":"EV_1","createdAt":"1790545830"}""".formatted(who.getId(), participantSid, reason);
    }

    private static String joined(User who, String participantSid) {
        return """
                {"event":"participant_joined","room":{"name":"xove"},\
                "participant":{"identity":"user-%d","sid":"%s","state":"ACTIVE"},\
                "id":"EV_3","createdAt":"1790545830"}""".formatted(who.getId(), participantSid);
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
