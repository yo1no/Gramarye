package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/** Defensive, transient outer-carrier tree plus its non-visible typed blob table. */
final class TokenizedSavedDataCarrierSnapshot {
    private final CompoundTag tokenizedTree;
    private final OpaqueSavedDataBlobTable blobTable;

    TokenizedSavedDataCarrierSnapshot(
            CompoundTag tokenizedTree,
            OpaqueSavedDataBlobTable blobTable) {
        this.tokenizedTree = Objects.requireNonNull(tokenizedTree, "tokenizedTree").copy();
        this.blobTable = Objects.requireNonNull(blobTable, "blobTable");
    }

    CompoundTag copyTokenizedTree() {
        return tokenizedTree.copy();
    }

    OpaqueSavedDataBlobTable blobTable() {
        return blobTable;
    }

    @Override
    public String toString() {
        return "TokenizedSavedDataCarrierSnapshot[treePresent=true, blobCount=2]";
    }
}
