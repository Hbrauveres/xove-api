package dev.hbrauveres.xove.room;

/** Someone tried to take a seat that wasn't offered to them, or whose offer ran out. */
public class NoSeatOfferException extends RuntimeException {

    public NoSeatOfferException() {
        super("There's no seat waiting for you. Keep this page open: you'll get one when it's your turn.");
    }
}
