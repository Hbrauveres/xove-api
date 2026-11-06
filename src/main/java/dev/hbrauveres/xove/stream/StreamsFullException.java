package dev.hbrauveres.xove.stream;

/** All the stream places are taken. */
public class StreamsFullException extends RuntimeException {

    public StreamsFullException(int places) {
        super("The room already has " + places + " streams.");
    }
}
