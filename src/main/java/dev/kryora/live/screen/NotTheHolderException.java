package dev.kryora.live.screen;

/** Someone tried to stop a share that isn't theirs. */
public class NotTheHolderException extends RuntimeException {

    public NotTheHolderException() {
        super("Only the person sharing can stop the share.");
    }
}