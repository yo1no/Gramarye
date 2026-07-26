package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/** The sole live SavedData adapter for the Overworld skill Store cache entry. */
final class GramaryeSkillSavedData extends SavedData {
    private SkillSavedDataState state;

    private GramaryeSkillSavedData(SkillSavedDataState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    static GramaryeSkillSavedData ready(SkillSavedDataReadyCandidate candidate) {
        return new GramaryeSkillSavedData(SkillSavedDataState.Ready.fromCandidate(candidate));
    }

    static GramaryeSkillSavedData quarantined(SkillSavedDataPrimaryFailure failure) {
        return new GramaryeSkillSavedData(new SkillSavedDataState.Quarantined(failure));
    }

    SkillSubsystemResult<Optional<SkillDocument>> find(SkillReference reference) {
        Objects.requireNonNull(reference, "reference");
        var current = state;
        return current instanceof SkillSavedDataState.Ready ready
                ? available(ready.store().find(reference))
                : unavailable(current);
    }

    SkillSubsystemResult<Optional<SkillReference>> latestReference(SkillId skillId) {
        Objects.requireNonNull(skillId, "skillId");
        var current = state;
        return current instanceof SkillSavedDataState.Ready ready
                ? available(ready.store().latestReference(skillId))
                : unavailable(current);
    }

    SkillSubsystemResult<Optional<SkillOwnerId>> ownerOf(SkillId skillId) {
        Objects.requireNonNull(skillId, "skillId");
        var current = state;
        return current instanceof SkillSavedDataState.Ready ready
                ? available(ready.store().ownerOf(skillId))
                : unavailable(current);
    }

    SkillSubsystemResult<Integer> committedSkillCount(SkillOwnerId owner) {
        Objects.requireNonNull(owner, "owner");
        var current = state;
        return current instanceof SkillSavedDataState.Ready ready
                ? available(ready.store().committedSkillCount(owner))
                : unavailable(current);
    }

    SkillSubsystemResult<Optional<SkillRevisionPin>> pin(SkillReference reference) {
        Objects.requireNonNull(reference, "reference");
        var current = state;
        return current instanceof SkillSavedDataState.Ready ready
                ? available(ready.store().pin(reference))
                : unavailable(current);
    }

    SkillSubsystemResult<SkillReclaimResult> reclaim(SkillRetentionRootSnapshot roots) {
        return reclaim(roots, SkillStoreCarrierBuilder::filterAfterReclaim);
    }

    SkillSubsystemResult<SkillReclaimResult> reclaim(
            SkillRetentionRootSnapshot roots,
            ReclaimCarrierFilter filter) {
        Objects.requireNonNull(roots, "roots");
        Objects.requireNonNull(filter, "filter");
        var current = state;
        if (!(current instanceof SkillSavedDataState.Ready ready)) {
            return unavailable(current);
        }

        var result = ready.store().reclaim(roots);
        if (!(result instanceof SkillReclaimResult.Completed completed)
                || completed.report().revisionsReclaimed() == 0) {
            return available(result);
        }

        try {
            var filtered = filter.apply(ready.storeCarrier(), ready.store().snapshot());
            var replacementInner = SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                    filtered,
                    ready.innerCarrier().pending(),
                    Math.addExact(
                            SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                            Math.addExact(
                                    filtered.storeByteCount(),
                                    ready.innerCarrier().pending().byteCount())));
            var replacement = ready.afterReclaim(replacementInner);
            state = replacement;
            setDirty();
            return available(result);
        } catch (RuntimeException exception) {
            state = new SkillSavedDataState.Unavailable(
                    SkillSavedDataRuntimeFailure.from(exception));
            setDirty(false);
            return unavailable(state);
        }
    }

    boolean rewriteRequired() {
        var current = state;
        return current instanceof SkillSavedDataState.Ready ready
                && ready.rewriteRequired();
    }

    SkillSavedDataState state() {
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag output, HolderLookup.Provider provider) {
        var current = state;
        if (!(current instanceof SkillSavedDataState.Ready ready)) {
            throw new IllegalStateException("skill SavedData is in a non-saving state");
        }
        return ready.innerCarrier().createDataTag();
    }

    private static <T> SkillSubsystemResult.Available<T> available(T value) {
        return new SkillSubsystemResult.Available<>(value);
    }

    private static <T> SkillSubsystemResult.Unavailable<T> unavailable(
            SkillSavedDataState state) {
        return new SkillSubsystemResult.Unavailable<>(state.unavailableReason());
    }

    @FunctionalInterface
    interface ReclaimCarrierFilter {
        EncodedSkillStoreCarrier apply(
                EncodedSkillStoreCarrier base,
                SkillDefinitionStoreSnapshot snapshot);
    }
}

