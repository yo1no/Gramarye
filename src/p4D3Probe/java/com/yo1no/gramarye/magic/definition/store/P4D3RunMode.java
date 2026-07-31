package com.yo1no.gramarye.magic.definition.store;

/** Exact external task/server phase selector for one P4-D3 world. */
public enum P4D3RunMode {
    CRASH_D_FIRST(P4D3ProbeCase.D, false),
    CRASH_D_RESTART(P4D3ProbeCase.D, true),
    CRASH_E_FIRST(P4D3ProbeCase.E, false),
    CRASH_E_RESTART(P4D3ProbeCase.E, true),
    CRASH_F_FIRST(P4D3ProbeCase.F, false),
    CRASH_F_RESTART(P4D3ProbeCase.F, true),
    CRASH_G_FIRST(P4D3ProbeCase.G, false),
    CRASH_G_RESTART(P4D3ProbeCase.G, true),
    CRASH_H_FIRST(P4D3ProbeCase.H, false),
    CRASH_H_RESTART(P4D3ProbeCase.H, true),
    CRASH_I_FIRST(P4D3ProbeCase.I, false),
    CRASH_I_RESTART(P4D3ProbeCase.I, true),
    CRASH_J1_FIRST(P4D3ProbeCase.J1, false),
    CRASH_J1_RESTART(P4D3ProbeCase.J1, true),
    COMBINED_FIRST(P4D3ProbeCase.COMBINED, false),
    COMBINED_RESTART(P4D3ProbeCase.COMBINED, true);

    public static final String SYSTEM_PROPERTY = "gramarye.p4d3.runMode";

    private final P4D3ProbeCase probeCase;
    private final boolean restart;
    private final String token;

    P4D3RunMode(P4D3ProbeCase probeCase, boolean restart) {
        this.probeCase = probeCase;
        this.restart = restart;
        token = probeCase.token() + (restart ? "-restart" : "-first");
    }

    public P4D3ProbeCase probeCase() {
        return probeCase;
    }

    public boolean restart() {
        return restart;
    }

    public String token() {
        return token;
    }

    public String completionPhase() {
        return token + "-complete";
    }

    public static P4D3RunMode fromSystemProperty() {
        return fromToken(System.getProperty(SYSTEM_PROPERTY, ""));
    }

    public static P4D3RunMode fromToken(String token) {
        for (var value : values()) {
            if (value.token.equals(token)) {
                return value;
            }
        }
        throw new IllegalArgumentException("unknown P4-D3 run-mode token");
    }
}
