package com.yo1no.gramarye.magic.definition.player;

/** Exactly six independent dedicated-server modes. */
enum P4C2RunMode {
    READY_FIRST("ready-first", P4C2ProbeCase.READY, false),
    READY_RESTART("ready-restart", P4C2ProbeCase.READY, true),
    PRESERVED_RAW_FIRST(
            "preserved-raw-first", P4C2ProbeCase.PRESERVED_RAW, false),
    PRESERVED_RAW_RESTART(
            "preserved-raw-restart", P4C2ProbeCase.PRESERVED_RAW, true),
    OVERSIZE_FIRST("oversize-first", P4C2ProbeCase.OVERSIZE, false),
    OVERSIZE_RESTART("oversize-restart", P4C2ProbeCase.OVERSIZE, true);

    static final String SYSTEM_PROPERTY = "gramarye.p4c2.runMode";

    private final String token;
    private final P4C2ProbeCase probeCase;
    private final boolean restart;

    P4C2RunMode(String token, P4C2ProbeCase probeCase, boolean restart) {
        this.token = token;
        this.probeCase = probeCase;
        this.restart = restart;
    }

    String token() {
        return token;
    }

    P4C2ProbeCase probeCase() {
        return probeCase;
    }

    boolean restart() {
        return restart;
    }

    P4C2RunMode restartMode() {
        return switch (this) {
            case READY_FIRST -> READY_RESTART;
            case PRESERVED_RAW_FIRST -> PRESERVED_RAW_RESTART;
            case OVERSIZE_FIRST -> OVERSIZE_RESTART;
            case READY_RESTART, PRESERVED_RAW_RESTART, OVERSIZE_RESTART ->
                    throw new IllegalStateException("mode is already restart");
        };
    }

    static P4C2RunMode fromToken(String token) {
        for (var value : values()) {
            if (value.token.equals(token)) {
                return value;
            }
        }
        throw new IllegalArgumentException("unknown P4-C2 run mode");
    }
}
