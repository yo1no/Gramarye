package com.yo1no.gramarye.magic.network;

import java.util.function.BooleanSupplier;

/** Bounded input bookkeeping. No sender, sequence, world or gameplay authority. */
final class P9FreshPressState {
    static final int MAX_FRESH_EDGES = 8;
    static final int MAX_CLICK_DRAIN = 32;
    private int pending;
    private boolean held;
    private boolean overLimit;

    void action(int action, boolean eligible) {
        if (action == 0) { held = false; return; }
        if (action != 1) { return; }
        boolean wasHeld = held;
        held = true;
        if (!eligible || wasHeld || overLimit) { return; }
        if (pending == MAX_FRESH_EDGES) { overLimit = true; }
        else { pending++; }
    }

    /** Negative means the whole frame must be dropped and enter bounded flush. */
    int takeEligible(int nativeClicks, boolean observedEmpty) {
        int count = pending;
        boolean valid = observedEmpty && !overLimit && nativeClicks >= count;
        pending = 0;
        overLimit = false;
        return valid ? count : -1;
    }

    void invalidate() {
        pending = 0;
        held = false;
        overLimit = false;
    }

    static Drain drain(BooleanSupplier consumeClick) {
        for (int count = 0; count < MAX_CLICK_DRAIN; count++) {
            if (!consumeClick.getAsBoolean()) { return new Drain(count, true); }
        }
        return new Drain(MAX_CLICK_DRAIN, false);
    }

    record Drain(int clicks, boolean empty) { }
}
