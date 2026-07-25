package com.yo1no.gramarye.magic.definition.store;

/** Logical current-carrier locations for the two B1-opaque payloads. */
enum OpaqueSavedDataBlobLocation {
    STORE_BLOB(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD, 0),
    PENDING_ATTACHMENT_UPDATES_BLOB(
            SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD, 1);

    private final String fieldName;
    private final int tokenId;

    OpaqueSavedDataBlobLocation(String fieldName, int tokenId) {
        this.fieldName = fieldName;
        this.tokenId = tokenId;
    }

    String fieldName() {
        return fieldName;
    }

    int tokenId() {
        return tokenId;
    }
}
