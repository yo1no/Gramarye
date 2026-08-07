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

    /** Inclusive encoded-byte ceiling for one physical Store revision entry. */
    public static final int MAX_STORE_REVISION_ENTRY_ENCODED_BYTES = 1_114_112;

    /** Inclusive encoded-byte ceiling for one physical retained skill history. */
    public static final int MAX_SKILL_HISTORY_ENCODED_BYTES = 8_388_608;

    /** Inclusive encoded-byte ceiling for the current uncompressed skill Store blob. */
    public static final int MAX_SKILL_STORE_ENCODED_BYTES = 67_108_864;

    /** Inclusive raw-byte ceiling for the opaque pending Attachment-update journal blob. */
    public static final int MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES = 1_048_576;

    /** Technical ceiling for raw pending Attachment-update entries before validation. */
    public static final int MAX_PENDING_ATTACHMENT_UPDATES = 4_096;

    /** Inclusive encoded-byte ceiling for the unnamed SavedData inner carrier Compound. */
    public static final int MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES = 69_206_016;

    /** Inclusive byte ceiling for the complete compressed primary skill SavedData file. */
    public static final int MAX_SKILL_SAVED_DATA_FILE_BYTES = 73_400_320;

    /** Technical ceiling for persisted Draft routes in one player skill Attachment. */
    public static final int MAX_PLAYER_DRAFTS = 32;

    /** Technical ceiling for persisted latest-reference routes in one player skill Attachment. */
    public static final int MAX_PLAYER_LATEST_STATES = 256;

    /** Technical ceiling for persisted equipped references in one player skill Attachment. */
    public static final int MAX_PLAYER_EQUIPPED_REFERENCES = 64;

    /** Inclusive raw-payload ceiling for one encoded player Draft entry. */
    public static final int MAX_PLAYER_DRAFT_ENTRY_ENCODED_BYTES = 1_114_112;

    /** Inclusive writeAnyTag byte ceiling for the complete player skill Attachment value. */
    public static final int MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES = 16_777_216;

    /** Maximum immediate entries admitted from the playerdata directory by one P4-E audit. */
    public static final int MAX_PLAYERDATA_DIRECTORY_ENTRIES = 4_096;

    /** Maximum selected canonical player records admitted by one P4-E audit. */
    public static final int MAX_PLAYERDATA_RELEVANT_RECORDS = 2_048;

    /** Inclusive compressed-byte ceiling for one selected playerdata file. */
    public static final int MAX_PLAYERDATA_COMPRESSED_BYTES_PER_FILE = 33_559_514;

    /** Inclusive decompressed-byte ceiling for one selected playerdata file. */
    public static final int MAX_PLAYERDATA_DECOMPRESSED_BYTES_PER_FILE = 268_435_456;

    /** Inclusive container-depth ceiling for one selected playerdata file. */
    public static final int MAX_PLAYERDATA_CONTAINER_DEPTH_PER_FILE = 512;

    /** Inclusive Compound-container ceiling for one selected playerdata file. */
    public static final int MAX_PLAYERDATA_COMPOUND_CONTAINERS_PER_FILE = 1_024;

    /** Inclusive Compound-field-entry ceiling for one selected playerdata file. */
    public static final int MAX_PLAYERDATA_COMPOUND_FIELD_ENTRIES_PER_FILE = 65_537;

    /** Inclusive List payload-element ceiling for one selected playerdata file. */
    public static final int MAX_PLAYERDATA_LIST_ELEMENTS_PER_FILE = 65_536;

    /** Inclusive byte-array element ceiling for one selected playerdata file. */
    public static final int MAX_PLAYERDATA_BYTE_ARRAY_ELEMENTS_PER_FILE = 268_435_384;

    /** Inclusive int-array element ceiling for one selected playerdata file. */
    public static final int MAX_PLAYERDATA_INT_ARRAY_ELEMENTS_PER_FILE = 65_536;

    /** Inclusive long-array element ceiling for one selected playerdata file. */
    public static final int MAX_PLAYERDATA_LONG_ARRAY_ELEMENTS_PER_FILE = 65_536;

    /** Inclusive modified-UTF payload-byte ceiling for one selected playerdata file. */
    public static final int MAX_PLAYERDATA_MODIFIED_UTF8_BYTES_PER_FILE = 67_107_692;

    /** Inclusive scalar-tag ceiling for one selected playerdata file. */
    public static final int MAX_PLAYERDATA_SCALAR_TAGS_PER_FILE = 65_537;

    /** Inclusive compressed-byte ceiling across selected files in one P4-E audit. */
    public static final int MAX_PLAYERDATA_COMPRESSED_BYTES_TOTAL = 268_440_533;

    /** Inclusive decompressed-byte ceiling across selected files in one P4-E audit. */
    public static final int MAX_PLAYERDATA_DECOMPRESSED_BYTES_TOTAL = 536_870_912;

    /** Inclusive Compound-container ceiling across selected files in one P4-E audit. */
    public static final int MAX_PLAYERDATA_COMPOUND_CONTAINERS_TOTAL = 131_072;

    /** Inclusive Compound-field-entry ceiling across selected files in one P4-E audit. */
    public static final int MAX_PLAYERDATA_COMPOUND_FIELD_ENTRIES_TOTAL = 524_288;

    /** Inclusive List payload-element ceiling across selected files in one P4-E audit. */
    public static final int MAX_PLAYERDATA_LIST_ELEMENTS_TOTAL = 131_072;

    /** Inclusive byte-array element ceiling across selected files in one P4-E audit. */
    public static final int MAX_PLAYERDATA_BYTE_ARRAY_ELEMENTS_TOTAL = 456_524_705;

    /** Inclusive int-array element ceiling across selected files in one P4-E audit. */
    public static final int MAX_PLAYERDATA_INT_ARRAY_ELEMENTS_TOTAL = 131_072;

    /** Inclusive long-array element ceiling across selected files in one P4-E audit. */
    public static final int MAX_PLAYERDATA_LONG_ARRAY_ELEMENTS_TOTAL = 131_072;

    /** Inclusive modified-UTF payload-byte ceiling across selected files in one P4-E audit. */
    public static final int MAX_PLAYERDATA_MODIFIED_UTF8_BYTES_TOTAL = 75_497_472;

    /** Inclusive scalar-tag ceiling across selected files in one P4-E audit. */
    public static final int MAX_PLAYERDATA_SCALAR_TAGS_TOTAL = 458_752;

    /** Maximum P4-C Attachment admission invocations accepted by one P4-E audit. */
    public static final int MAX_PLAYERDATA_ATTACHMENT_ADMISSIONS = 1_024;

    /**
     * Product-selected minimum effective HotSpot {@code MaxHeapSize} for a P4-E root audit.
     * Runtime-reported usable heap and memory-pool maxima are diagnostics, not this authority
     * coordinate.
     */
    public static final long MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES = 1_610_612_736L;

    private MagicSafetyCeilings() {
    }

    private static int requirePositive(String name, int value) {
        if (value <= 0) {
            throw new ExceptionInInitializerError(name + " must be positive");
        }
        return value;
    }
}
