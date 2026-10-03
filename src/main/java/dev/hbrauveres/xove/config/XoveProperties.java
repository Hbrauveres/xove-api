package dev.hbrauveres.xove.config;

import dev.hbrauveres.xove.user.User;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under "xove:" in application.yml. */
@ConfigurationProperties(prefix = "xove")
public record XoveProperties(List<String> adminEmails, Room room) {

    public XoveProperties {
        adminEmails = adminEmails == null
                ? List.of()
                : adminEmails.stream()
                        .filter(e -> !e.isBlank())
                        .map(User::normalizeEmail)
                        .toList();
        room = room == null ? new Room(null) : room;
    }

    public boolean isAdmin(String email) {
        return email != null && adminEmails.contains(User.normalizeEmail(email));
    }

    /**
     * The room (spec 0060).
     *
     * @param seats people in the room at once, sharers included: 1 to 20, 20 when not set
     *              ({@code XOVE_ROOM_SEATS}); staging uses fewer to try the queue
     */
    public record Room(Integer seats) {

        public static final int MAX_SEATS = 20;

        public Room {
            if (seats == null) {
                seats = MAX_SEATS;
            }
            if (seats < 1 || seats > MAX_SEATS) {
                throw new IllegalArgumentException(
                        "XOVE_ROOM_SEATS must be a whole number from 1 to " + MAX_SEATS + ", not " + seats);
            }
        }
    }
}
