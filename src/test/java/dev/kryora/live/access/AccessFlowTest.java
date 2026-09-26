package dev.kryora.live.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "kryora.admin-emails=admin@example.com")
@Transactional
class AccessFlowTest {

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired AccessRequestRepository requests;
    @Autowired FindByIndexNameSessionRepository<? extends Session> sessions;

    MockMvc mvc;
    User admin;
    User friend;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = userService.recordGoogleLogin("sub-admin", "admin@example.com", "Admin", null);
        friend = userService.recordGoogleLogin("sub-friend", "friend@example.com", "Friend", null);
    }

    // ---- requesting access ----

    @Test
    void aNewUserCanRequestAccess() throws Exception {
        requestAccess("sub-friend", "{\"message\":\"it's me, Bruno\"}").andExpect(status().isCreated());

        assertThat(statusOf(friend)).isEqualTo(UserStatus.PENDING);
        mvc.perform(get("/api/me").with(asUser("sub-friend")))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void theMessageIsOptional() throws Exception {
        requestAccess("sub-friend", null).andExpect(status().isCreated());
    }

    @Test
    void aSecondRequestWhileWaitingIsRejected() throws Exception {
        requestAccess("sub-friend", "{}").andExpect(status().isCreated());
        requestAccess("sub-friend", "{}").andExpect(status().isConflict());
    }

    @Test
    void membersCannotRequestAccess() throws Exception {
        requestAccess("sub-admin", "{}").andExpect(status().isConflict());
    }

    @Test
    void errorsCarryAMessageTheUiCanShow() throws Exception {
        requestAccess("sub-admin", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You already have access"));
    }

    @Test
    void messagesOver500CharactersAreRejected() throws Exception {
        String longMessage = "x".repeat(501);
        requestAccess("sub-friend", "{\"message\":\"" + longMessage + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void requestingAccessNeedsCsrfToken() throws Exception {
        mvc.perform(post("/api/access-requests").with(asUser("sub-friend")))
                .andExpect(status().isForbidden());
    }

    // ---- who can use the admin endpoints ----

    @Test
    void adminEndpointsNeedLogin() throws Exception {
        mvc.perform(get("/api/admin/access-requests")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsRejectNonAdmins() throws Exception {
        mvc.perform(get("/api/admin/access-requests").with(asUser("sub-friend")))
                .andExpect(status().isForbidden());
    }

    // ---- approving and declining ----

    @Test
    void adminSeesPendingRequests() throws Exception {
        requestAccess("sub-friend", "{\"message\":\"hi\"}");

        mvc.perform(get("/api/admin/access-requests").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("friend@example.com"))
                .andExpect(jsonPath("$[0].message").value("hi"));
    }

    @Test
    void approvingMakesTheUserAMember() throws Exception {
        Long requestId = createRequestFor(friend);

        mvc.perform(post("/api/admin/access-requests/" + requestId + "/approve").with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(statusOf(friend)).isEqualTo(UserStatus.MEMBER);
        assertThat(requests.findById(requestId)).get()
                .extracting(AccessRequest::getStatus).isEqualTo(AccessRequestStatus.APPROVED);
    }

    @Test
    void aRequestCanOnlyBeDecidedOnce() throws Exception {
        Long requestId = createRequestFor(friend);
        mvc.perform(post("/api/admin/access-requests/" + requestId + "/approve").with(asAdmin()).with(csrf()));

        mvc.perform(post("/api/admin/access-requests/" + requestId + "/decline").with(asAdmin()).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void decliningKeepsTheUserOutButLetsThemAskAgain() throws Exception {
        Long requestId = createRequestFor(friend);

        mvc.perform(post("/api/admin/access-requests/" + requestId + "/decline").with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(statusOf(friend)).isEqualTo(UserStatus.DECLINED);

        requestAccess("sub-friend", "{\"message\":\"please?\"}").andExpect(status().isCreated());
    }

    @Test
    void unknownRequestsAre404() throws Exception {
        mvc.perform(post("/api/admin/access-requests/999999/approve").with(asAdmin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---- members ----

    @Test
    void adminListsMembers() throws Exception {
        mvc.perform(get("/api/admin/members").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("admin@example.com"))
                .andExpect(jsonPath("$[0].admin").value(true));
    }

    @Test
    void removingAMemberRevokesAccessAndSignsThemOut() throws Exception {
        friend.changeStatus(UserStatus.MEMBER);
        users.saveAndFlush(friend);
        saveSessionFor("sub-friend");
        assertThat(sessions.findByPrincipalName("sub-friend")).hasSize(1);

        mvc.perform(delete("/api/admin/members/" + friend.getId()).with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(statusOf(friend)).isEqualTo(UserStatus.NONE);
        assertThat(sessions.findByPrincipalName("sub-friend")).isEmpty();
    }

    @Test
    void adminsCannotBeRemoved() throws Exception {
        mvc.perform(delete("/api/admin/members/" + admin.getId()).with(asAdmin()).with(csrf()))
                .andExpect(status().isConflict());
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.ResultActions requestAccess(String subject, String json)
            throws Exception {
        var request = post("/api/access-requests").with(asUser(subject)).with(csrf());
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    private Long createRequestFor(User user) {
        AccessRequest request = requests.saveAndFlush(new AccessRequest(user, null));
        user.changeStatus(UserStatus.PENDING);
        users.saveAndFlush(user);
        return request.getId();
    }

    private UserStatus statusOf(User user) {
        return users.findById(user.getId()).orElseThrow().getStatus();
    }

    private void saveSessionFor(String principalName) {
        saveSession(sessions, principalName);
    }

    private static <S extends Session> void saveSession(FindByIndexNameSessionRepository<S> repository,
                                                        String principalName) {
        S session = repository.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);
        repository.save(session);
    }

    private RequestPostProcessor asUser(String subject) {
        return oidcLogin().idToken(token -> token.subject(subject));
    }

    private RequestPostProcessor asAdmin() {
        return oidcLogin()
                .idToken(token -> token.subject("sub-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
