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
import dev.hbrauveres.xove.user.User;
import dev.hbrauveres.xove.user.UserRepository;
import dev.hbrauveres.xove.user.UserService;
import dev.hbrauveres.xove.user.UserStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** Each environment has its own room, set with LIVEKIT_ROOM (spec 0044, AC-3). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "LIVEKIT_ROOM=xove-test",
        "xove.livekit.url=wss://rtc.example.com",
        "xove.livekit.api-key=APItest",
        "xove.livekit.api-secret=0123456789abcdef0123456789abcdef0123456789abcdef", // gitleaks:allow (fake test value)
})
@Transactional
class LiveKitRoomSettingTest {

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired UserRepository users;

    @Test
    void usesTheConfiguredRoom() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        User friend = userService.recordGoogleLogin("sub-friend", "friend@example.com", "Friend", null);
        friend.changeStatus(UserStatus.MEMBER);
        users.save(friend);

        String body = mvc.perform(post("/api/livekit/token")
                        .with(oidcLogin().idToken(t -> t.subject("sub-friend"))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room").value("xove-test"))
                .andReturn().getResponse().getContentAsString();

        var video = (Map<?, ?>) SignedJWT.parse(JsonPath.<String>read(body, "$.token")).getJWTClaimsSet().getClaim("video");
        assertThat(video.get("room")).isEqualTo("xove-test");
    }
}
