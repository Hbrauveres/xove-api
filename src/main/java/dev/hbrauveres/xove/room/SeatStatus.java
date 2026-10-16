package dev.hbrauveres.xove.room;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.Instant;

/**
 * Where someone stands with the room (spec 0060):
 * - "in": they have a seat;
 * - "waiting": they're in the queue, at {@code place} (1 is next);
 * - "offered": a seat is held for them until {@code until}, {@code seconds} from now; they
 *   confirm or cancel. The countdown uses the seconds, so a browser's wrong clock doesn't matter.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeatStatus(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"in", "waiting", "offered"}) String status,
        @Schema(description = "While waiting: my place, 1 is next") Integer place,
        @Schema(description = "While offered: when the offer runs out") Instant until,
        @Schema(description = "While offered: the seconds left") Long seconds) {

    public static SeatStatus in() {
        return new SeatStatus("in", null, null, null);
    }

    public static SeatStatus waiting(int place) {
        return new SeatStatus("waiting", place, null, null);
    }

    public static SeatStatus offered(Instant until, Instant now) {
        long millis = Duration.between(now, until).toMillis();
        return new SeatStatus("offered", null, until, Math.max(0, (millis + 999) / 1000));
    }
}
