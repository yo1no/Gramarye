package com.yo1no.gramarye.magic.definition.store;

/** Unique current-version and physical-route vocabulary for the P4-A2 Store format. */
final class StorePersistenceSchema {
    static final int CURRENT_SCHEMA_VERSION = 0;
    static final String DOCUMENT_ENCODING = "family_tagged_subtrees_v0";

    private StorePersistenceSchema() {
    }
}
