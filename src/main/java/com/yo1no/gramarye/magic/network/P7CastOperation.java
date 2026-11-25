package com.yo1no.gramarye.magic.network;

import java.util.Objects;

/** Closed decoded operations; neither variant is a grant of gameplay authority. */
sealed interface P7CastOperation {
    long sequence();

    record Legacy(CastIntent intent) implements P7CastOperation {
        public Legacy { Objects.requireNonNull(intent, "intent"); }
        @Override public long sequence() { return intent.sequence(); }
    }

    record NormalSpell(long sequence) implements P7CastOperation { }
}
