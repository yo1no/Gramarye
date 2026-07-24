package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.definition.migration.OpaqueSkillDocumentMigrationFacade;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** The sole Store-to-document persistence seam for current encode and always-migrating load. */
public final class SkillDocumentStorePersistenceFacade {
    private SkillDocumentStorePersistenceFacade() {
    }

    /**
     * Encodes one current-schema immutable document through the P4-A1 mixed-family bridge.
     * Migration, resolution and validation are intentionally not performed here.
     */
    public static EncodeResult encodeCurrent(SkillDocument document) {
        Objects.requireNonNull(document, "document");
        var encoded = SkillDocumentPersistenceBridge.encodeCurrent(document);
        if (encoded.successValue().isPresent()) {
            return new Encoded(EncodedSkillDocument.fromInternal(
                    encoded.successValue().orElseThrow()));
        }
        return new EncodeRejected(mapPersistenceFailure(
                encoded.failureValue().orElseThrow(), true));
    }

    /**
     * Loads one encoded document only after physical parsing, logical schema migration, opaque-token
     * verification and exact raw reinsertion. No public current-only load bypass exists.
     */
    public static LoadResult load(
            EncodedSkillDocument encoded,
            Optional<HolderLookup.Provider> provider) {
        return loadWithMigration(encoded, provider, OpaqueSkillDocumentMigrationFacade::migrate);
    }

    /** Package-private deterministic seam used to prove migration precedes current-shape decode. */
    static LoadResult loadWithMigration(
            EncodedSkillDocument encoded,
            Optional<HolderLookup.Provider> provider,
            TokenizedMigration migration) {
        return loadWithMigration(
                encoded,
                provider,
                migration,
                SkillDocumentPersistenceBridge::hydrateCurrentForInternalUse);
    }

    /** Package-private fixture seam proving failed migration cannot invoke current hydration. */
    static LoadResult loadWithMigration(
            EncodedSkillDocument encoded,
            Optional<HolderLookup.Provider> provider,
            TokenizedMigration migration,
            CurrentDocumentHydrator hydrator) {
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(migration, "migration");
        Objects.requireNonNull(hydrator, "hydrator");
        var noFacts = emptyFacts();
        var facts = noFacts;
        try {
            var physicalRoot = decodeCompound(encoded.copyInternal());
            if (physicalRoot.isEmpty()) {
                return rejected(FailureCode.MALFORMED_DOCUMENT, noFacts);
            }
            var built = LogicalSkillDocumentConformanceView.extractPhysical(
                    physicalRoot.orElseThrow());
            if (!(built instanceof LogicalSkillDocumentConformanceView.BuildResult.Success view)) {
                return rejected(FailureCode.DOCUMENT_DECODE_FAILED, noFacts);
            }
            var tokenizedBytes = StrictNbtTreeCodec.encode(
                    view.logicalTree(), MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES);
            var migrationInput = TokenizedSkillDocumentMigrationInput.copyOf(
                    tokenizedBytes.copyBytes());

            var migrated = migration.migrate(migrationInput);
            if (migrated instanceof OpaqueSkillDocumentMigrationFacade.Failure failed) {
                return rejected(FailureCode.DOCUMENT_MIGRATION_FAILED, failed.factReport());
            }
            var migrationSuccess = (OpaqueSkillDocumentMigrationFacade.Success) migrated;
            facts = migrationSuccess.factReport();
            var migratedRoot = decodeCompound(ImmutableEncodedBytes.copyOf(
                    migrationSuccess.migratedDocument().copyBytes()));
            if (migratedRoot.isEmpty()) {
                return rejected(
                        FailureCode.DOCUMENT_MIGRATION_FAILED,
                        migrationSuccess.factReport());
            }

            var reinserted = LogicalSkillDocumentConformanceView.reinsert(
                    migratedRoot.orElseThrow(), view.table());
            if (reinserted instanceof LogicalSkillDocumentConformanceView.ReinsertionResult.Failure failure) {
                var code = failure.kind()
                                == LogicalSkillDocumentConformanceView.FailureKind.TOKEN_INVARIANT
                        ? FailureCode.OPAQUE_TOKEN_INVARIANT_VIOLATION
                        : FailureCode.DOCUMENT_DECODE_FAILED;
                return rejected(code, migrationSuccess.factReport());
            }
            var currentPhysical = ((LogicalSkillDocumentConformanceView.ReinsertionResult.Success) reinserted)
                    .document();
            var encodedPhysical = PhysicalSkillDocumentNbt.encode(currentPhysical);
            if (encodedPhysical.failureValue().isPresent()) {
                return new LoadRejected(
                        mapPersistenceFailure(encodedPhysical.failureValue().orElseThrow(), false),
                        migrationSuccess.factReport());
            }
            var currentBytes = StrictNbtTreeCodec.encode(
                    encodedPhysical.successValue().orElseThrow(),
                    MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES);
            var hydrated = hydrator.hydrate(currentBytes, provider);
            if (hydrated.failureValue().isPresent()) {
                return new LoadRejected(
                        mapPersistenceFailure(hydrated.failureValue().orElseThrow(), false),
                        migrationSuccess.factReport());
            }
            return new Loaded(
                    hydrated.successValue().orElseThrow(),
                    migrationSuccess.factReport(),
                    migrationSuccess.migrated());
        } catch (BoundedByteEncoding.CapacityExceeded exception) {
            return new LoadRejected(
                    new CapacityFailure(
                            FailureCode.DOCUMENT_ENCODED_CAPACITY_EXCEEDED,
                            exception.observedAtLeast(),
                            exception.maximum()),
                    facts);
        } catch (IOException exception) {
            return rejected(FailureCode.DOCUMENT_DECODE_FAILED, facts);
        } catch (RuntimeException exception) {
            return rejected(FailureCode.INTERNAL_CODEC_EXCEPTION, facts);
        }
    }

