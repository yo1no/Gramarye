package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class SkillDefinitionStoreRestoreTest {
    @Test
    void snapshotConstructorsRejectNullButPreserveDuplicateRawEntriesForRestore() {
        var skillId = StoreTestFixtures.skillId(1);
        var owner = StoreTestFixtures.ownerId(1);
        var revision = StoreTestFixtures.revisionSnapshot(skillId, 0);
        var duplicateHistory = new SkillHistorySnapshot(skillId, owner, List.of(revision, revision));
        var duplicateSkills = new SkillDefinitionStoreSnapshot(List.of(duplicateHistory, duplicateHistory));

        assertAll(
                () -> assertEquals(2, duplicateHistory.revisions().size()),
                () -> assertEquals(2, duplicateSkills.histories().size()),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillDefinitionStoreSnapshot(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillDefinitionStoreSnapshot(Arrays.asList(duplicateHistory, null))),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillHistorySnapshot(skillId, owner, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillRevisionSnapshot(StoreTestFixtures.revision(0), null)));
    }

    @Test
    void duplicateSkillIdHasDeterministicPrecedenceOverHistoryContent() {
        var skillId = StoreTestFixtures.skillId(2);
        var owner = StoreTestFixtures.ownerId(2);
        var snapshot = StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, owner, 0),
                new SkillHistorySnapshot(skillId, owner, List.of()));

        var first = rejected(snapshot);
        var second = rejected(snapshot);
        var failure = assertInstanceOf(
                SkillDefinitionStoreRestoreFailure.DuplicateSkillId.class,
                first.failure());
        assertAll(
                () -> assertEquals(skillId, failure.skillId()),
                () -> assertEquals(first, second),
                () -> assertEquals(2, snapshot.histories().size()));
    }

    @Test
    void emptyHistoryIsRejectedButRevisionGapsAndMissingZeroAreNot() {
        var skillId = StoreTestFixtures.skillId(3);
        var owner = StoreTestFixtures.ownerId(3);
        var empty = StoreTestFixtures.snapshot(new SkillHistorySnapshot(skillId, owner, List.of()));

        var failure = assertInstanceOf(
                SkillDefinitionStoreRestoreFailure.EmptyHistory.class,
                rejected(empty).failure());
        var sparse = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, owner, 2, 9)));

        assertAll(
                () -> assertEquals(skillId, failure.skillId()),
                () -> assertEquals(new SkillReference(skillId, StoreTestFixtures.revision(9)),
                        sparse.latestReference(skillId).orElseThrow()));
    }

    @Test
    void duplicateRevisionIsRejectedBeforeDocumentPairingChecks() {
        var skillId = StoreTestFixtures.skillId(4);
        var owner = StoreTestFixtures.ownerId(4);
        var valid = StoreTestFixtures.revisionSnapshot(skillId, 0);
        var mismatched = new SkillRevisionSnapshot(
                StoreTestFixtures.revision(0),
                StoreTestFixtures.document(StoreTestFixtures.skillId(99), 7));
        var snapshot = StoreTestFixtures.snapshot(
                new SkillHistorySnapshot(skillId, owner, List.of(valid, mismatched)));

        var failure = assertInstanceOf(
                SkillDefinitionStoreRestoreFailure.DuplicateRevision.class,
                rejected(snapshot).failure());
        assertEquals(new SkillReference(skillId, StoreTestFixtures.revision(0)), failure.reference());
    }

    @Test
    void routeSkillIdMismatchUsesTypedBoundedMetadata() {
        var route = StoreTestFixtures.skillId(5);
        var documentSkill = StoreTestFixtures.skillId(6);
        var revision = StoreTestFixtures.revision(3);
        var snapshot = StoreTestFixtures.snapshot(new SkillHistorySnapshot(
                route,
                StoreTestFixtures.ownerId(5),
                List.of(new SkillRevisionSnapshot(revision,
                        StoreTestFixtures.document(documentSkill, revision.value())))));

        var failure = assertInstanceOf(
                SkillDefinitionStoreRestoreFailure.DocumentSkillIdMismatch.class,
                rejected(snapshot).failure());
        assertAll(
                () -> assertEquals(route, failure.routeSkillId()),
                () -> assertEquals(documentSkill, failure.documentSkillId()),
                () -> assertEquals(revision, failure.routeRevision()));
    }

    @Test
    void routeRevisionMismatchReportsBothTypedReferences() {
        var skillId = StoreTestFixtures.skillId(7);
        var snapshot = StoreTestFixtures.snapshot(new SkillHistorySnapshot(
                skillId,
                StoreTestFixtures.ownerId(7),
                List.of(new SkillRevisionSnapshot(
                        StoreTestFixtures.revision(4),
                        StoreTestFixtures.document(skillId, 5)))));

        var failure = assertInstanceOf(
                SkillDefinitionStoreRestoreFailure.DocumentRevisionMismatch.class,
                rejected(snapshot).failure());
        assertAll(
                () -> assertEquals(new SkillReference(skillId, StoreTestFixtures.revision(4)),
                        failure.routeReference()),
                () -> assertEquals(new SkillReference(skillId, StoreTestFixtures.revision(5)),
                        failure.documentReference()));
    }

    @Test
    void restoreRequiresCurrentDocumentSchemaAndNonEmptyNodes() {
        var schemaSkill = StoreTestFixtures.skillId(8);
        var schemaRevision = StoreTestFixtures.revision(0);
        var unsupported = StoreTestFixtures.document(
                schemaSkill,
                0,
                SkillDocument.CURRENT_SCHEMA_VERSION + 1,
                List.of(sharedNode()));
        var schemaFailure = assertInstanceOf(
                SkillDefinitionStoreRestoreFailure.UnsupportedDocumentSchema.class,
                rejected(StoreTestFixtures.snapshot(new SkillHistorySnapshot(
                                schemaSkill,
                                StoreTestFixtures.ownerId(8),
                                List.of(new SkillRevisionSnapshot(schemaRevision, unsupported)))))
                        .failure());

        var emptySkill = StoreTestFixtures.skillId(9);
        var emptyDocument = StoreTestFixtures.document(
                emptySkill,
                0,
                SkillDocument.CURRENT_SCHEMA_VERSION,
                List.of());
        var emptyFailure = assertInstanceOf(
                SkillDefinitionStoreRestoreFailure.EmptyDocumentNodes.class,
                rejected(StoreTestFixtures.snapshot(new SkillHistorySnapshot(
                                emptySkill,
                                StoreTestFixtures.ownerId(9),
                                List.of(new SkillRevisionSnapshot(StoreTestFixtures.revision(0), emptyDocument)))))
                        .failure());

        assertAll(
                () -> assertEquals(new SkillReference(schemaSkill, schemaRevision), schemaFailure.reference()),
                () -> assertEquals(SkillDocument.CURRENT_SCHEMA_VERSION + 1, schemaFailure.actual()),
                () -> assertEquals(SkillDocument.CURRENT_SCHEMA_VERSION, schemaFailure.expected()),
                () -> assertEquals(new SkillReference(emptySkill, StoreTestFixtures.revision(0)),
                        emptyFailure.reference()));
    }

    @Test
    void restoreFailureNeverContainsRawDocumentOrDiagnosticText() {
        var secret = "UNIQUE_P3_D1_RAW_SECRET";
        var route = StoreTestFixtures.skillId(10);
        var documentSkill = StoreTestFixtures.skillId(11);
        var secretNode = nodeWithSecret(secret);
        var document = StoreTestFixtures.document(
                documentSkill,
                0,
                SkillDocument.CURRENT_SCHEMA_VERSION,
                List.of(secretNode));
        var revisionSnapshot = new SkillRevisionSnapshot(StoreTestFixtures.revision(0), document);
        var historySnapshot = new SkillHistorySnapshot(
                route,
                StoreTestFixtures.ownerId(10),
                List.of(revisionSnapshot));
        var snapshot = StoreTestFixtures.snapshot(historySnapshot);
        var failure = rejected(snapshot).failure();

        assertAll(
                () -> assertFalse(failure.toString().contains(secret)),
                () -> assertFalse(snapshot.toString().contains(secret)),
                () -> assertFalse(historySnapshot.toString().contains(secret)),
                () -> assertFalse(revisionSnapshot.toString().contains(secret)),
                () -> assertTrue(snapshot.toString().length() <= MagicSafetyCeilings.MAX_STRING_LENGTH),
                () -> assertTrue(historySnapshot.toString().length() <= MagicSafetyCeilings.MAX_STRING_LENGTH),
                () -> assertTrue(revisionSnapshot.toString().length() <= MagicSafetyCeilings.MAX_STRING_LENGTH),
                () -> assertTrue(Arrays.stream(failure.getClass().getRecordComponents())
                        .noneMatch(component -> component.getType() == String.class
                                || Throwable.class.isAssignableFrom(component.getType())
                                || SkillDocument.class.isAssignableFrom(component.getType())
                                || Dynamic.class.isAssignableFrom(component.getType()))));
    }

    @Test
    void exactlyPerOwnerHistoryCeilingSucceedsAndOneMoreIsRejected() {
        var owner = StoreTestFixtures.ownerId(20);
        var histories = histories(
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER,
                index -> owner,
                1);
        assertInstanceOf(SkillDefinitionStoreRestoreResult.Restored.class,
                SkillDefinitionStore.restore(new SkillDefinitionStoreSnapshot(histories)));

        histories.add(StoreTestFixtures.history(
                StoreTestFixtures.skillId(histories.size() + 1L), owner, 0));
        assertCapacity(
                new SkillDefinitionStoreSnapshot(histories),
                SkillStoreCapacityScope.OWNER_SKILL_HISTORIES,
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER + 1,
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER);
    }

    @Test
    void exactlyGlobalHistoryCeilingSucceedsAndOneMoreIsRejected() {
        var histories = histories(
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL,
                index -> StoreTestFixtures.ownerId(index + 1L),
                1);
        assertInstanceOf(SkillDefinitionStoreRestoreResult.Restored.class,
                SkillDefinitionStore.restore(new SkillDefinitionStoreSnapshot(histories)));

        histories.add(StoreTestFixtures.history(
                StoreTestFixtures.skillId(histories.size() + 1L),
                StoreTestFixtures.ownerId(histories.size() + 1L),
                0));
        assertCapacity(
                new SkillDefinitionStoreSnapshot(histories),
                SkillStoreCapacityScope.GLOBAL_SKILL_HISTORIES,
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL + 1,
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL);
    }

    @Test
    void exactlyPerSkillRevisionCeilingSucceedsAndOneMoreIsRejected() {
        var skillId = StoreTestFixtures.skillId(30);
        var revisions = revisions(skillId, MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL);
        var valid = new SkillDefinitionStoreSnapshot(List.of(new SkillHistorySnapshot(
                skillId, StoreTestFixtures.ownerId(30), revisions)));
        assertInstanceOf(SkillDefinitionStoreRestoreResult.Restored.class,
                SkillDefinitionStore.restore(valid));

        revisions.add(StoreTestFixtures.revisionSnapshot(
                skillId, MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL));
        assertCapacity(
                new SkillDefinitionStoreSnapshot(List.of(new SkillHistorySnapshot(
                        skillId, StoreTestFixtures.ownerId(30), revisions))),
                SkillStoreCapacityScope.SKILL_RETAINED_REVISIONS,
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL + 1,
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL);
    }

    @Test
    void exactlyGlobalRevisionCeilingSucceedsAndOneMoreIsRejected() {
        var histories = new ArrayList<SkillHistorySnapshot>();
        var revisionsPerHistory = MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL;
        var fullHistories = MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL / revisionsPerHistory;
        var firstOwner = StoreTestFixtures.ownerId(40);
        for (var index = 0; index < fullHistories; index++) {
            var skillId = StoreTestFixtures.skillId(index + 1L);
            histories.add(new SkillHistorySnapshot(
                    skillId,
                    firstOwner,
                    revisions(skillId, revisionsPerHistory)));
        }
        var valid = new SkillDefinitionStoreSnapshot(histories);
        assertInstanceOf(SkillDefinitionStoreRestoreResult.Restored.class,
                SkillDefinitionStore.restore(valid));

        var extraSkill = StoreTestFixtures.skillId(fullHistories + 1L);
        histories.add(StoreTestFixtures.history(extraSkill, StoreTestFixtures.ownerId(41), 0));
        assertCapacity(
                new SkillDefinitionStoreSnapshot(histories),
                SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS,
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL + 1,
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL);
    }

    @Test
    void crossingHistoryGlobalCapacityPrecedesItsDocumentCorruption() {
        var histories = new ArrayList<SkillHistorySnapshot>();
        var revisionsPerHistory = MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL;
        var fullHistories = MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL / revisionsPerHistory;
        var fullOwner = StoreTestFixtures.ownerId(42);
        for (var index = 0; index < fullHistories; index++) {
            var skillId = StoreTestFixtures.skillId(index + 1L);
            histories.add(new SkillHistorySnapshot(
                    skillId,
                    fullOwner,
                    revisions(skillId, revisionsPerHistory)));
        }

        var crossingSkill = StoreTestFixtures.skillId(fullHistories + 1L);
        var mismatchedDocument = StoreTestFixtures.document(StoreTestFixtures.skillId(9_999), 0);
        histories.add(new SkillHistorySnapshot(
                crossingSkill,
                StoreTestFixtures.ownerId(43),
                List.of(new SkillRevisionSnapshot(
                        StoreTestFixtures.revision(0), mismatchedDocument))));

        assertCapacity(
                new SkillDefinitionStoreSnapshot(histories),
                SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS,
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL + 1,
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL);
    }

    @Test
    void capacityFailureRequiresStrictlyExceededMetadata() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillDefinitionStoreRestoreFailure.CapacityExceeded(
                                SkillStoreCapacityScope.GLOBAL_SKILL_HISTORIES, 4_096, 4_096)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillDefinitionStoreRestoreFailure.CapacityExceeded(
                                SkillStoreCapacityScope.GLOBAL_SKILL_HISTORIES, -1, 4_096)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillDefinitionStoreRestoreFailure.CapacityExceeded(
                                SkillStoreCapacityScope.GLOBAL_SKILL_HISTORIES, 4_097, -1)));
    }

    @Test
    void schemaZeroHasNoRepresentableLowerSkillDocumentSchema() {
        var skillId = StoreTestFixtures.skillId(51);

        assertAll(
                () -> assertEquals(0, SkillDocument.CURRENT_SCHEMA_VERSION),
                () -> assertThrows(IllegalArgumentException.class, () -> StoreTestFixtures.document(
                        skillId,
                        0,
                        -1,
                        List.of(sharedNode()))));
    }

    @Test
    void nullSnapshotIsProgrammingMisuseAndRejectedResultHasNoPartialStore() {
        assertThrows(NullPointerException.class, () -> SkillDefinitionStore.restore(null));
        var skillId = StoreTestFixtures.skillId(50);
        var result = rejected(StoreTestFixtures.snapshot(
                new SkillHistorySnapshot(skillId, StoreTestFixtures.ownerId(50), List.of())));

        assertAll(
                () -> assertEquals(List.of("failure"), Arrays.stream(result.getClass().getRecordComponents())
                        .map(component -> component.getName())
                        .toList()),
                () -> assertFalse(Arrays.stream(result.getClass().getMethods())
                        .anyMatch(method -> method.getName().equals("store"))));
    }

    private static SkillDefinitionStoreRestoreResult.Rejected rejected(
            SkillDefinitionStoreSnapshot snapshot) {
        return assertInstanceOf(
                SkillDefinitionStoreRestoreResult.Rejected.class,
                SkillDefinitionStore.restore(snapshot));
    }

    private static void assertCapacity(
            SkillDefinitionStoreSnapshot snapshot,
            SkillStoreCapacityScope expectedScope,
            int expectedCurrent,
            int expectedMaximum) {
        var failure = assertInstanceOf(
                SkillDefinitionStoreRestoreFailure.CapacityExceeded.class,
                rejected(snapshot).failure());
        assertAll(
                () -> assertEquals(expectedScope, failure.scope()),
                () -> assertEquals(expectedCurrent, failure.current()),
                () -> assertEquals(expectedMaximum, failure.maximum()),
                () -> assertTrue(failure.current() > failure.maximum()));
    }

    private static ArrayList<SkillHistorySnapshot> histories(
            int count,
            java.util.function.IntFunction<com.yo1no.gramarye.magic.api.id.SkillOwnerId> owner,
            int revisionCount) {
        var histories = new ArrayList<SkillHistorySnapshot>(count + 1);
        for (var index = 0; index < count; index++) {
            var skillId = StoreTestFixtures.skillId(index + 1L);
            histories.add(new SkillHistorySnapshot(
                    skillId,
                    owner.apply(index),
                    revisions(skillId, revisionCount)));
        }
        return histories;
    }

    private static ArrayList<SkillRevisionSnapshot> revisions(SkillId skillId, int count) {
        var revisions = new ArrayList<SkillRevisionSnapshot>(count + 1);
        for (var revision = 0; revision < count; revision++) {
            revisions.add(StoreTestFixtures.revisionSnapshot(skillId, revision));
        }
        return revisions;
    }

    private static NodeDocument sharedNode() {
        return StoreTestFixtures.document(StoreTestFixtures.skillId(999), 0).nodes().getFirst();
    }

    private static NodeDocument nodeWithSecret(String secret) {
        var payload = new JsonObject();
        payload.addProperty("secret", secret);
        var trigger = new DefinitionEnvelope(
                ResourceLocation.fromNamespaceAndPath("test", "secret_trigger"),
                0,
                new Dynamic<>(JsonOps.INSTANCE, payload));
        var action = new DefinitionEnvelope(
                ResourceLocation.fromNamespaceAndPath("test", "action"),
                0,
                new Dynamic<>(JsonOps.INSTANCE, new JsonObject()));
        return new NodeDocument(trigger, action, AppearanceOverrideDocument.none());
    }
}
