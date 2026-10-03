package dev.hbrauveres.xove.stream;

/** All the stream places are taken. */
public class StreamsFullException extends RuntimeException {

    public StreamsFullException() {
        super("The room already has " + Streams.MAX + " streams.");
    }
}
