package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;

/** Mints and validates the two deterministic outer-migration blob sentinels. */
final class OpaqueSavedDataBlobTokens {
    static final String TOKEN_FIELD = "__gramarye_opaque_saved_data_blob_token_v0";

    private OpaqueSavedDataBlobTokens() {
    }

    static TokenizedSavedDataCarrierSnapshot tokenize(
            int schemaVersion,
            ImmutableStoreBlob storeBlob,
            OpaquePendingAttachmentUpdatesBlob pending) {
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("schemaVersion must be non-negative");
        }
        var table = new OpaqueSavedDataBlobTable(storeBlob, pending);
        var tree = new CompoundTag();
        tree.putInt(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD, schemaVersion);
        for (var location : OpaqueSavedDataBlobLocation.values()) {
            tree.put(location.fieldName(), sentinel(location.tokenId()));
        }
        return new TokenizedSavedDataCarrierSnapshot(tree, table);
    }

    static ReinsertedSavedDataCarrier reinsertCurrent(
            CompoundTag migratedTree,
            OpaqueSavedDataBlobTable table,
            int currentSchemaVersion) throws OpaqueSavedDataTokenException {
        Objects.requireNonNull(migratedTree, "migratedTree");
        Objects.requireNonNull(table, "table");
        if (!migratedTree.getAllKeys().equals(Set.of(
                SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD,
                SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD,
                SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD))) {
            throw new OpaqueSavedDataTokenException(
                    OpaqueSavedDataTokenException.Code.UNEXPECTED_CURRENT_FIELD);
        }
        if (!(migratedTree.get(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD)
                        instanceof IntTag version)
                || version.getAsInt() != currentSchemaVersion) {
            throw new OpaqueSavedDataTokenException(
                    OpaqueSavedDataTokenException.Code.INVALID_CURRENT_SCHEMA);
        }
        for (var location : OpaqueSavedDataBlobLocation.values()) {
            requireTokenAt(migratedTree, location);
        }
        return new ReinsertedSavedDataCarrier(
                currentSchemaVersion, table.storeBlob(), table.pending());
    }

    static void requireAllTokensPresent(CompoundTag tree)
            throws OpaqueSavedDataTokenException {
        Objects.requireNonNull(tree, "tree");
        var counts = new int[OpaqueSavedDataBlobLocation.values().length];
        countSentinels(tree, counts);
        for (var location : OpaqueSavedDataBlobLocation.values()) {
            if (counts[location.tokenId()] != 1) {
                throw new OpaqueSavedDataTokenException(
                        counts[location.tokenId()] == 0
                                ? OpaqueSavedDataTokenException.Code.MISSING_TOKEN
                                : OpaqueSavedDataTokenException.Code.MALFORMED_TOKEN);
            }
        }
    }

    private static CompoundTag sentinel(int tokenId) {
        var token = new CompoundTag();
        token.putInt(TOKEN_FIELD, tokenId);
        return token;
    }

    private static void requireTokenAt(
            CompoundTag tree,
            OpaqueSavedDataBlobLocation location) throws OpaqueSavedDataTokenException {
        if (!(tree.get(location.fieldName()) instanceof CompoundTag token)
                || !token.getAllKeys().equals(Set.of(TOKEN_FIELD))
                || !(token.get(TOKEN_FIELD) instanceof IntTag id)) {
            throw new OpaqueSavedDataTokenException(
                    OpaqueSavedDataTokenException.Code.MALFORMED_TOKEN);
        }
        if (id.getAsInt() != location.tokenId()) {
            var known = id.getAsInt() >= 0
                    && id.getAsInt() < OpaqueSavedDataBlobLocation.values().length;
            throw new OpaqueSavedDataTokenException(
                    known
                            ? OpaqueSavedDataTokenException.Code.RELOCATED_TOKEN
                            : OpaqueSavedDataTokenException.Code.UNKNOWN_TOKEN);
        }
    }

    private static void countSentinels(CompoundTag tree, int[] counts)
            throws OpaqueSavedDataTokenException {
        for (var key : tree.getAllKeys()) {
            if (!(tree.get(key) instanceof CompoundTag compound)) {
                continue;
            }
            if (compound.get(TOKEN_FIELD) instanceof IntTag id) {
                if (!compound.getAllKeys().equals(Set.of(TOKEN_FIELD))) {
                    throw new OpaqueSavedDataTokenException(
                            OpaqueSavedDataTokenException.Code.MALFORMED_TOKEN);
                }
                if (id.getAsInt() < 0 || id.getAsInt() >= counts.length) {
                    throw new OpaqueSavedDataTokenException(
                            OpaqueSavedDataTokenException.Code.UNKNOWN_TOKEN);
                }
                counts[id.getAsInt()]++;
            }
        }
    }
}
