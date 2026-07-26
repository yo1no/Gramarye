package com.yo1no.gramarye.magic.definition.store;

import java.util.Arrays;

/** Fixed P4-B2-B fixture cases and the exact single-test run modes. */
enum P4B2ProbeCase {
    FULL("full"),
    HOSTILE_FNAME("hostile-fname"),
    MALFORMED_GZIP("malformed-gzip"),
    COMPRESSED_TRAILING("compressed-trailing"),
    SECOND_MEMBER("second-member");

    private final String token;

    P4B2ProbeCase(String token) {
        this.token = token;
    }

    String token() {
        return token;
    }

    boolean fullSize() {
        return this == FULL || this == HOSTILE_FNAME;
    }

    static P4B2ProbeCase fromToken(String token) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.token.equals(token))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown P4-B2 fixture case"));
    }
}

enum P4B2RunMode {
    FULL_FIRST("full-first-load-save", P4B2ProbeCase.FULL, false),
    FULL_RESTART("full-restart", P4B2ProbeCase.FULL, true),
    HOSTILE_FNAME_FIRST("hostile-fname-first", P4B2ProbeCase.HOSTILE_FNAME, false),
    HOSTILE_FNAME_RESTART("hostile-fname-restart", P4B2ProbeCase.HOSTILE_FNAME, true),
    MALFORMED_FIRST("malformed-first", P4B2ProbeCase.MALFORMED_GZIP, false),
    MALFORMED_RESTART("malformed-restart", P4B2ProbeCase.MALFORMED_GZIP, true),
    TRAILING_FIRST("trailing-first", P4B2ProbeCase.COMPRESSED_TRAILING, false),
    TRAILING_RESTART("trailing-restart", P4B2ProbeCase.COMPRESSED_TRAILING, true),
    SECOND_MEMBER_FIRST("second-member-first", P4B2ProbeCase.SECOND_MEMBER, false),
    SECOND_MEMBER_RESTART("second-member-restart", P4B2ProbeCase.SECOND_MEMBER, true);

    static final String SYSTEM_PROPERTY = "gramarye.p4b2.runMode";

    private final String token;
    private final P4B2ProbeCase fixtureCase;
    private final boolean restart;

    P4B2RunMode(String token, P4B2ProbeCase fixtureCase, boolean restart) {
        this.token = token;
        this.fixtureCase = fixtureCase;
        this.restart = restart;
    }

    String token() {
        return token;
    }

    P4B2ProbeCase fixtureCase() {
        return fixtureCase;
    }

    boolean restart() {
        return restart;
    }

    boolean fullSize() {
        return fixtureCase.fullSize();
    }

    P4B2RunMode restartMode() {
        return switch (this) {
            case FULL_FIRST -> FULL_RESTART;
            case HOSTILE_FNAME_FIRST -> HOSTILE_FNAME_RESTART;
            case MALFORMED_FIRST -> MALFORMED_RESTART;
            case TRAILING_FIRST -> TRAILING_RESTART;
            case SECOND_MEMBER_FIRST -> SECOND_MEMBER_RESTART;
            case FULL_RESTART, HOSTILE_FNAME_RESTART, MALFORMED_RESTART, TRAILING_RESTART,
                    SECOND_MEMBER_RESTART -> this;
        };
    }

    static P4B2RunMode fromToken(String token) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.token.equals(token))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown P4-B2 run mode"));
    }
}
