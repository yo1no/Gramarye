package com.yo1no.gramarye;

/** Field-local native stack lifetime. It grants no actor/source authority and holds no victims. */
final class P11NativeCreditLifetime {
    private int consumers;
    private boolean removalPending;

    void enter() { consumers = Math.incrementExact(consumers); }

    /** Called only after original setRemoved returned normally. */
    boolean removed() {
        if (consumers == 0) { return true; }
        removalPending = true;
        return false;
    }

    /** Only the original same-body dimension transition's successful revive calls this. */
    void revived() {
        if (consumers == 0 || !removalPending) {
            throw new IllegalStateException("P11_NATIVE_REVIVE_WITHOUT_PENDING_REMOVAL");
        }
        removalPending = false;
    }

    /** True means the normal-removal obligation reached its final active native consumer. */
    boolean leave() {
        if (consumers == 0) { throw new IllegalStateException("P11_NATIVE_CONSUMER_NOT_ENTERED"); }
        consumers--;
        if (consumers != 0 || !removalPending) { return false; }
        removalPending = false;
        return true;
    }
}
