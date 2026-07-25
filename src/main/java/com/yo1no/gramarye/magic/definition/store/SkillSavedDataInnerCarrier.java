package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/** Immutable current-schema inner SavedData carrier assembled from prevalidated framing. */
final class SkillSavedDataInnerCarrier {
    private final EncodedSkillStoreCarrier storeCarrier;
    private final OpaquePendingAttachmentUpdatesBlob pending;
    private final int encodedByteCount;

    private SkillSavedDataInnerCarrier(
            EncodedSkillStoreCarrier storeCarrier,
            OpaquePendingAttachmentUpdatesBlob pending,
            int encodedByteCount) {
        this.storeCarrier = Objects.requireNonNull(storeCarrier, "storeCarrier");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.encodedByteCount = encodedByteCount;
    }

    /** Binds preflight metadata to the exact current V0 Store and pending payload sizes. */
    static SkillSavedDataInnerCarrier fromPrevalidatedFraming(
            EncodedSkillStoreCarrier storeCarrier,
            OpaquePendingAttachmentUpdatesBlob pending,
            int encodedByteCount) {
        Objects.requireNonNull(storeCarrier, "storeCarrier");
        Objects.requireNonNull(pending, "pending");
        var expected = Math.addExact(
                SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                Math.addExact(storeCarrier.storeByteCount(), pending.byteCount()));
        if (encodedByteCount != expected
                || expected > MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "prevalidated SavedData inner-carrier byte count does not match V0 framing");
        }
        return new SkillSavedDataInnerCarrier(storeCarrier, pending, expected);
    }

    EncodedSkillStoreCarrier storeCarrier() {
        return storeCarrier;
    }

    OpaquePendingAttachmentUpdatesBlob pending() {
        return pending;
    }

    int encodedByteCount() {
        return encodedByteCount;
    }

    /** Creates a fresh fixed-shape data tag without encoding or consulting a live Store. */
    CompoundTag createDataTag() {
        var storeBytes = new byte[storeCarrier.storeByteCount()];
        storeCarrier.copyStoreBlobInto(storeBytes, 0);

        var data = new CompoundTag();
        data.putInt(
                SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD,
                SkillSavedDataPersistenceSchema.CURRENT_SCHEMA_VERSION);
        data.putByteArray(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD, storeBytes);
        data.putByteArray(
                SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD,
                pending.copyBytes());
        return data;
    }

    @Override
    public String toString() {
        return "SkillSavedDataInnerCarrier[encodedByteCount=" + encodedByteCount
                + ", storeByteCount=" + storeCarrier.storeByteCount()
                + ", pendingByteCount=" + pending.byteCount() + "]";
    }
}
