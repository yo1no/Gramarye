package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

/**
 * Idempotent handle for one active in-memory use of an exact committed skill revision.
 *
 * <p>A handle belongs to the Store that created it and follows that Store's server logic-thread
 * confinement contract. It is transient lifecycle state, not a persistent retention root or
 * cross-restart credential.</p>
 */
public final class SkillRevisionPin implements AutoCloseable {
    private final SkillDefinitionStore store;
    private final SkillReference reference;
    private boolean closed;

    SkillRevisionPin(SkillDefinitionStore store, SkillReference reference) {
        this.store = Objects.requireNonNull(store, "store");
        this.reference = Objects.requireNonNull(reference, "reference");
    }

    /** Returns the exact committed revision protected by this handle. */
    public SkillReference reference() {
        return reference;
    }

    /** Returns whether this handle has successfully released its pin. */
    public boolean isClosed() {
        return closed;
    }

    /** Releases this handle's one pin count; repeated calls are no-ops. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        store.releasePin(reference);
        closed = true;
    }
}
