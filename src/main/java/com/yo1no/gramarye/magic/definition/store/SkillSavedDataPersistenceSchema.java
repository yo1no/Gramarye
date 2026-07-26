package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;

/** Package-internal SavedData V0 field names and derived framing limits. */
final class SkillSavedDataPersistenceSchema {
    static final int CURRENT_SCHEMA_VERSION = 0;

    static final String DATA_FIELD = "data";
    static final String DATA_VERSION_FIELD = "DataVersion";
    static final String SCHEMA_VERSION_FIELD = "saved_data_schema_version";
    static final String STORE_BLOB_FIELD = "store_blob";
    static final String PENDING_UPDATES_BLOB_FIELD = "pending_attachment_updates_blob";

    /** Unnamed root type/name plus the three fixed V0 field headers and Compound end marker. */
    static final int INNER_CARRIER_V0_FRAMING_BYTES = 91;
    static final int WHOLE_ROOT_V0_FRAMING_OVERHEAD = 26;
    static final int MAX_WHOLE_DECOMPRESSED_ROOT_BYTES = Math.addExact(
            MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES,
            WHOLE_ROOT_V0_FRAMING_OVERHEAD);
    static final long FINITE_WHOLE_ROOT_NBT_QUOTA = 69_206_405L;

    private SkillSavedDataPersistenceSchema() {
    }
}
