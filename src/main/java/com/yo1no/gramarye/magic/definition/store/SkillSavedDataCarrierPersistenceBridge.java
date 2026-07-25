package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;

/** Fail-closed P4-B1 composition from decompressed root through a matching Ready candidate. */
final class SkillSavedDataCarrierPersistenceBridge {
    private SkillSavedDataCarrierPersistenceBridge() {
    }

    static SkillSavedDataCarrierLoadResult loadDecompressed(
            InputStream decompressed,
            Optional<HolderLookup.Provider> provider) {
        return loadDecompressed(
                decompressed,
                provider,
                SkillDefinitionStorePersistenceBridge::loadStoreBlob,
                SkillStoreCarrierBuilder::rebuild);
    }

    static SkillSavedDataCarrierLoadResult loadDecompressed(
            InputStream decompressed,
            Optional<HolderLookup.Provider> provider,
            StoreLoader storeLoader,
            CarrierRebuilder carrierRebuilder) {
        Objects.requireNonNull(decompressed, "decompressed");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(storeLoader, "storeLoader");
        Objects.requireNonNull(carrierRebuilder, "carrierRebuilder");

        var emptyFacts = new PipelineFactReport(List.of(), false);
        var framed = SkillSavedDataNbtFraming.decodeWholeRoot(decompressed);
        if (framed.failureValue().isPresent()) {
            return failure(framed.failureValue().orElseThrow(), emptyFacts);
        }
        var parsed = framed.successValue().orElseThrow();
        var tokenized = OpaqueSavedDataBlobTokens.tokenize(
                parsed.schemaVersion(), parsed.storeBlob(), parsed.pending());
        var migration = SkillSavedDataCarrierMigrator.migrate(tokenized);
        if (migration instanceof SkillSavedDataCarrierMigrationResult.Failure failed) {
            var migrationFailure = failed.failure();
            if (migrationFailure.code()
                    == SkillSavedDataCarrierMigrationFailure.Code.FUTURE_SCHEMA_VERSION) {
                return failure(new SkillSavedDataCarrierFailure.UnsupportedSavedDataSchema(
                        migrationFailure.observedVersion().orElseThrow(),
                        SkillSavedDataPersistenceSchema.CURRENT_SCHEMA_VERSION),
                        failed.factReport());
            }
            if (migrationFailure.code()
                    == SkillSavedDataCarrierMigrationFailure.Code
                            .OPAQUE_TOKEN_INVARIANT_VIOLATION) {
                return failure(
                        SkillSavedDataCarrierFailure.OpaqueTokenInvariantViolation.INSTANCE,
                        failed.factReport());
            }
            return failure(
                    new SkillSavedDataCarrierFailure.SavedDataEnvelopeMigrationFailed(
                            migrationFailure),
                    failed.factReport());
        }
        var migrated = (SkillSavedDataCarrierMigrationResult.Success) migration;
        var facts = migrated.factReport();
        var source = migrated.carrier();

        StorePersistenceLoadResult storeLoad;
        try {
            storeLoad = storeLoader.load(source.storeBlob(), provider);
        } catch (RuntimeException exception) {
            return failure(SkillSavedDataCarrierFailure.InternalCodecException.from(
                    SkillSavedDataCarrierFailure.EnvelopeStage.STORE_LOAD, exception), facts);
        }
        if (storeLoad instanceof StorePersistenceLoadResult.Failure failed) {
            facts = facts.append(failed.factReport());
            return failure(
                    new SkillSavedDataCarrierFailure.StoreLoadFailed(failed.failure()), facts);
        }
        var loaded = (StorePersistenceLoadResult.Loaded) storeLoad;
        facts = facts.append(loaded.factReport());

        CarrierBuildResult rebuilt;
        try {
            rebuilt = carrierRebuilder.rebuild(loaded.store());
        } catch (RuntimeException exception) {
            return failure(SkillSavedDataCarrierFailure.InternalCodecException.from(
                    SkillSavedDataCarrierFailure.EnvelopeStage.CARRIER_REBUILD, exception), facts);
        }
        if (rebuilt instanceof CarrierBuildResult.Failure failed) {
            return failure(
                    new SkillSavedDataCarrierFailure.CarrierRebuildFailed(failed.failure()), facts);
        }
        var storeCarrier = ((CarrierBuildResult.Success) rebuilt).carrier();
        var rewriteRequired = migrated.migrated()
                || loaded.rewritePending()
                || !storeCarrier.matchesStoreBlob(source.storeBlob());
        try {
            var encodedByteCount = Math.addExact(
                    SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                    Math.addExact(storeCarrier.storeByteCount(), source.pending().byteCount()));
            var inner = SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                    storeCarrier, source.pending(), encodedByteCount);
            var candidate = SkillSavedDataReadyCandidate.afterCarrierRebuild(
                    loaded.store(), inner, facts, rewriteRequired);
            return new SkillSavedDataCarrierLoadResult.Ready(candidate);
        } catch (RuntimeException exception) {
            return failure(SkillSavedDataCarrierFailure.InternalCodecException.from(
                    SkillSavedDataCarrierFailure.EnvelopeStage.CARRIER_REBUILD, exception), facts);
        }
    }

    private static SkillSavedDataCarrierLoadResult.Failure failure(
            SkillSavedDataCarrierFailure failure,
            PipelineFactReport facts) {
        return new SkillSavedDataCarrierLoadResult.Failure(failure, facts);
    }

    @FunctionalInterface
    interface StoreLoader {
        StorePersistenceLoadResult load(
                ImmutableStoreBlob blob,
                Optional<HolderLookup.Provider> provider);
    }

    @FunctionalInterface
    interface CarrierRebuilder {
        CarrierBuildResult rebuild(SkillDefinitionStore store);
    }
}
