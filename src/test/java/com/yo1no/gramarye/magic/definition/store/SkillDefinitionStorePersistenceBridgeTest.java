package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.EncodedSkillDocument;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentStorePersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import com.yo1no.gramarye.magic.definition.migration.SkillMigrationFact;
import com.yo1no.gramarye.magic.definition.migration.SkillMigrationFactCode;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;

class SkillDefinitionStorePersistenceBridgeTest {
    private static final HolderLookup.Provider EMPTY_PROVIDER =
            HolderLookup.Provider.create(Stream.empty());

    @Test
    void emptyStoreRoundTripsWithoutInventingHistoriesOrRewrite() {
        var encoded = assertInstanceOf(StorePersistenceEncodeResult.Success.class,
                SkillDefinitionStorePersistenceBridge.encodeCurrentStoreBlob(
                        new SkillDefinitionStore()));
        var loaded = assertInstanceOf(StorePersistenceLoadResult.Loaded.class,
                SkillDefinitionStorePersistenceBridge.loadStoreBlob(
                        encoded.blob(), Optional.empty()));

        assertTrue(loaded.store().snapshot().histories().isEmpty());
        assertTrue(loaded.factReport().facts().isEmpty());
        assertFalse(loaded.rewritePending());
    }

    @Test
    void multipleOwnersSparseRevisionsAndCanonicalOrderRoundTrip() {
        var first = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(3), StoreTestFixtures.ownerId(2), 9, 1),
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(1), StoreTestFixtures.ownerId(1), 5),
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(2), StoreTestFixtures.ownerId(1), 4, 0)));
        var reordered = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(2), StoreTestFixtures.ownerId(1), 0, 4),
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(1), StoreTestFixtures.ownerId(1), 5),
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(3), StoreTestFixtures.ownerId(2), 1, 9)));

        var firstBlob = encoded(first);
        var secondBlob = encoded(reordered);
        var loaded = loaded(firstBlob);

        assertEquals(firstBlob, secondBlob);
        assertEquals(
                List.of(StoreTestFixtures.skillId(1), StoreTestFixtures.skillId(2),
                        StoreTestFixtures.skillId(3)),
                loaded.store().snapshot().histories().stream()
                        .map(SkillHistorySnapshot::skillId).toList());
        assertEquals(new SkillReference(StoreTestFixtures.skillId(3), StoreTestFixtures.revision(9)),
                loaded.store().latestReference(StoreTestFixtures.skillId(3)).orElseThrow());
    }

    @Test
    void duplicateSkillAndRevisionListsReachRestoreWithoutMapNormalization() {
        var skillId = StoreTestFixtures.skillId(10);
        var owner = StoreTestFixtures.ownerId(10);
        var revision = revisionBlob(skillId, 0, 0);
        var history = historyBlob(skillId, owner, List.of(revision));
        var duplicateSkills = storeBlob(List.of(history, history));
        var duplicateRevisions = storeBlob(List.of(
                historyBlob(skillId, owner, List.of(revision, revision))));
        var skillCalls = new AtomicInteger();
        var revisionCalls = new AtomicInteger();

        var skillFailure = loadFailure(duplicateSkills, countingRestorer(skillCalls));
        var revisionFailure = loadFailure(duplicateRevisions, countingRestorer(revisionCalls));

        assertInstanceOf(SkillDefinitionStoreRestoreFailure.DuplicateSkillId.class,
                assertInstanceOf(StorePersistenceFailure.StoreRestoreRejected.class,
                        skillFailure.failure()).failure());
        assertInstanceOf(SkillDefinitionStoreRestoreFailure.DuplicateRevision.class,
                assertInstanceOf(StorePersistenceFailure.StoreRestoreRejected.class,
                        revisionFailure.failure()).failure());
        assertEquals(1, skillCalls.get());
        assertEquals(1, revisionCalls.get());
    }

    @Test
    void routeDocumentMismatchIsRestoreRejectionAfterSuccessfulDecodeAndMigration() {
        var skillId = StoreTestFixtures.skillId(20);
        var blob = storeBlob(List.of(historyBlob(
                skillId,
                StoreTestFixtures.ownerId(20),
                List.of(revisionBlob(skillId, 1, 0)))));
        var calls = new AtomicInteger();

        var failure = loadFailure(blob, countingRestorer(calls));
        var rejected = assertInstanceOf(
                StorePersistenceFailure.StoreRestoreRejected.class, failure.failure());

        assertInstanceOf(SkillDefinitionStoreRestoreFailure.DocumentRevisionMismatch.class,
                rejected.failure());
        assertEquals(1, calls.get());
    }

    @Test
    void storeMigrationFailureStopsBeforeDocumentHydrationAndRestore() {
        var future = StoreNbtFraming.encodeStore(new StorePersistentEnvelopeV0(1, List.of()))
                .successValue().orElseThrow();
        var calls = new AtomicInteger();

        var failure = loadFailure(future, countingRestorer(calls));
        var migration = assertInstanceOf(
                StorePersistenceFailure.StoreEnvelopeMigrationFailed.class,
                failure.failure());

        assertEquals(StorePersistenceMigrationFailure.Code.FUTURE_SCHEMA_VERSION,
                migration.failure().code());
        assertEquals(0, calls.get());
    }

    @Test
    void futureStoreSchemaPrecedesCorruptNestedHistoryAndRestore() {
        var futureWithCorruptHistory = StoreNbtFraming.encodeStore(
                new StorePersistentEnvelopeV0(
                        1, List.of(ImmutableHistoryBlob.copyOf(new byte[] {1}))))
                .successValue().orElseThrow();
        var calls = new AtomicInteger();

        var failure = loadFailure(futureWithCorruptHistory, countingRestorer(calls));
        var migration = assertInstanceOf(
                StorePersistenceFailure.StoreEnvelopeMigrationFailed.class,
                failure.failure());

        assertEquals(StorePersistenceMigrationFailure.Code.FUTURE_SCHEMA_VERSION,
                migration.failure().code());
        assertEquals(0, calls.get());
    }

    @Test
    void firstUnsupportedEncodingInPhysicalListOrderWinsDeterministically() {
        var firstSkill = StoreTestFixtures.skillId(31);
        var secondSkill = StoreTestFixtures.skillId(32);
        var first = historyBlob(
                firstSkill,
                StoreTestFixtures.ownerId(31),
                List.of(revisionBlobWithEncoding(
                        firstSkill, 0, "xamily_tagged_subtrees_v0")));
        var second = historyBlob(
                secondSkill,
                StoreTestFixtures.ownerId(32),
                List.of(revisionBlobWithEncoding(
                        secondSkill, 0, "yamily_tagged_subtrees_v0")));

        assertUnsupportedEncodingRoute(storeBlob(List.of(first, second)), firstSkill);
        assertUnsupportedEncodingRoute(storeBlob(List.of(second, first)), secondSkill);
    }

    @Test
    void firstUnsupportedEncodingInRevisionListOrderWinsDeterministically() {
        var skillId = StoreTestFixtures.skillId(33);
        var first = revisionBlobWithEncoding(
                skillId, 7, "xamily_tagged_subtrees_v0");
        var second = revisionBlobWithEncoding(
                skillId, 9, "yamily_tagged_subtrees_v0");
        var owner = StoreTestFixtures.ownerId(33);

        assertUnsupportedEncodingReference(
                storeBlob(List.of(historyBlob(skillId, owner, List.of(first, second)))),
                new SkillReference(skillId, StoreTestFixtures.revision(7)));
        assertUnsupportedEncodingReference(
                storeBlob(List.of(historyBlob(skillId, owner, List.of(second, first)))),
                new SkillReference(skillId, StoreTestFixtures.revision(9)));
    }

    @Test
    void malformedPhysicalInputStopsBeforeRestoreAndReturnsNoPartialStore() {
        var calls = new AtomicInteger();
        var failure = loadFailure(ImmutableStoreBlob.copyOf(new byte[] {1}),
                countingRestorer(calls));

        assertInstanceOf(StorePersistenceFailure.MalformedStoreEnvelope.class,
                failure.failure());
        assertEquals(0, calls.get());
        assertFalse(java.util.Arrays.stream(failure.getClass().getMethods())
                .anyMatch(method -> method.getName().equals("store")));
    }

    @Test
    void missingRegistryProviderIsTypedAndRestoreIsNotInvoked() {
        var ops = RegistryOps.create(JsonOps.INSTANCE, EMPTY_PROVIDER);
        var payload = new Dynamic<>(ops, new JsonObject());
        var skillId = StoreTestFixtures.skillId(30);
        var document = new SkillDocument(
                0,
                skillId,
                StoreTestFixtures.revision(0),
                List.of(new NodeDocument(
                        new DefinitionEnvelope(id("trigger"), 0, payload),
                        new DefinitionEnvelope(id("action"), 0, payload),
                        AppearanceOverrideDocument.none())),
                AppearanceDocument.defaultAppearance());
        var encodedDocument = encodeDocument(document);
        var revision = StoreNbtFraming.encodeRevision(new RevisionPersistentEnvelopeV0(
                StoreTestFixtures.revision(0),
                StorePersistenceSchema.DOCUMENT_ENCODING,
                encodedDocument)).successValue().orElseThrow();
        var blob = storeBlob(List.of(historyBlob(
                skillId, StoreTestFixtures.ownerId(30), List.of(revision))));
        var calls = new AtomicInteger();

        var failure = loadFailure(blob, countingRestorer(calls));

        assertInstanceOf(StorePersistenceFailure.RegistryContextUnavailable.class,
                failure.failure());
        assertEquals(0, calls.get());
    }

    @Test
    void migratedDocumentBehaviorSetsRewritePendingAndPreservesFacts() {
        var skillId = StoreTestFixtures.skillId(40);
        var blob = storeBlob(List.of(markerHistory(
                skillId, StoreTestFixtures.ownerId(40), 1)));
        var calls = new AtomicInteger();
        var facts = new PipelineFactReport(List.of(new SkillMigrationFact(
                SkillMigrationFactCode.STEP_APPLIED,
                0,
                1,
                OptionalInt.of(0))), false);

        var loaded = assertInstanceOf(StorePersistenceLoadResult.Loaded.class,
                SkillDefinitionStorePersistenceBridge.loadStoreBlob(
                        blob,
                        Optional.empty(),
                        countingRestorer(calls),
                        (encoded, provider) -> new SkillDocumentStorePersistenceFacade.Loaded(
                                markerDocument(encoded), facts, true)));

        assertTrue(loaded.rewritePending());
        assertEquals(facts, loaded.factReport());
        assertEquals(1, calls.get());
    }

    @Test
    void syntheticOldDocumentMigrationFeedsCurrentSnapshotToRestore() {
        var skillId = StoreTestFixtures.skillId(41);
        var blob = storeBlob(List.of(markerHistory(
                skillId, StoreTestFixtures.ownerId(41), 1)));
        var calls = new AtomicInteger();

        var loaded = assertInstanceOf(StorePersistenceLoadResult.Loaded.class,
                SkillDefinitionStorePersistenceBridge.loadStoreBlob(
                        blob,
                        Optional.empty(),
                        countingRestorer(calls),
                        (encoded, provider) -> new SkillDocumentStorePersistenceFacade.Loaded(
                                markerDocument(encoded), emptyFacts(), true)));

        assertTrue(loaded.rewritePending());
        assertEquals(1, calls.get());
    }

    @Test
    void sameNonCurrentDocumentWithoutMigrationIsRejectedByDirectRestore() {
        var skillId = StoreTestFixtures.skillId(42);
        var current = StoreTestFixtures.document(skillId, 0);
        var nonCurrent = StoreTestFixtures.document(
                skillId, 0, 5, current.nodes());

        var rejected = assertInstanceOf(SkillDefinitionStoreRestoreResult.Rejected.class,
                SkillDefinitionStore.restore(new SkillDefinitionStoreSnapshot(List.of(
                        new SkillHistorySnapshot(
                                skillId,
                                StoreTestFixtures.ownerId(42),
                                List.of(new SkillRevisionSnapshot(
                                        StoreTestFixtures.revision(0), nonCurrent)))))));

        assertInstanceOf(SkillDefinitionStoreRestoreFailure.UnsupportedDocumentSchema.class,
                rejected.failure());
    }

    @Test
    void documentMigrationMissingOrExceptionFailureStopsBeforeRestore() {
        var skillId = StoreTestFixtures.skillId(43);
        var blob = storeBlob(List.of(markerHistory(
                skillId, StoreTestFixtures.ownerId(43), 1)));

        var exceptionFact = new PipelineFactReport(List.of(new SkillMigrationFact(
                SkillMigrationFactCode.STEP_APPLIED,
                0,
                1,
                OptionalInt.of(0))), false);
        for (var failureFacts : List.of(emptyFacts(), exceptionFact)) {
            var calls = new AtomicInteger();
            var failed = loadFailure(
                    blob,
                    countingRestorer(calls),
                    (encoded, provider) -> new SkillDocumentStorePersistenceFacade.LoadRejected(
                            new SkillDocumentStorePersistenceFacade.SimpleFailure(
                                    SkillDocumentStorePersistenceFacade.FailureCode
                                            .DOCUMENT_MIGRATION_FAILED),
                            failureFacts));

            assertInstanceOf(StorePersistenceFailure.DocumentMigrationFailed.class,
                    failed.failure());
            assertEquals(0, calls.get());
        }
    }

    @Test
    void successfulDocumentMigrationStillLeavesDomainCorruptionToRestore() {
        var skillId = StoreTestFixtures.skillId(44);
        var blob = storeBlob(List.of(markerHistory(
                skillId, StoreTestFixtures.ownerId(44), 1)));
        var calls = new AtomicInteger();

        var failed = loadFailure(
                blob,
                countingRestorer(calls),
                (encoded, provider) -> new SkillDocumentStorePersistenceFacade.Loaded(
                        StoreTestFixtures.document(skillId, 1), emptyFacts(), true));

        assertInstanceOf(SkillDefinitionStoreRestoreFailure.DocumentRevisionMismatch.class,
                assertInstanceOf(StorePersistenceFailure.StoreRestoreRejected.class,
                        failed.failure()).failure());
        assertEquals(1, calls.get());
    }

    @Test
    void physicalGlobalHistoryOverageReachesRestoreExactlyOnce() {
        var histories = new ArrayList<ImmutableHistoryBlob>();
        var count = MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL + 1;
        for (var index = 0; index < count; index++) {
            histories.add(markerHistory(
                    StoreTestFixtures.skillId(100_000L + index),
                    StoreTestFixtures.ownerId(200_000L + index),
                    1));
        }

        assertDomainCapacityFailure(
                storeBlob(histories),
                SkillStoreCapacityScope.GLOBAL_SKILL_HISTORIES,
                count,
                count);
    }

    @Test
    void physicalOwnerHistoryOverageReachesRestoreExactlyOnce() {
        var histories = new ArrayList<ImmutableHistoryBlob>();
        var count = MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER + 1;
        var owner = StoreTestFixtures.ownerId(300_000L);
        for (var index = 0; index < count; index++) {
            histories.add(markerHistory(
                    StoreTestFixtures.skillId(300_000L + index), owner, 1));
        }

        assertDomainCapacityFailure(
                storeBlob(histories),
                SkillStoreCapacityScope.OWNER_SKILL_HISTORIES,
                count,
                count);
    }

    @Test
    void physicalPerSkillRevisionOverageReachesRestoreExactlyOnce() {
        var count = MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL + 1;
        var skillId = StoreTestFixtures.skillId(400_000L);
        var blob = storeBlob(List.of(markerHistory(
                skillId, StoreTestFixtures.ownerId(400_000L), count)));

        assertDomainCapacityFailure(
                blob,
                SkillStoreCapacityScope.SKILL_RETAINED_REVISIONS,
                count,
                count);
    }

    @Test
    void physicalGlobalRevisionOverageReachesRestoreExactlyOnce() {
        var histories = new ArrayList<ImmutableHistoryBlob>();
        var perSkill = MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL;
        var fullHistories = MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL / perSkill;
        for (var index = 0; index < fullHistories; index++) {
            histories.add(markerHistory(
                    StoreTestFixtures.skillId(500_000L + index),
                    StoreTestFixtures.ownerId(500_000L + index),
                    perSkill));
        }
        histories.add(markerHistory(
                StoreTestFixtures.skillId(600_000L),
                StoreTestFixtures.ownerId(600_000L),
                1));
        var observed = MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL + 1;

        assertDomainCapacityFailure(
                storeBlob(histories),
                SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS,
                observed,
                observed);
    }

    private static ImmutableStoreBlob encoded(SkillDefinitionStore store) {
        return assertInstanceOf(StorePersistenceEncodeResult.Success.class,
                SkillDefinitionStorePersistenceBridge.encodeCurrentStoreBlob(store)).blob();
    }

    private static StorePersistenceLoadResult.Loaded loaded(ImmutableStoreBlob blob) {
        return assertInstanceOf(StorePersistenceLoadResult.Loaded.class,
                SkillDefinitionStorePersistenceBridge.loadStoreBlob(blob, Optional.empty()));
    }

    private static StorePersistenceLoadResult.Failure loadFailure(
            ImmutableStoreBlob blob,
            SkillDefinitionStorePersistenceBridge.StoreRestorer restorer) {
        return assertInstanceOf(StorePersistenceLoadResult.Failure.class,
                SkillDefinitionStorePersistenceBridge.loadStoreBlob(
                        blob, Optional.empty(), restorer));
    }

    private static StorePersistenceLoadResult.Failure loadFailure(
            ImmutableStoreBlob blob,
            SkillDefinitionStorePersistenceBridge.StoreRestorer restorer,
            SkillDefinitionStorePersistenceBridge.DocumentLoader documentLoader) {
        return assertInstanceOf(StorePersistenceLoadResult.Failure.class,
                SkillDefinitionStorePersistenceBridge.loadStoreBlob(
                        blob, Optional.empty(), restorer, documentLoader));
    }

    private static SkillDefinitionStorePersistenceBridge.StoreRestorer countingRestorer(
            AtomicInteger calls) {
        return snapshot -> {
            calls.incrementAndGet();
            return SkillDefinitionStore.restore(snapshot);
        };
    }

    private static ImmutableStoreBlob storeBlob(List<ImmutableHistoryBlob> histories) {
        return StoreNbtFraming.encodeStore(new StorePersistentEnvelopeV0(0, histories))
                .successValue().orElseThrow();
    }

    private static ImmutableHistoryBlob historyBlob(
            com.yo1no.gramarye.magic.api.id.SkillId skillId,
            com.yo1no.gramarye.magic.api.id.SkillOwnerId owner,
            List<ImmutableRevisionBlob> revisions) {
        return StoreNbtFraming.encodeHistory(
                new HistoryPersistentEnvelopeV0(skillId, owner, revisions))
                .successValue().orElseThrow();
    }

    private static ImmutableHistoryBlob markerHistory(
            com.yo1no.gramarye.magic.api.id.SkillId skillId,
            com.yo1no.gramarye.magic.api.id.SkillOwnerId owner,
            int revisionCount) {
        var revisions = new ArrayList<ImmutableRevisionBlob>();
        for (var revision = 0; revision < revisionCount; revision++) {
            revisions.add(markerRevision(skillId, revision));
        }
        return historyBlob(skillId, owner, revisions);
    }

    private static ImmutableRevisionBlob markerRevision(
            com.yo1no.gramarye.magic.api.id.SkillId skillId,
            int revision) {
        var encoded = marker(skillId, revision);
        return StoreNbtFraming.encodeRevision(new RevisionPersistentEnvelopeV0(
                StoreTestFixtures.revision(revision),
                StorePersistenceSchema.DOCUMENT_ENCODING,
                encoded)).successValue().orElseThrow();
    }

    private static EncodedSkillDocument marker(
            com.yo1no.gramarye.magic.api.id.SkillId skillId,
            int revision) {
        var uuid = skillId.value();
        var bytes = ByteBuffer.allocate(Long.BYTES * 2 + Integer.BYTES)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .putInt(revision)
                .array();
        return EncodedSkillDocument.copyOf(bytes);
    }

    private static SkillDocument markerDocument(EncodedSkillDocument encoded) {
        var bytes = encoded.copyBytes();
        if (bytes.length != Long.BYTES * 2 + Integer.BYTES) {
            throw new AssertionError("unexpected marker length");
        }
        var marker = ByteBuffer.wrap(bytes);
        var skillId = new com.yo1no.gramarye.magic.api.id.SkillId(
                new UUID(marker.getLong(), marker.getLong()));
        return StoreTestFixtures.document(skillId, marker.getInt());
    }

    private static PipelineFactReport emptyFacts() {
        return new PipelineFactReport(List.of(), false);
    }

    private static void assertDomainCapacityFailure(
            ImmutableStoreBlob blob,
            SkillStoreCapacityScope expectedScope,
            int expectedCurrent,
            int expectedDocumentLoads) {
        var restoreCalls = new AtomicInteger();
        var documentLoads = new AtomicInteger();
        var failure = loadFailure(
                blob,
                countingRestorer(restoreCalls),
                (encoded, provider) -> {
                    documentLoads.incrementAndGet();
                    return new SkillDocumentStorePersistenceFacade.Loaded(
                            markerDocument(encoded), emptyFacts(), false);
                });
        var capacity = assertInstanceOf(
                SkillDefinitionStoreRestoreFailure.CapacityExceeded.class,
                assertInstanceOf(StorePersistenceFailure.StoreRestoreRejected.class,
                        failure.failure()).failure());

        assertEquals(expectedScope, capacity.scope());
        assertEquals(expectedCurrent, capacity.current());
        assertEquals(expectedScope.canonicalMaximum(), capacity.maximum());
        assertEquals(1, restoreCalls.get());
        assertEquals(expectedDocumentLoads, documentLoads.get());
    }

    private static ImmutableRevisionBlob revisionBlob(
            com.yo1no.gramarye.magic.api.id.SkillId documentSkillId,
            int routeRevision,
            int documentRevision) {
        var document = StoreTestFixtures.document(documentSkillId, documentRevision);
        return StoreNbtFraming.encodeRevision(new RevisionPersistentEnvelopeV0(
                StoreTestFixtures.revision(routeRevision),
                StorePersistenceSchema.DOCUMENT_ENCODING,
                encodeDocument(document))).successValue().orElseThrow();
    }

    private static ImmutableRevisionBlob revisionBlobWithEncoding(
            com.yo1no.gramarye.magic.api.id.SkillId documentSkillId,
            int revision,
            String encoding) {
        return StoreNbtFraming.encodeRevision(new RevisionPersistentEnvelopeV0(
                StoreTestFixtures.revision(revision),
                encoding,
                marker(documentSkillId, revision))).successValue().orElseThrow();
    }

    private static void assertUnsupportedEncodingRoute(
            ImmutableStoreBlob blob,
            com.yo1no.gramarye.magic.api.id.SkillId expectedSkillId) {
        assertUnsupportedEncodingReference(
                blob,
                new SkillReference(expectedSkillId, StoreTestFixtures.revision(0)));
    }

    private static void assertUnsupportedEncodingReference(
            ImmutableStoreBlob blob,
            SkillReference expectedReference) {
        var restoreCalls = new AtomicInteger();
        var failure = loadFailure(blob, countingRestorer(restoreCalls));
        var unsupported = assertInstanceOf(
                StorePersistenceFailure.UnsupportedDocumentEncoding.class,
                failure.failure());

        assertEquals(expectedReference, unsupported.reference());
        assertEquals(0, restoreCalls.get());
    }

    private static EncodedSkillDocument encodeDocument(SkillDocument document) {
        return assertInstanceOf(SkillDocumentStorePersistenceFacade.Encoded.class,
                SkillDocumentStorePersistenceFacade.encodeCurrent(document)).document();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

}
