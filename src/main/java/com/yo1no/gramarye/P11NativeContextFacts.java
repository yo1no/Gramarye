package com.yo1no.gramarye;

/** Observations of one original command engine; not a queue or permission provider. */
final class P11NativeContextFacts {
    enum Drain { NOT_RUN, RUNNING, EMPTY, QUOTA, OVERFLOW, THREW }
    private Drain drain = Drain.NOT_RUN;
    private boolean overflow;
    private boolean tracerClosed;
    private boolean terminal;
    private boolean outerNormal;
    private int discardedFrames;

    void started() {
        if (!terminal) { drain = Drain.RUNNING; }
    }

    void overflow() {
        if (!terminal) { overflow = true; }
    }

    void discarded() {
        if (!terminal && discardedFrames != Integer.MAX_VALUE) { discardedFrames++; }
    }

    void drained(boolean normal, int quota, boolean queueEmpty, boolean newCommandsEmpty) {
        if (terminal) { return; }
        drain = !normal ? Drain.THREW : overflow ? Drain.OVERFLOW
                : quota <= 0 ? Drain.QUOTA : queueEmpty && newCommandsEmpty ? Drain.EMPTY : Drain.NOT_RUN;
    }

    void tracerClosed() { tracerClosed = true; }

    boolean outerFinished(boolean normal) {
        if (terminal) { return false; }
        terminal = true;
        outerNormal = normal;
        return true;
    }

    Drain drain() { return drain; }
    boolean terminal() { return terminal; }
    boolean normal() { return outerNormal; }
    boolean tracerDidClose() { return tracerClosed; }
    int discardedFrames() { return discardedFrames; }
}
