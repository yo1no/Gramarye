package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Same-tick, one-iterator root handoff reserved for the future E3 composition. */
final class P4E1CompleteRootHandoff implements Iterable<SkillReference>, AutoCloseable {
    private LeaseAuthority authority;
    private Cursor cursor;
    private boolean iteratorIssued;
    private boolean closed;
    private boolean forceInvalidated;
    private boolean callChainLost;

    P4E1CompleteRootHandoff(LeaseAuthority authority) {
        this.authority = Objects.requireNonNull(authority, "authority");
    }

    @Override
    public Iterator<SkillReference> iterator() {
        requireCurrent();
        if (iteratorIssued) {
            throw new IllegalStateException("P4E1_COMPLETE_HANDOFF_ITERATOR_ALREADY_ISSUED");
        }
        iteratorIssued = true;
        cursor = new Cursor();
        return cursor;
    }

    @Override
    public void close() {
        if (forceInvalidated) {
            throw new IllegalStateException("P4E1_COMPLETE_HANDOFF_FORCE_INVALIDATED");
        }
        if (callChainLost) {
            throw new IllegalStateException("P4E1_COMPLETE_HANDOFF_CALL_CHAIN_LOST");
        }
        if (closed) {
            return;
        }
        var currentAuthority = authority;
        if (!currentAuthority.isCurrent(this)) {
            callChainLost = true;
            clearCursor();
            throw new IllegalStateException("P4E1_COMPLETE_HANDOFF_CALL_CHAIN_LOST");
        }
        closed = true;
        clearLocalAuthority();
        currentAuthority.release(this);
    }

    void forceInvalidate(LeaseAuthority expectedAuthority) {
        Objects.requireNonNull(expectedAuthority, "expectedAuthority");
        if (forceInvalidated || authority != expectedAuthority) {
            throw new IllegalStateException("P4E1_COMPLETE_HANDOFF_INVALIDATION_MISMATCH");
        }
        forceInvalidated = true;
        closed = true;
        callChainLost = false;
        clearLocalAuthority();
    }

    private SkillReference nextReference() {
        requireCurrent();
        var current = cursor;
        if (current == null || current.nextIndex >= authority.size()) {
            throw new NoSuchElementException();
        }
        return authority.referenceAt(current.nextIndex++);
    }

    private boolean hasNextReference() {
        requireCurrent();
        return cursor != null && cursor.nextIndex < authority.size();
    }

    private void requireCurrent() {
        if (forceInvalidated || callChainLost || closed || authority == null
                || !authority.isCurrent(this)) {
            throw new IllegalStateException("P4E1_COMPLETE_HANDOFF_NOT_CURRENT");
        }
    }

    private void clearLocalAuthority() {
        clearCursor();
        authority = null;
    }

    private void clearCursor() {
        if (cursor != null) {
            cursor.nextIndex = 0;
            cursor = null;
        }
    }

    interface LeaseAuthority {
        boolean isCurrent(P4E1CompleteRootHandoff handoff);

        int size();

        SkillReference referenceAt(int index);

        void release(P4E1CompleteRootHandoff handoff);
    }

    private final class Cursor implements Iterator<SkillReference> {
        private int nextIndex;

        @Override
        public boolean hasNext() {
            return hasNextReference();
        }

        @Override
        public SkillReference next() {
            return nextReference();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
