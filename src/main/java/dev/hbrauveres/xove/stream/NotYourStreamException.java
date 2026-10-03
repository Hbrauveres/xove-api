package dev.hbrauveres.xove.stream;

/** Someone tried to change a stream that isn't theirs, or that they don't have. */
public class NotYourStreamException extends RuntimeException {

    public NotYourStreamException() {
        super("Only the person sharing can change how their stream is sent.");
    }
}