/** Server-thread-confined live state; only Ready retains Store/carrier truth. */
sealed interface SkillSavedDataState
        permits SkillSavedDataState.Ready,
                SkillSavedDataState.Quarantined,
                SkillSavedDataState.Unavailable {
    SkillSubsystemUnavailableReason unavailableReason();

    final class Ready implements SkillSavedDataState {
        private final SkillDefinitionStore store;
        private final SkillSavedDataInnerCarrier innerCarrier;
        private final boolean rewriteRequired;

        private Ready(
                SkillDefinitionStore store,
                SkillSavedDataInnerCarrier innerCarrier,
                boolean rewriteRequired) {
            this.store = Objects.requireNonNull(store, "store");
            this.innerCarrier = Objects.requireNonNull(innerCarrier, "innerCarrier");
            this.rewriteRequired = rewriteRequired;
        }

        static Ready fromCandidate(SkillSavedDataReadyCandidate candidate) {
            Objects.requireNonNull(candidate, "candidate");
            return new Ready(
                    candidate.store(), candidate.carrier(), candidate.rewriteRequired());
        }

        Ready afterReclaim(SkillSavedDataInnerCarrier replacementInner) {
            return new Ready(store, replacementInner, rewriteRequired);
        }

        SkillDefinitionStore store() {
            return store;
        }

        SkillSavedDataInnerCarrier innerCarrier() {
            return innerCarrier;
        }

        EncodedSkillStoreCarrier storeCarrier() {
            return innerCarrier.storeCarrier();
        }

        boolean rewriteRequired() {
            return rewriteRequired;
        }

        @Override
        public SkillSubsystemUnavailableReason unavailableReason() {
            throw new IllegalStateException("Ready state is available");
        }

        @Override
        public String toString() {
            return "Ready[rewriteRequired=" + rewriteRequired
                    + ", carrierByteCount=" + innerCarrier.encodedByteCount() + "]";
        }
    }

    record Quarantined(SkillSavedDataPrimaryFailure failure)
            implements SkillSavedDataState {
        public Quarantined {
            Objects.requireNonNull(failure, "failure");
        }

        @Override
        public SkillSubsystemUnavailableReason unavailableReason() {
            return new SkillSubsystemUnavailableReason(quarantinedCode(failure));
        }

        private static SkillSubsystemUnavailableReason.Code quarantinedCode(
                SkillSavedDataPrimaryFailure failure) {
            return switch (failure) {
                case SkillSavedDataPrimaryFailure.OuterSavedDataUnreadable ignored ->
                        SkillSubsystemUnavailableReason.Code.OUTER_SAVED_DATA_UNREADABLE;
                case SkillSavedDataPrimaryFailure.SavedDataFileCapacityExceeded ignored ->
                        SkillSubsystemUnavailableReason.Code.SAVED_DATA_FILE_CAPACITY_EXCEEDED;
                case SkillSavedDataPrimaryFailure.UnsupportedPrimaryFileType ignored ->
                        SkillSubsystemUnavailableReason.Code.UNSUPPORTED_PRIMARY_FILE_TYPE;
                case SkillSavedDataPrimaryFailure.PrimaryFileIdentityUnavailable ignored ->
                        SkillSubsystemUnavailableReason.Code.PRIMARY_FILE_IDENTITY_UNAVAILABLE;
                case SkillSavedDataPrimaryFailure.PrimaryFileRaceDetected ignored ->
                        SkillSubsystemUnavailableReason.Code.PRIMARY_FILE_RACE_DETECTED;
                case SkillSavedDataPrimaryFailure.MalformedGzip ignored ->
                        SkillSubsystemUnavailableReason.Code.MALFORMED_GZIP;
                case SkillSavedDataPrimaryFailure.MultipleGzipMembers ignored ->
                        SkillSubsystemUnavailableReason.Code.MULTIPLE_GZIP_MEMBERS;
                case SkillSavedDataPrimaryFailure.CompressedTrailingData ignored ->
                        SkillSubsystemUnavailableReason.Code.COMPRESSED_TRAILING_DATA;
                case SkillSavedDataPrimaryFailure.DecompressedCarrierFailure ignored ->
                        SkillSubsystemUnavailableReason.Code.DECOMPRESSED_CARRIER_FAILURE;
            };
        }
    }

    record Unavailable(SkillSavedDataRuntimeFailure failure)
            implements SkillSavedDataState {
        public Unavailable {
            Objects.requireNonNull(failure, "failure");
        }

        @Override
        public SkillSubsystemUnavailableReason unavailableReason() {
            return new SkillSubsystemUnavailableReason(
                    SkillSubsystemUnavailableReason.Code.RUNTIME_CARRIER_INVARIANT);
        }
    }
}

/** Bounded runtime failure retained after a post-reclaim carrier invariant breaks. */
record SkillSavedDataRuntimeFailure(Code code) {
    SkillSavedDataRuntimeFailure {
        Objects.requireNonNull(code, "code");
    }

    static SkillSavedDataRuntimeFailure from(RuntimeException exception) {
        Objects.requireNonNull(exception, "exception");
        return new SkillSavedDataRuntimeFailure(Code.RECLAIM_CARRIER_INVARIANT);
    }

    enum Code {
        RECLAIM_CARRIER_INVARIANT
    }
}
