package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11ReceiptLedger.Observation.FAILED;
import static com.yo1no.gramarye.P11ReceiptLedger.Observation.SUCCEEDED;
import static com.yo1no.gramarye.P11ReceiptLedger.Observation.UNKNOWN;

import com.yo1no.gramarye.P11ReceiptLedger.Observation;
import java.util.Objects;

/** Bounded observations consumed by the original writer scope, not a source or save grant. */
final class P11NativeWriteFacts {
    enum Kind { NBT, STATISTICS, ADVANCEMENTS }

    record Result(Observation encode, Observation write, Observation close,
            Observation replace, boolean successful) {}

    private final Kind kind;
    private Observation encode = UNKNOWN;
    private Observation write = UNKNOWN;
    private Observation close = UNKNOWN;
    private Observation replace = UNKNOWN;
    private boolean failed;
    private boolean ioReturned;
    private Result terminal;

    P11NativeWriteFacts(Kind kind) { this.kind = Objects.requireNonNull(kind, "kind"); }

    void encoded(boolean normal) {
        if (terminal != null) { return; }
        encode = merge(encode, normal);
        failed |= !normal;
    }

    void written(boolean normal) {
        if (terminal != null) { return; }
        // FileUtils' joint helper does not reveal whether write or close failed.
        if (normal || kind != Kind.STATISTICS) { write = merge(write, normal); }
        failed |= !normal;
    }

    void closed(boolean normal) {
        if (terminal != null) { return; }
        if (normal || kind != Kind.STATISTICS) { close = merge(close, normal); }
        failed |= !normal;
    }

    void nbtBody(boolean normal) {
        if (kind != Kind.NBT || terminal != null) { return; }
        // A body exception may be IO or encoding: only normal return proves both.
        if (normal) { encoded(true); }
        written(normal);
    }

    void ioReturned(boolean normal) {
        if (kind != Kind.NBT || terminal != null) { return; }
        ioReturned |= normal;
        failed |= !normal;
    }

    void rejected() { if (terminal == null) { failed = true; } }

    void nativeReturned(boolean normal) { if (!normal) { rejected(); } }

    boolean readyToReplace() {
        return terminal == null && kind == Kind.NBT && ioReturned && completeBody() && !failed;
    }

    void replaced(boolean actualResult) {
        if (kind != Kind.NBT || terminal != null) { return; }
        if (actualResult && !readyToReplace()) { failed = true; }
        replace = merge(replace, actualResult);
        failed |= !actualResult;
    }

    Result settle() {
        if (terminal == null) {
            boolean success = !failed && completeBody()
                    && (kind != Kind.NBT || (ioReturned && replace == SUCCEEDED));
            terminal = new Result(encode, write, close, replace, success);
        }
        return terminal;
    }

    private boolean completeBody() {
        return encode == SUCCEEDED && write == SUCCEEDED && close == SUCCEEDED;
    }

    private static Observation merge(Observation prior, boolean normal) {
        return prior == FAILED || !normal ? FAILED : SUCCEEDED;
    }
}
