package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentStorePersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;

/** Package-internal Store persistence composition; it neither mutates nor publishes a live Store. */
final class SkillDefinitionStorePersistenceBridge {
    private SkillDefinitionStorePersistenceBridge() {
    }

    static StorePersistenceEncodeResult encodeCurrentStoreBlob(SkillDefinitionStore store) {
        Objects.requireNonNull(store, "store");
        var encoded = encodeCurrentStoreLayout(store.snapshot());
        return switch (encoded) {
            case StoreLayoutEncodeResult.Success success ->
                    new StorePersistenceEncodeResult.Success(success.layout().blob());
            case StoreLayoutEncodeResult.Failure failure ->
                    encodeFailure(failure.failure());
        };
    }

    static StoreLayoutEncodeResult encodeCurrentStoreLayout(
            SkillDefinitionStoreSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        var histories = new ArrayList<StoreNbtFraming.EncodedHistoryFrame>();
        for (var history : snapshot.histories()) {
            var revisions = new ArrayList<StoreNbtFraming.EncodedRevisionFrame>();
            for (var revision : history.revisions()) {
                var document = revision.document();
                if (!history.skillId().equals(document.skillId())
                        || !revision.revision().equals(document.revision())) {
                    throw new IllegalStateException(
                            "Store snapshot route does not match its document route");
                }
                var encodedRevision = encodeCurrentRevision(document);
                if (encodedRevision.failureValue().isPresent()) {
                    return layoutEncodeFailure(encodedRevision.failureValue().orElseThrow());
                }
                revisions.add(encodedRevision.successValue().orElseThrow());
            }

            var historyFrame = StoreNbtFraming.encodeHistoryWithLayout(
                    history.skillId(), history.owner(), revisions);
            if (historyFrame.failureValue().isPresent()) {
                return layoutEncodeFailure(historyFrame.failureValue().orElseThrow());
            }
            histories.add(historyFrame.successValue().orElseThrow());
        }

        var storeFrame = StoreNbtFraming.encodeStoreWithLayout(
                StorePersistenceSchema.CURRENT_SCHEMA_VERSION, histories);
        if (storeFrame.failureValue().isPresent()) {
            return layoutEncodeFailure(storeFrame.failureValue().orElseThrow());
        }
        var frame = storeFrame.successValue().orElseThrow();
        return new StoreLayoutEncodeResult.Success(
                StoreEncodingLayout.fromWriterFrame(frame));
    }

    static StoreNbtFraming.FramingResult<StoreNbtFraming.EncodedRevisionFrame>
            encodeCurrentRevision(
            SkillDocument document) {
        Objects.requireNonNull(document, "document");
        try {
            var encoded = SkillDocumentStorePersistenceFacade.encodeCurrent(document);
            if (encoded instanceof SkillDocumentStorePersistenceFacade.EncodeRejected rejected) {
                return new StoreNbtFraming.FramingResult.Failure<>(
                        mapEncodeFailure(rejected.failure()));
            }
            var encodedDocument = ((SkillDocumentStorePersistenceFacade.Encoded) encoded).document();
            var revisionEnvelope = new RevisionPersistentEnvelopeV0(
                    document.revision(),
                    StorePersistenceSchema.DOCUMENT_ENCODING,
                    encodedDocument);
            var revisionBlob = StoreNbtFraming.encodeRevisionWithRoute(
                    new SkillReference(document.skillId(), document.revision()),
                    revisionEnvelope);
            if (revisionBlob.failureValue().isPresent()) {
                return new StoreNbtFraming.FramingResult.Failure<>(
                        revisionBlob.failureValue().orElseThrow());
            }
            return new StoreNbtFraming.FramingResult.Success<>(
                    revisionBlob.successValue().orElseThrow());
        } catch (RuntimeException exception) {
            return new StoreNbtFraming.FramingResult.Failure<>(
                    StorePersistenceFailure.EncodeFailed.INSTANCE);
        }
    }

    static StorePersistenceLoadResult loadStoreBlob(
            ImmutableStoreBlob blob,
            Optional<HolderLookup.Provider> provider) {
        return loadStoreBlob(
                blob,
                provider,
                SkillDefinitionStore::restore,
                SkillDocumentStorePersistenceFacade::load);
    }

    static StorePersistenceLoadResult loadStoreBlob(
            ImmutableStoreBlob blob,
            Optional<HolderLookup.Provider> provider,
            StoreRestorer restorer) {
        return loadStoreBlob(
                blob,
                provider,
                restorer,
                SkillDocumentStorePersistenceFacade::load);
    }

