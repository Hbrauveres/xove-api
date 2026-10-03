package dev.hbrauveres.xove.livekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "xove.admin-emails=admin@example.com",
        "xove.livekit.url=wss://rtc.example.com",
        "xove.livekit.api-key=APItest",
        "xove.livekit.api-secret=0123456789abcdef0123456789abcdef0123456789abcdef",
})
@Transactional
class LiveKitControllerTest {

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired RoomSeats seats;

    MockMvc mvc;
    User friend;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        seats.clear();
        friend = userService.recordGoogleLogin("sub-friend", "friend@example.com", "Friend Person", null);
        friend.changeStatus(UserStatus.MEMBER);
        users.save(friend);
        userService.recordGoogleLogin("sub-stranger", "stranger@example.com", "Stranger", null);
    }

    @Test
    void signedOutGets401() throws Exception {
        mvc.perform(post("/api/livekit/token").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void nonMembersGet403() throws Exception {
        mvc.perform(post("/api/livekit/token").with(as("sub-stranger")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void withoutTheCsrfTokenItIsRejected() throws Exception {
        mvc.perform(post("/api/livekit/token").with(as("sub-friend"))).andExpect(status().isForbidden());
    }

    // Spec 0060, AC-8: the room holds only the people with a seat.
    @Test
    void withoutASeatThereIsNoToken() throws Exception {
        mvc.perform(post("/api/livekit/token").with(as("sub-friend")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Wait for your turn: the room is full."));
    }

    @Test
    void aMemberWithASeatGetsWhereToConnectAndAToken() throws Exception {
        seats.enter(friend.getId());

        String body = mvc.perform(post("/api/livekit/token").with(as("sub-friend")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("wss://rtc.example.com"))
                .andExpect(jsonPath("$.room").value("xove"))
                .andExpect(jsonPath("$.identity").value("user-" + friend.getId()))
                .andReturn().getResponse().getContentAsString();

        var claims = SignedJWT.parse(JsonPath.<String>read(body, "$.token")).getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo("user-" + friend.getId());
        assertThat(claims.getStringClaim("name")).isEqualTo("Friend Person");
    }

    private RequestPostProcessor as(String subject) {
        return oidcLogin().idToken(token -> token.subject(subject));
    }
}
