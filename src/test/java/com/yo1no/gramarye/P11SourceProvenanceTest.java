package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Direct capability/source boundaries; these assertions do not substitute for native load tests. */
final class P11SourceProvenanceTest {
    @Test
    void nativeOnlyLifetimeHasClosedNonNullScopeWithoutGrantingPersistedMaterial() {
        var lifetime = new P11SourceProvenance.Lifetime();
        var first = lifetime.legacyObservation();
        var next = lifetime.legacyObservation();
        assertTrue(first.sameScope(next));
        assertTrue(next.sameScope(first));
        assertEquals(P11SourceProvenance.Kind.LEGACY_NATIVE, first.kind());
        assertEquals(0, first.sourceEpoch());
        assertEquals(0, first.sourceVersion());
        assertTrue(first.persistedStates().isEmpty());
        assertFalse(first.sameScope(new P11SourceProvenance.Lifetime().legacyObservation()));
    }

    @Test
    void everyLifetimeBoundaryInvalidatesPreviouslyObservedNativeOnlyScope() {
        var lifetime = new P11SourceProvenance.Lifetime();
        var beforeStart = lifetime.legacyObservation();
        lifetime.rotate();
        var duringFirstServer = lifetime.legacyObservation();
        assertFalse(beforeStart.sameScope(duringFirstServer));
        assertTrue(duringFirstServer.sameScope(lifetime.legacyObservation()));
        lifetime.rotate();
        var afterStop = lifetime.legacyObservation();
        assertFalse(duringFirstServer.sameScope(afterStop));
        assertFalse(beforeStart.sameScope(afterStop));
        lifetime.rotate();
        var nextServer = lifetime.legacyObservation();
        assertFalse(duringFirstServer.sameScope(nextServer));
        assertTrue(nextServer.sameScope(lifetime.legacyObservation()));
    }

    @Test
    void absentOrNullDomainNeverBecomesValidByMatchingAnotherUnknownObservation() {
        var lifetime = new P11SourceProvenance.Lifetime();
        var unknown = lifetime.unknownObservation();
        var legacy = lifetime.legacyObservation();
        assertEquals(P11SourceProvenance.Kind.UNKNOWN, unknown.kind());
        assertFalse(unknown.sameScope(unknown));
        assertFalse(unknown.sameScope(new P11SourceProvenance.Lifetime().unknownObservation()));
        assertFalse(unknown.sameScope(legacy));
        assertFalse(legacy.sameScope(unknown));
        assertFalse(legacy.sameScope(null));
        assertTrue(unknown.persistedStates().isEmpty());
    }

    @Test
    void bothManagedAndDisabledNativeLifecyclesRotateTheSameClosedScope() throws Exception {
        var source = Files.readString(root().resolve(
                "src/main/java/com/yo1no/gramarye/P11SourceProvenance.java"));
        for (var bounds : new String[][] {
                {"void started(", "void inactiveBoundary("},
                {"void inactiveBoundary(", "void publicationObserver("},
                {"void stopped(", "public Observation observe("}}) {
            var method = source.substring(source.indexOf(bounds[0]), source.indexOf(bounds[1]));
            assertTrue(method.contains("lifetime.rotate()"));
        }
        assertFalse(source.contains("Observation.LEGACY"));
        var rootSource = Files.readString(root().resolve(
                "src/main/java/com/yo1no/gramarye/P11FoundationService.java"));
        var start = rootSource.substring(rootSource.indexOf("void started("),
                rootSource.indexOf("Optional<P11StartupLoadState> startupState("));
        var stop = rootSource.substring(rootSource.indexOf("private void stopExact("));
        assertTrue(start.contains("} else {\n            provenance.inactiveBoundary();"));
        assertTrue(stop.contains("} else {\n            provenance.inactiveBoundary();"));
    }

