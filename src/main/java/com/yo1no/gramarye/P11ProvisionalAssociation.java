package com.yo1no.gramarye;

import java.util.Objects;

/** One constructor attempt's association-only witness. Never source or material authority. */
final class P11ProvisionalAssociation {
    private final Object canonical;
    private final Object previous;
    private final Object candidate;
    private final Object source;
    private final long epoch;
    private final long version;
    private boolean escaped;
    private boolean copying;
    private boolean terminal;
    private int pending;

    P11ProvisionalAssociation(Object canonical, Object previous, Object candidate,
            Object source, long epoch, long version) {
        this.canonical = Objects.requireNonNull(canonical, "canonical");
        this.previous = Objects.requireNonNull(previous, "previous");
        this.candidate = Objects.requireNonNull(candidate, "candidate");
        this.source = Objects.requireNonNull(source, "source");
        if (previous == candidate || epoch <= 0 || version < 0) {
            throw new IllegalArgumentException("P11_INVALID_PROVISIONAL_ASSOCIATION");
        }
        this.epoch = epoch;
        this.version = version;
    }

    void effectOrUnknownEscape() { escaped = true; }
    void copyStarted() { copying = true; }

    void pendingConsumer() {
        if (terminal || pending == Integer.MAX_VALUE) { escaped = true; }
        else { pending++; }
    }

    void consumerTerminal() {
        if (terminal || pending == 0) { escaped = true; }
        else { pending--; }
    }

    boolean withdrawable(Object exactCanonical, Object receiver, Object exactSource,
            long currentEpoch, long currentVersion, boolean previousComplete, boolean cleanupLegal) {
        return !terminal && !escaped && !copying && pending == 0
                && canonical == exactCanonical && candidate == receiver && source == exactSource
                && epoch == currentEpoch && version == currentVersion
                && previousComplete && cleanupLegal;
    }

    /** The owner must check exact PA/player and source again immediately before this call. */
    boolean withdraw(Object exactCanonical, Object receiver, Object exactSource,
            long currentEpoch, long currentVersion, boolean previousComplete, boolean cleanupLegal,
            Runnable restoreExactAssociation) {
        Objects.requireNonNull(restoreExactAssociation, "restoreExactAssociation");
        if (!withdrawable(exactCanonical, receiver, exactSource, currentEpoch, currentVersion,
                previousComplete, cleanupLegal)) { terminal = true; return false; }
        terminal = true; // A throwing restoration cannot be retried as an unused receipt.
        restoreExactAssociation.run();
        return true;
    }

    Object previousActor() { return previous; }
    Object candidateActor() { return candidate; }
    void finish() { terminal = true; }
}
