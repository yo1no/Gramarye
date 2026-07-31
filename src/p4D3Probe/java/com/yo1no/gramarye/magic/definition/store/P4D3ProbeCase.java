package com.yo1no.gramarye.magic.definition.store;

/** Isolated disk-world identity for the P4-D3 paired restart matrix. */
public enum P4D3ProbeCase {
    D("crash-d"),
    E("crash-e"),
    F("crash-f"),
    G("crash-g"),
    H("crash-h"),
    I("crash-i"),
    J1("crash-j1"),
    COMBINED("combined");

    private final String token;

    P4D3ProbeCase(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public static P4D3ProbeCase fromToken(String token) {
        for (var value : values()) {
            if (value.token.equals(token)) {
                return value;
            }
        }
        throw new IllegalArgumentException("unknown P4-D3 case token");
    }
}