    @Test
    void oldConfigFinalReadbackObservesTheActualRootChainWithoutAnUnboundSecondService()
            throws Exception {
        var observation = Files.readString(root().resolve(
                "src/main/java/com/yo1no/gramarye/P4E2RecoveryGameTestObservation.java"));
        for (var forbidden : Set.of("P11SourceProvenance", "JournalClearProof",
                "new PlayerSkillAttachmentService", "new P4E2QualificationFacade",
                "registerOn(", ".post(", ".setData(", ".prepareJournalPrefixClear(")) {
            assertFalse(observation.contains(forbidden), forbidden);
        }
        assertTrue(observation.contains("getCustomExtension(P4E2QualificationFacade.class)"));
        assertTrue(observation.contains("handle.facade.consume(handle.session)"));
        for (var required : Set.of("snapshot.entriesCleared() != 2",
                "snapshot.stepsReplayed() != 0", "snapshot.continuationCalls() != 1",
                "snapshot.setDataAttempts() != 0", "snapshot.setDataSuccesses() != 0",
                "P4E2QualificationFacade.ReconciliationVariant.RECOVERY_CHANGED")) {
            assertTrue(observation.contains(required), required);
        }
        var gameTest = Files.readString(root().resolve(
                "src/main/java/com/yo1no/gramarye/magic/definition/store/"
                        + "SkillSubmissionRecoveryGameTests.java"));
        var arm = gameTest.indexOf("P4E2RecoveryGameTestObservation.armFinalReadback(");
        var nativeReload = gameTest.indexOf("reloaded = placePlayer(", arm);
        var consume = gameTest.indexOf("P4E2RecoveryGameTestObservation.assertFinalReadback(");
        assertTrue(arm >= 0 && nativeReload > arm && consume > nativeReload);
        assertFalse(gameTest.contains("assertRecoveryChangedHandoffOnce"));
        assertFalse(gameTest.contains("bus.post("));
    }

