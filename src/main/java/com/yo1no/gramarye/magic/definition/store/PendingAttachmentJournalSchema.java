package com.yo1no.gramarye.magic.definition.store;

/** Exact current physical schema constants for the pending Attachment journal. */
final class PendingAttachmentJournalSchema {
    static final int CURRENT_SCHEMA_VERSION = 0;
    static final int MAX_ENTRIES =
            com.yo1no.gramarye.magic.limits.MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES;

    static final String VERSION = "journal_schema_version";
    static final String ENTRIES = "entries";
    static final String OWNER = "owner";
    static final String SKILL_ID = "skill_id";
    static final String EXPECTED_GENERATION = "expected_attachment_generation";
    static final String TARGET_GENERATION = "target_attachment_generation";
    static final String EXPECTED_POINTER = "expected_pointer";
    static final String TARGET_POINTER = "target_pointer";
    static final String REVISION = "revision";

    private PendingAttachmentJournalSchema() {
    }
}
