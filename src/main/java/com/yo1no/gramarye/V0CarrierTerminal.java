package com.yo1no.gramarye;

import java.util.Objects;

final class V0CarrierTerminal {
    private final V0TerminalReason reason;

    private V0CarrierTerminal(V0TerminalReason reason) {
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    static V0CarrierTerminal of(V0TerminalReason reason) {
        return new V0CarrierTerminal(reason);
    }

    V0TerminalReason reason() {
        return reason;
    }
}