    static StorePersistenceLoadResult loadStoreBlob(
            ImmutableStoreBlob blob,
            Optional<HolderLookup.Provider> provider,
            StoreRestorer restorer,
            DocumentLoader documentLoader) {
        Objects.requireNonNull(blob, "blob");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(restorer, "restorer");
        Objects.requireNonNull(documentLoader, "documentLoader");
        var facts = new PipelineFactReport(List.of(), false);

        var decodedStore = StoreNbtFraming.decodeStore(blob);
        if (decodedStore.failureValue().isPresent()) {
            return loadFailure(decodedStore.failureValue().orElseThrow(), facts);
        }
        var initialEnvelope = decodedStore.successValue().orElseThrow();

        var migration = StorePersistenceMigrator.migrate(
                StoreNbtFraming.toTag(initialEnvelope),
                StorePersistenceMigrationPlans.production());
        if (migration instanceof StorePersistenceMigrationResult.Failure failed) {
            return loadFailure(
                    new StorePersistenceFailure.StoreEnvelopeMigrationFailed(failed.failure()),
                    failed.factReport());
        }
        var migrated = (StorePersistenceMigrationResult.Success) migration;
        facts = migrated.factReport();
        var rewritePending = migrated.migrated();
        var currentEnvelopeResult = StoreNbtFraming.fromTag(migrated.migratedTree());
        if (currentEnvelopeResult.failureValue().isPresent()) {
            return loadFailure(currentEnvelopeResult.failureValue().orElseThrow(), facts);
        }
        var currentEnvelope = currentEnvelopeResult.successValue().orElseThrow();
        if (currentEnvelope.schemaVersion() != StorePersistenceSchema.CURRENT_SCHEMA_VERSION) {
            return loadFailure(new StorePersistenceFailure.UnsupportedStoreSchema(
                    currentEnvelope.schemaVersion(),
                    StorePersistenceSchema.CURRENT_SCHEMA_VERSION), facts);
        }

        var historySnapshots = new ArrayList<SkillHistorySnapshot>();
        for (var historyBlob : currentEnvelope.historyEntries()) {
            var decodedHistory = StoreNbtFraming.decodeHistory(historyBlob);
            if (decodedHistory.failureValue().isPresent()) {
                return loadFailure(decodedHistory.failureValue().orElseThrow(), facts);
            }
            var history = decodedHistory.successValue().orElseThrow();
            var revisions = new ArrayList<SkillRevisionSnapshot>();
            for (var revisionBlob : history.revisionEntries()) {
                var decodedRevision = StoreNbtFraming.decodeRevisionForStore(
                        revisionBlob, history.skillId());
                if (decodedRevision.failureValue().isPresent()) {
                    return loadFailure(decodedRevision.failureValue().orElseThrow(), facts);
                }
                var revision = decodedRevision.successValue().orElseThrow();
                var reference = new SkillReference(history.skillId(), revision.revision());

                var loaded = documentLoader.load(revision.document(), provider);
                if (loaded instanceof SkillDocumentStorePersistenceFacade.LoadRejected rejected) {
                    facts = facts.append(rejected.factReport());
                    return loadFailure(mapLoadFailure(reference, rejected.failure()), facts);
                }
                var document = (SkillDocumentStorePersistenceFacade.Loaded) loaded;
                facts = facts.append(document.factReport());
                rewritePending |= document.migrated();
                revisions.add(new SkillRevisionSnapshot(revision.revision(), document.document()));
            }
            historySnapshots.add(new SkillHistorySnapshot(
                    history.skillId(), history.owner(), revisions));
        }

        var restored = restorer.restore(new SkillDefinitionStoreSnapshot(historySnapshots));
        return switch (restored) {
            case SkillDefinitionStoreRestoreResult.Restored success ->
                    new StorePersistenceLoadResult.Loaded(
                            success.store(), facts, rewritePending);
            case SkillDefinitionStoreRestoreResult.Rejected rejected ->
                    loadFailure(
                            new StorePersistenceFailure.StoreRestoreRejected(rejected.failure()),
                            facts);
        };
    }

    private static StorePersistenceFailure mapEncodeFailure(
            SkillDocumentStorePersistenceFacade.Failure failure) {
        if (failure instanceof SkillDocumentStorePersistenceFacade.CapacityFailure capacity) {
            return new StorePersistenceFailure.DocumentBlobEncodedCapacityExceeded(
                    capacity.observedAtLeast(), capacity.maximum());
        }
        return StorePersistenceFailure.EncodeFailed.INSTANCE;
    }

    private static StorePersistenceFailure mapLoadFailure(
            SkillReference reference,
            SkillDocumentStorePersistenceFacade.Failure failure) {
        return switch (failure.code()) {
            case DOCUMENT_ENCODED_CAPACITY_EXCEEDED -> {
                if (failure instanceof SkillDocumentStorePersistenceFacade.CapacityFailure capacity) {
                    yield new StorePersistenceFailure.DocumentBlobEncodedCapacityExceeded(
                            capacity.observedAtLeast(), capacity.maximum());
                }
                yield new StorePersistenceFailure.DocumentDecodeFailed(reference);
            }
            case REGISTRY_CONTEXT_UNAVAILABLE ->
                    new StorePersistenceFailure.RegistryContextUnavailable(reference);
            case DOCUMENT_MIGRATION_FAILED, UNSUPPORTED_DOCUMENT_SCHEMA ->
                    new StorePersistenceFailure.DocumentMigrationFailed(reference);
            case OPAQUE_TOKEN_INVARIANT_VIOLATION ->
                    new StorePersistenceFailure.OpaqueTokenInvariantViolation(reference);
            case MALFORMED_DOCUMENT, DOCUMENT_BOUNDS_EXCEEDED,
                    DOCUMENT_DECODE_FAILED, INTERNAL_CODEC_EXCEPTION, ENCODE_REJECTED ->
                    new StorePersistenceFailure.DocumentDecodeFailed(reference);
        };
    }

    private static StorePersistenceEncodeResult.Failure encodeFailure(
            StorePersistenceFailure failure) {
        return new StorePersistenceEncodeResult.Failure(failure);
    }

    private static StoreLayoutEncodeResult.Failure layoutEncodeFailure(
            StorePersistenceFailure failure) {
        return new StoreLayoutEncodeResult.Failure(failure);
    }

    private static StorePersistenceLoadResult.Failure loadFailure(
            StorePersistenceFailure failure,
            PipelineFactReport facts) {
        return new StorePersistenceLoadResult.Failure(failure, facts);
    }

    @FunctionalInterface
    interface StoreRestorer {
        SkillDefinitionStoreRestoreResult restore(SkillDefinitionStoreSnapshot snapshot);
    }

    @FunctionalInterface
    interface DocumentLoader {
        SkillDocumentStorePersistenceFacade.LoadResult load(
                com.yo1no.gramarye.magic.definition.document.EncodedSkillDocument document,
                Optional<HolderLookup.Provider> provider);
    }

}
