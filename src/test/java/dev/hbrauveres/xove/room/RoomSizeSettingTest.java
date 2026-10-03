package dev.hbrauveres.xove.room;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import dev.hbrauveres.xove.TestcontainersConfiguration;
import dev.hbrauveres.xove.user.User;
import dev.hbrauveres.xove.user.UserRepository;
import dev.hbrauveres.xove.user.UserService;
import dev.hbrauveres.xove.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** The room's size comes from XOVE_ROOM_SEATS: staging uses 3 to try the queue (spec 0060). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "XOVE_ROOM_SEATS=2")
@Transactional
class RoomSizeSettingTest {

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired RoomSeats seats;

    @Test
    void theThirdPersonWaitsInARoomOfTwo() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        seats.clear();
        for (String who : new String[] {"sub-a", "sub-b", "sub-c"}) {
            User user = userService.recordGoogleLogin(who, who + "@example.com", who, null);
            user.changeStatus(UserStatus.MEMBER);
            users.save(user);
        }

        for (String who : new String[] {"sub-a", "sub-b"}) {
            mvc.perform(post("/api/room/enter").with(oidcLogin().idToken(t -> t.subject(who))).with(csrf()))
                    .andExpect(jsonPath("$.status").value("in"));
        }
        mvc.perform(post("/api/room/enter").with(oidcLogin().idToken(t -> t.subject("sub-c"))).with(csrf()))
                .andExpect(jsonPath("$.status").value("waiting"))
                .andExpect(jsonPath("$.place").value(1));
    }
}