    private static Optional<CompoundTag> decodeCompound(ImmutableEncodedBytes encoded)
            throws IOException {
        var decoded = StrictNbtTreeCodec.decode(
                encoded, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES);
        return decoded instanceof CompoundTag compound
                ? Optional.of(compound)
                : Optional.empty();
    }

    private static LoadRejected rejected(FailureCode code, PipelineFactReport facts) {
        return new LoadRejected(new SimpleFailure(code), facts);
    }

    private static PipelineFactReport emptyFacts() {
        return new PipelineFactReport(List.of(), false);
    }

    private static Failure mapPersistenceFailure(
            SkillDocumentPersistenceFailure failure,
            boolean encoding) {
        if (failure instanceof SkillDocumentPersistenceFailure.DocumentEncodedCapacityExceeded capacity) {
            return new CapacityFailure(
                    FailureCode.DOCUMENT_ENCODED_CAPACITY_EXCEEDED,
                    capacity.observedAtLeast(),
                    capacity.maximum());
        }
        if (failure instanceof SkillDocumentPersistenceFailure.UnsupportedDocumentSchema) {
            return new SimpleFailure(FailureCode.UNSUPPORTED_DOCUMENT_SCHEMA);
        }
        if (failure instanceof SkillDocumentPersistenceFailure.RegistryContextUnavailable) {
            return new SimpleFailure(FailureCode.REGISTRY_CONTEXT_UNAVAILABLE);
        }
        if (failure instanceof SkillDocumentPersistenceFailure.DocumentBoundsExceeded) {
            return new SimpleFailure(FailureCode.DOCUMENT_BOUNDS_EXCEEDED);
        }
        if (failure instanceof SkillDocumentPersistenceFailure.InternalCodecException) {
            return new SimpleFailure(FailureCode.INTERNAL_CODEC_EXCEPTION);
        }
        if (encoding) {
            return new SimpleFailure(FailureCode.ENCODE_REJECTED);
        }
        if (failure instanceof SkillDocumentPersistenceFailure.MalformedPhysicalDocument
                || failure instanceof SkillDocumentPersistenceFailure.InvalidRawContext) {
            return new SimpleFailure(FailureCode.MALFORMED_DOCUMENT);
        }
        return new SimpleFailure(FailureCode.DOCUMENT_DECODE_FAILED);
    }

    public sealed interface EncodeResult permits Encoded, EncodeRejected {
    }

    public record Encoded(EncodedSkillDocument document) implements EncodeResult {
        public Encoded {
            Objects.requireNonNull(document, "document");
        }
    }

    public record EncodeRejected(Failure failure) implements EncodeResult {
        public EncodeRejected {
            Objects.requireNonNull(failure, "failure");
        }
    }

    public sealed interface LoadResult permits Loaded, LoadRejected {
    }

    public record Loaded(
            SkillDocument document,
            PipelineFactReport factReport,
            boolean migrated) implements LoadResult {
        public Loaded {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(factReport, "factReport");
        }
    }

    public record LoadRejected(
            Failure failure,
            PipelineFactReport factReport) implements LoadResult {
        public LoadRejected {
            Objects.requireNonNull(failure, "failure");
            Objects.requireNonNull(factReport, "factReport");
        }
    }

    public sealed interface Failure permits SimpleFailure, CapacityFailure {
        FailureCode code();
    }

    public record SimpleFailure(FailureCode code) implements Failure {
        public SimpleFailure {
            Objects.requireNonNull(code, "code");
        }
    }

    public record CapacityFailure(
            FailureCode code,
            long observedAtLeast,
            long maximum) implements Failure {
        public CapacityFailure {
            Objects.requireNonNull(code, "code");
            if (code != FailureCode.DOCUMENT_ENCODED_CAPACITY_EXCEEDED
                    || maximum <= 0
                    || observedAtLeast <= maximum) {
                throw new IllegalArgumentException("invalid document capacity failure");
            }
        }
    }

    public enum FailureCode {
        ENCODE_REJECTED,
        DOCUMENT_ENCODED_CAPACITY_EXCEEDED,
        MALFORMED_DOCUMENT,
        UNSUPPORTED_DOCUMENT_SCHEMA,
        DOCUMENT_BOUNDS_EXCEEDED,
        REGISTRY_CONTEXT_UNAVAILABLE,
        DOCUMENT_MIGRATION_FAILED,
        OPAQUE_TOKEN_INVARIANT_VIOLATION,
        DOCUMENT_DECODE_FAILED,
        INTERNAL_CODEC_EXCEPTION
    }

    @FunctionalInterface
    interface TokenizedMigration {
        OpaqueSkillDocumentMigrationFacade.Result migrate(
                TokenizedSkillDocumentMigrationInput source);
    }

    @FunctionalInterface
    interface CurrentDocumentHydrator {
        SkillDocumentPersistenceResult<SkillDocument> hydrate(
                ImmutableEncodedBytes encoded,
                Optional<HolderLookup.Provider> provider);
    }
}
