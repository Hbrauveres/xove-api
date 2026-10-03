package dev.hbrauveres.xove.stream;

import java.util.Arrays;
import java.util.Optional;

/** What a stream shows: a shared screen or a camera (spec 0060). Never a microphone. */
public enum StreamKind {
    SCREEN("screen"),
    CAMERA("camera");

    private final String id;

    StreamKind(String id) {
        this.id = id;
    }

    /** How the frontend names it: "screen" or "camera". */
    public String id() {
        return id;
    }

    public static Optional<StreamKind> of(String id) {
        return Arrays.stream(values()).filter(k -> k.id.equals(id)).findFirst();
    }
}
