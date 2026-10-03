package dev.hbrauveres.xove.stream;

/**
 * Which LiveKit connection and track a stream is sent from, so a LiveKit report
 * can be matched to the stream it's about. Both are required to start a stream.
 * Never shown to other people.
 *
 * @param participantSid the connection, e.g. "PA_…"
 * @param trackSid       the screen or camera track, e.g. "TR_…"
 */
public record SharingConnection(String participantSid, String trackSid) {

    boolean isComplete() {
        return participantSid != null && !participantSid.isBlank()
                && trackSid != null && !trackSid.isBlank();
    }
}
