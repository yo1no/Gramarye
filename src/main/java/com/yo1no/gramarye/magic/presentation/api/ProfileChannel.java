package com.yo1no.gramarye.magic.presentation.api;

import java.util.Optional;

/** Closed presentation channels and their stable wire codes. */
public enum ProfileChannel {
    SOUND(0),
    PARTICLE(1),
    TRAIL(2);

    private final int wireCode;

    ProfileChannel(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static Optional<ProfileChannel> fromWireCode(int code) {
        return switch (code) {
            case 0 -> Optional.of(SOUND);
            case 1 -> Optional.of(PARTICLE);
            case 2 -> Optional.of(TRAIL);
            default -> Optional.empty();
        };
    }
}
