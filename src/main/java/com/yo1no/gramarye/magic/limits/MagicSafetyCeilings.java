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

    /**
     * Technical ceiling for one retained unparsed appearance subtree. This is intentionally the
     * same raw-tree byte boundary, not an independent gameplay or persistence policy.
     */
    public static final int MAX_UNPARSED_APPEARANCE_BYTES = MAX_RAW_PAYLOAD_BYTES;

    /** Technical ceiling that bounds per-effect runtime tag collection growth. */
    public static final int MAX_RUNTIME_TAGS = 64;

    /** Technical ceiling that bounds per-lineage visited-target collection growth. */
    public static final int MAX_VISITED_TARGETS = 128;

    /** Technical ceiling for presentation intensity in milli-units (1000 = 1.0). */
    public static final int MAX_APPEARANCE_INTENSITY = 10_000;

    /** Technical ceiling for a retained unparsed appearance subtree, with its root at depth 1. */
    public static final int MAX_UNPARSED_APPEARANCE_DEPTH = 32;

    /** Technical ceiling for values retained in one unparsed appearance subtree. */
    public static final int MAX_UNPARSED_APPEARANCE_NODES = 1_024;

    /** Provisional server policy default for unparsed appearance subtree depth. */
    public static final int DEFAULT_UNPARSED_APPEARANCE_DEPTH = 16;

    /** Provisional server policy default for unparsed appearance subtree values. */
    public static final int DEFAULT_UNPARSED_APPEARANCE_NODES = 256;

    /** Technical ceiling for a parsed skill document or draft, with its root at depth 1. */
    public static final int MAX_SKILL_DOCUMENT_DEPTH = 64;

    /** Provisional server policy default for parsed skill document or draft depth. */
    public static final int DEFAULT_SKILL_DOCUMENT_DEPTH = 32;

    /** Technical raw-I/O ceiling for a whole skill document or draft (1 MiB). */
    public static final int MAX_SKILL_DOCUMENT_BYTES = 1_024 * 1_024;

    /** Provisional raw-I/O policy default for a whole skill document or draft (256 KiB). */
    public static final int DEFAULT_SKILL_DOCUMENT_BYTES = 256 * 1_024;

    /** Technical ceiling for non-persistent normalization facts from one tolerant read. */
    public static final int MAX_READ_REPORT_FACTS = 1_024;

    /** Technical ceiling for non-persistent facts emitted by one definition pipeline run. */
    public static final int MAX_PIPELINE_FACTS = 1_024;

    /** Technical ceiling for segments in one machine-readable validation path. */
    public static final int MAX_VALIDATION_PATH_SEGMENTS = 64;

    /** Segments reserved for the future nodes[index].side.payload validation prefix. */
    public static final int VALIDATION_PATH_PREFIX_RESERVED_SEGMENTS = 8;

    /** Characters reserved for the future nodes[index].side.payload validation prefix. */
    public static final int VALIDATION_PATH_PREFIX_RESERVED_CHARACTERS = 64;

    /** Technical ceiling for payload-relative path segments emitted by an inspector. */
    public static final int MAX_INSPECTOR_RELATIVE_PATH_SEGMENTS = requirePositive(
            "MAX_INSPECTOR_RELATIVE_PATH_SEGMENTS",
            MAX_VALIDATION_PATH_SEGMENTS - VALIDATION_PATH_PREFIX_RESERVED_SEGMENTS);

    /** Technical ceiling for a rendered payload-relative path emitted by an inspector. */
    public static final int MAX_INSPECTOR_RELATIVE_PATH_RENDER_LENGTH = requirePositive(
            "MAX_INSPECTOR_RELATIVE_PATH_RENDER_LENGTH",
            MAX_STRING_LENGTH - VALIDATION_PATH_PREFIX_RESERVED_CHARACTERS);

    /** Technical ceiling for node references emitted by one Trigger or Action inspector side. */
    public static final int MAX_INSPECTED_REFERENCES_PER_SIDE = 1_024;

    /** Technical ceiling for retained issues in one non-persistent validation result. */
    public static final int MAX_VALIDATION_ISSUES = 1_024;

    /** Technical parsed-tree proxy ceiling for values in one skill document or draft. */
    public static final int MAX_SKILL_DOCUMENT_TREE_NODES = 65_536;

    /** Provisional server policy default for parsed values in one skill document or draft. */
    public static final int DEFAULT_SKILL_DOCUMENT_TREE_NODES = 16_384;

    /** Technical ceiling for active committed skill histories owned by one principal. */
    public static final int MAX_COMMITTED_SKILLS_PER_OWNER = 256;

    /** Technical ceiling for active committed skill histories in one Store. */
    public static final int MAX_COMMITTED_SKILLS_GLOBAL = 4_096;

    /** Technical ceiling for retained revisions in one active skill history. */
    public static final int MAX_RETAINED_REVISIONS_PER_SKILL = 128;

    /** Technical ceiling for retained revisions across one Store. */
    public static final int MAX_RETAINED_REVISIONS_GLOBAL = 32_768;

    /** Technical ceiling for external retention roots accepted by one reclaim operation. */
    public static final int MAX_RETENTION_ROOTS_PER_RECLAIM = 65_536;

    private MagicSafetyCeilings() {
    }

    private static int requirePositive(String name, int value) {
        if (value <= 0) {
            throw new ExceptionInInitializerError(name + " must be positive");
        }
        return value;
    }
}