    @Test
    void onlyTheInjectedP4ObservationAndPublicationSurfaceIsPublic() {
        assertTrue(Arrays.stream(P11SourceProvenance.class.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                        || Modifier.isProtected(constructor.getModifiers())));
        assertEquals(Set.of("observe", "published"),
                Arrays.stream(P11SourceProvenance.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .map(method -> method.getName()).collect(Collectors.toSet()));
        for (var type : new Class<?>[] {
                P11SourceProvenance.SelectedInput.class,
                P11SourceProvenance.Lineage.class,
                P11SourceProvenance.Observation.class,
                PlayerSkillAttachmentService.JournalClearProof.class,
                PlayerSkillAttachmentService.P11AttachmentReadResult.class,
                PlayerSkillAttachmentService.P11AttachmentWriteResult.class,
                PlayerSkillAttachmentService.P11AttachmentSnapshot.class}) {
            assertTrue(Arrays.stream(type.getDeclaredConstructors())
                    .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())),
                    type.getSimpleName());
            assertTrue(Arrays.stream(type.getDeclaredFields())
                    .allMatch(field -> Modifier.isPrivate(field.getModifiers())),
                    type.getSimpleName());
        }
    }

    @Test
    void sealedLineageHasNoNativeGraphAndCannotBeForgedFromPublicScalarInputs() {
        for (var field : P11SourceProvenance.Lineage.class.getDeclaredFields()) {
            assertFalse(field.getType().getName().startsWith("net.minecraft."));
            assertFalse(field.getType() == P11SourceProvenance.class);
            assertFalse(field.getType() == Object.class);
        }
        for (var method : P11SourceProvenance.class.getDeclaredMethods()) {
            if (Set.of("manage", "persistedInput", "absentInput", "volatileInput",
                    "beginSelectedLoad", "readCompleted", "recordSelectedLoad",
                    "captureLineage", "invalidate", "release")
                    .contains(method.getName())) {
                assertFalse(Modifier.isPublic(method.getModifiers()));
                assertFalse(Modifier.isProtected(method.getModifiers()));
            }
        }
    }

    @Test
    void unknownInvalidationRetainsManagedResponsibilityAndAllPublicationsUseTheSoleSetter()
            throws Exception {
        var source = Files.readString(root().resolve(
                "src/main/java/com/yo1no/gramarye/P11SourceProvenance.java"));
        var invalidate = source.substring(source.indexOf("void invalidate("),
                source.indexOf("void release("));
        assertTrue(invalidate.contains("entry.current = null"));
        assertFalse(invalidate.contains("managed.remove"));
        assertFalse(invalidate.contains("managed.clear"));
        var service = Files.readString(root().resolve(
                "src/main/java/com/yo1no/gramarye/magic/definition/player/"
                        + "PlayerSkillAttachmentService.java"));
        var setter = service.indexOf("player.setData(type, replacement)");
        assertTrue(service.lastIndexOf("P11_ATTACHMENT_SOURCE_UNKNOWN", setter) >= 0);
        assertTrue(service.indexOf("sourceProvenance.published(player, before,", setter) > setter);
    }

    @Test
    void e2ManagedUnknownIsRejectedBeforeOpeningItsExistingProjection() throws Exception {
        var source = Files.readString(root().resolve(
                "src/main/java/com/yo1no/gramarye/magic/definition/store/"
                        + "P4E2OnlineReconciliationCoordinator.java"));
        var guard = source.indexOf("attachmentService.observeRecoveryProvenance(player)");
        var projection = source.indexOf("attachmentService.observeOnlineForReconciliation(player)");
        assertTrue(guard >= 0 && projection > guard);
        assertTrue(source.substring(guard, projection).contains("FailureReason.FRESHNESS_LOST"));
    }

    @Test
    void loadCallerAndWholeMaterialGatesRecheckExactCurrentProvenance() throws Exception {
        var source = Files.readString(root().resolve(
                "src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java"));
        for (var bounds : new String[][] {
                {"void loadReturned(", "void callerComplete("},
                {"void callerComplete(", "void fail("},
                {"boolean canCopy(", "private boolean currentAttachmentSource("}}) {
            var method = source.substring(source.indexOf(bounds[0]), source.indexOf(bounds[1]));
            assertTrue(method.contains("currentAttachmentSource(body)"));
        }
        var current = source.substring(source.indexOf("private boolean currentAttachmentSource("),
                source.indexOf("P11ReceiptLedger.PhysicalWriterReceipt beginWriter("));
        assertTrue(current.contains("attachments.captureP11Source(body.actor, provenance)"));
        assertTrue(current.contains("current.kind() == P11SourceProvenance.Kind.CURRENT"));
        assertTrue(current.contains("current.sourceEpoch() == body.source.epoch()"));
        assertTrue(current.contains("current.sourceVersion() == body.source.version()"));
        assertTrue(current.contains("body.mana.isCurrent(body.actor)"));
        assertFalse(current.contains("getPlayerList"));
        assertFalse(current.contains("LoginReady"));
    }

    @Test
    void missingCopyLineageRejectsBeforeNativeRespawnAndCannotReturnWithoutRestore() throws Exception {
        var source = Files.readString(root().resolve(
                "src/main/java/com/yo1no/gramarye/P11NativeStorageBoundary.java"));
        var respawn = source.substring(source.indexOf("public static ServerPlayer respawn("),
                source.indexOf("public static void copy("));
        assertTrue(respawn.indexOf("source.lineage(body).orElseThrow(")
                < respawn.indexOf("var result = original.call("));
        var copy = source.substring(source.indexOf("if (scope.lineage == null)"),
                source.indexOf("var input = root.provenance().volatileInput(scope.lineage)"));
        assertTrue(copy.contains("throw unavailable()"));
        assertFalse(copy.contains("return;"));
    }

    @Test
    void nestedAttachmentSerializationHasHolderScopedClosedOutputReceipts() throws Exception {
        var source = Files.readString(root().resolve(
                "src/main/java/com/yo1no/gramarye/P11NativeStorageBoundary.java"));
        var write = source.substring(source.indexOf("public static CompoundTag writeAttachments("),
                source.indexOf("public static void brainEncoded("));
        assertTrue(write.contains("var previous = scope.attachments"));
        assertTrue(write.contains("new AttachmentWriteScope(holder)"));
        assertTrue(write.contains("finally { scope.attachments = previous; }"));
        assertTrue(write.contains("observed.skills.matches(scope.body.actor, result.get(SKILLS.toString()))"));
        assertTrue(write.contains("observed.mana.matches(scope.body.actor, result.get(MANA.toString()))"));
        assertFalse(source.contains("skillsWritten = true"));
        assertFalse(source.contains("manaWritten = true"));
        var read = source.substring(source.indexOf("public static void manaReadCompleted("),
                source.indexOf("public static void manaPublished("));
        assertTrue(read.contains("!scope.manaReadObserved"));
        assertTrue(read.contains("!copy.manaReadObserved"));
        var owner = Files.readString(root().resolve(
                "src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java"));
        var publication = owner.substring(owner.indexOf("void manaPublication("),
                owner.indexOf("P11ReceiptLedger.PhysicalWriterReceipt beginWriter("));
        assertTrue(publication.contains("publication.follows(body.mana, body.actor)"));
        var mana = Files.readString(root().resolve(
                "src/main/java/com/yo1no/gramarye/magic/runtime/mana/P11ManaMaterial.java"));
        assertTrue(mana.contains("before.sameState(expected)"));
        assertTrue(mana.contains("after.isCurrent(actor)"));
    }

    private static Path root() {
        var candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("project root not found");
    }
}
