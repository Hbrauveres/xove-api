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
        room = room == null ? new Room(null, null) : room;
    }

    public boolean isAdmin(String email) {
        return email != null && adminEmails.contains(User.normalizeEmail(email));
    }

    /**
     * The room (spec 0060).
     *
     * @param seats people in the room at once, sharers included: 1 to 20, 20 when not set
     *              ({@code XOVE_ROOM_SEATS}); a test environment can use fewer to try the queue
     * @param streamPlaces streams (screens and cameras) live at once: 1 to 6, 6 when not set
     *                     ({@code XOVE_STREAM_PLACES}, a setting both apps share; spec 0191)
     */
    public record Room(Integer seats, Integer streamPlaces) {

        public static final int MAX_SEATS = 20;
        public static final int MAX_STREAM_PLACES = 6;

        public Room {
            if (seats == null) {
                seats = MAX_SEATS;
            }
            if (seats < 1 || seats > MAX_SEATS) {
                throw new IllegalArgumentException(
                        "XOVE_ROOM_SEATS must be a whole number from 1 to " + MAX_SEATS + ", not " + seats);
            }
            if (streamPlaces == null) {
                streamPlaces = MAX_STREAM_PLACES;
            }
            if (streamPlaces < 1 || streamPlaces > MAX_STREAM_PLACES) {
                throw new IllegalArgumentException("XOVE_STREAM_PLACES must be a whole number from 1 to "
                        + MAX_STREAM_PLACES + ", not " + streamPlaces);
            }
        }
    }
}
