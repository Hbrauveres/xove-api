package dev.hbrauveres.xove.room;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** The room's seats and queue through HTTP (spec 0060). The rules themselves: {@link RoomSeatsTest}. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RoomControllerTest {

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired RoomSeats seats;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        seats.clear();
        for (int i = 1; i <= 21; i++) {
            member("sub-m" + i, "m" + i + "@example.com");
        }
        userService.recordGoogleLogin("sub-stranger", "stranger@example.com", "Stranger", null);
    }

    @Test
    void signedOutGets401() throws Exception {
        mvc.perform(post("/api/room/enter").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void nonMembersGet403() throws Exception {
        enter("sub-stranger").andExpect(status().isForbidden());
    }

    @Test
    void enteringNeedsTheCsrfToken() throws Exception {
        mvc.perform(post("/api/room/enter").with(as("sub-m1"))).andExpect(status().isForbidden());
    }

    // AC-8
    @Test
    void twentyGetInAndThe21stWaitsWithTheirPlace() throws Exception {
        for (int i = 1; i <= 20; i++) {
            enter("sub-m" + i).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("in"));
        }

        enter("sub-m21")
                .andExpect(jsonPath("$.status").value("waiting"))
                .andExpect(jsonPath("$.place").value(1))
                .andExpect(jsonPath("$.until").doesNotExist());
    }

    @Test
    void enteringFromALiveConnectionConfirmsTheSeat() throws Exception {
        User m1 = users.findByGoogleSubject("sub-m1").orElseThrow();

        mvc.perform(post("/api/room/enter").with(as("sub-m1")).with(csrf())
                        .contentType("application/json").content("{\"participantSid\":\"PA_m1\"}"))
                .andExpect(jsonPath("$.status").value("in"));

        org.assertj.core.api.Assertions.assertThat(seats.keptUntil(m1.getId())).isEmpty();
    }

    @Test
    void acceptingWithoutAnOfferIs409() throws Exception {
        mvc.perform(post("/api/room/accept").with(as("sub-m1")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "There's no seat waiting for you. Keep this page open: you'll get one when it's your turn."));
    }

    @Test
    void cancellingLeavesTheQueue() throws Exception {
        for (int i = 1; i <= 20; i++) {
            enter("sub-m" + i);
        }
        enter("sub-m21");

        mvc.perform(post("/api/room/cancel").with(as("sub-m21")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post("/api/room/leave").with(as("sub-m21")).with(csrf())).andExpect(status().isNoContent());

        enter("sub-m21").andExpect(jsonPath("$.place").value(1));
    }

    private ResultActions enter(String subject) throws Exception {
        return mvc.perform(post("/api/room/enter").with(as(subject)).with(csrf()));
    }

    private RequestPostProcessor as(String subject) {
        return oidcLogin().idToken(token -> token.subject(subject));
    }

    private void member(String subject, String email) {
        User user = userService.recordGoogleLogin(subject, email, subject, null);
        user.changeStatus(UserStatus.MEMBER);
        users.save(user);
    }
}
