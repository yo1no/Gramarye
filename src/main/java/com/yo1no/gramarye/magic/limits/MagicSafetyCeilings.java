package com.yo1no.gramarye.magic.limits;

/**
 * Non-configurable technical safety ceilings for bounded magic data.
 *
 * <p>These values are resource-safety limits, not final gameplay balance.</p>
 */
public final class MagicSafetyCeilings {
    /** Technical ceiling that bounds definition graph decode and traversal work. */
    public static final int MAX_NODES = 256;

    /** Technical ceiling that bounds any single persisted or diagnostic string. */
    public static final int MAX_STRING_LENGTH = 1_024;

    /** Technical ceiling that bounds one retained raw definition payload to 256 KiB. */
    public static final int MAX_RAW_PAYLOAD_BYTES = 256 * 1_024;

    /** Technical ceiling that bounds per-effect runtime tag collection growth. */
    public static final int MAX_RUNTIME_TAGS = 64;

    /** Technical ceiling that bounds per-lineage visited-target collection growth. */
    public static final int MAX_VISITED_TARGETS = 128;

    /** Technical ceiling for a future fixed-point presentation intensity value. */
    public static final int MAX_APPEARANCE_INTENSITY = 10_000;

    private MagicSafetyCeilings() {
    }
}
