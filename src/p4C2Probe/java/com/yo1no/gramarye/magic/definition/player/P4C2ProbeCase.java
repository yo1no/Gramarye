package com.yo1no.gramarye.magic.definition.player;

import java.util.UUID;

/** Three isolated fixed-heap player Attachment worlds. */
enum P4C2ProbeCase {
    READY("ready", "ready", "c2b00000-0000-4000-8000-000000000001"),
    PRESERVED_RAW(
            "preserved-raw",
            "preserved_raw",
            "c2b00000-0000-4000-8000-000000000002"),
    OVERSIZE(
            "oversize",
            "oversize_marker",
            "c2b00000-0000-4000-8000-000000000003");

    private final String token;
    private final String stateToken;
    private final UUID playerId;

    P4C2ProbeCase(String token, String stateToken, String playerId) {
        this.token = token;
        this.stateToken = stateToken;
        this.playerId = UUID.fromString(playerId);
    }

    String token() {
        return token;
    }

    String stateToken() {
        return stateToken;
    }

    UUID playerId() {
        return playerId;
    }

    static P4C2ProbeCase fromToken(String token) {
        for (var value : values()) {
            if (value.token.equals(token)) {
                return value;
            }
        }
        throw new IllegalArgumentException("unknown P4-C2 case");
    }
}
