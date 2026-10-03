package dev.hbrauveres.xove.screen;

/** Someone tried to stop, or change, a share that isn't theirs. */
public class NotTheHolderException extends RuntimeException {

    public NotTheHolderException() {
        this("Only the person sharing can stop the share.");
    }

    public NotTheHolderException(String message) {
        super(message);
    }
}