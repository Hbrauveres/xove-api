package dev.kryora.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kryora.live.access.AccessRequest;
import dev.kryora.live.access.AccessRequestRepository;
import dev.kryora.live.access.AccessRequestStatus;
import dev.kryora.live.user.User;
import dev.kryora.live.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PersistenceTest {

    @Autowired UserRepository users;
    @Autowired AccessRequestRepository requests;

    @Test
    void storesEmailsLowercase() {
        users.saveAndFlush(new User("Friend@Gmail.com", "Friend", null));
        assertThat(users.findByEmail("friend@gmail.com")).isPresent();
    }

    @Test
    void rejectsDuplicateEmails() {
        users.saveAndFlush(new User("a@gmail.com", "A", null));
        assertThatThrownBy(() -> users.saveAndFlush(new User("A@gmail.com", "A again", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsOnlyOnePendingRequestPerUser() {
        User user = users.saveAndFlush(new User("b@gmail.com", "B", null));
        requests.saveAndFlush(new AccessRequest(user, "let me in"));
        assertThatThrownBy(() -> requests.saveAndFlush(new AccessRequest(user, "again")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsANewRequestAfterADecline() {
        User admin = users.saveAndFlush(new User("admin@gmail.com", "Admin", null));
        User user = users.saveAndFlush(new User("c@gmail.com", "C", null));
        AccessRequest first = requests.saveAndFlush(new AccessRequest(user, "first"));
        first.decide(AccessRequestStatus.DECLINED, admin);
        requests.saveAndFlush(first);
        requests.saveAndFlush(new AccessRequest(user, "second try"));
        assertThat(requests.findByUserIdAndStatus(user.getId(), AccessRequestStatus.PENDING)).isPresent();
    }
}
