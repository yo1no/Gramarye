package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** Exact package, public-surface, and owner-bound handoff gate for the P4-E1-B1 capture. */
final class P4E1B1ApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path STORE_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store");
    private static final Path PLAYER_SERVICE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java");
    private static final Path PLAYER_OBSERVATION = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSourceObservation.java");

    @Test
    void exactB1OwnersArePackagePrivateAndInventoryIsClosed() {
        var owners = List.of(
                P4E1RootSourceFamily.class,
                P4E1SourceInventory.class,
                P4E1GlobalSourceCapture.class,
                P4E1RawClaimBuffer.class,
                P4E1PendingJournalObservation.class);
        assertTrue(owners.stream().noneMatch(type -> Modifier.isPublic(type.getModifiers())));
        assertEquals(
                List.of("PLAYER_SKILL_ATTACHMENT", "PENDING_ATTACHMENT_JOURNAL"),
                Arrays.stream(P4E1RootSourceFamily.values()).map(Enum::name).toList());
        assertEquals(
                Set.of("Captured", "Incomplete", "OverLimit"),
                Arrays.stream(P4E1GlobalSourceCapture.CaptureResult.class
                                .getDeclaredClasses())
                        .map(Class::getSimpleName)
                        .collect(Collectors.toSet()));
    }

    @Test
    void onlineSurfaceIsExactOpaqueAndContainsNoRootCollection() {
        var reviewedNames = Set.of(
                "observeOnlineForRootAudit",
                "maximumRootAuditAttachmentEncodedBytes",
                "onlineRootState",
                "onlineRootUnavailableReason",
                "onlineRootCount",
                "drainOnlineRootProjection",
                "discardOnlineRootProjection",
                "isOnlineRootWitnessCurrent",
                "discardOnlineRootWitness",
                "discardOnlineRootAuditHandle");
        var reviewed = Arrays.stream(PlayerSkillAttachmentService.class.getDeclaredMethods())
                .filter(method -> reviewedNames.contains(method.getName()))
                .toList();
        assertEquals(reviewedNames,
                reviewed.stream().map(method -> method.getName()).collect(Collectors.toSet()));
        assertEquals(reviewedNames.size(), reviewed.size());
        assertTrue(reviewed.stream().allMatch(method -> Modifier.isPublic(method.getModifiers())));
        assertTrue(reviewed.stream()
                .flatMap(method -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(method.getReturnType()),
                        Arrays.stream(method.getParameterTypes())))
                .noneMatch(type -> Collection.class.isAssignableFrom(type)
                        || Iterable.class.isAssignableFrom(type)
                        || Tag.class.isAssignableFrom(type)));

        var handle = PlayerSkillAttachmentService.OnlineRootAuditHandle.class;
        assertTrue(Modifier.isPublic(handle.getModifiers()));
        assertTrue(Modifier.isFinal(handle.getModifiers()));
        assertTrue(Arrays.stream(handle.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
        assertEquals(0, Arrays.stream(handle.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())
                        || Modifier.isProtected(method.getModifiers()))
                .count());
        assertTrue(Arrays.stream(handle.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())));
    }

    @Test
    void staticBoundaryHasNoSecondCeilingRawLeakOrLaterPhaseOwner() throws Exception {
        var b1 = Files.readString(STORE_ROOT.resolve("P4E1GlobalSourceCapture.java"))
                + Files.readString(STORE_ROOT.resolve("P4E1RawClaimBuffer.java"))
                + Files.readString(STORE_ROOT.resolve("P4E1SourceInventory.java"))
                + Files.readString(STORE_ROOT.resolve("P4E1PendingJournalObservation.java"));
        var service = Files.readString(PLAYER_SERVICE);
        assertTrue(b1.contains("MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM")
                || Files.readString(STORE_ROOT.resolve("P4E1AuditBudget.java"))
                        .contains("MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM"));
        for (var forbidden : List.of(
                "65_536",
                "Files.readAllBytes",
                "java.util.zip.GZIPInputStream",
                "NbtAccounter.unlimitedHeap",
                "@SuppressWarnings",
                "java.lang.reflect",
                "sun.misc.Unsafe",
                "Executor",
                "Future",
                "parallelStream(",
                "SkillRetentionRootSnapshot",
                ".reclaim(",
                "ServerStartingEvent",
                "PlayerEvent",
                "CustomPacketPayload",
                "P4E1B2BApiGateTest",
                "SkillRetentionRootAuditResult",
                "ReconciliationRequired",
                "CompleteResult",
                "RootIndex")) {
            assertFalse(b1.contains(forbidden), forbidden);
        }
        assertFalse(service.contains("List<OnlineRoot"));
        assertFalse(service.contains("Iterable<OnlineRoot"));
    }

    @Test
    void checkpointOrderAndOnlineRootCleanupRemainExplicit() throws Exception {
        var capture = Files.readString(STORE_ROOT.resolve("P4E1GlobalSourceCapture.java"));
        assertOrdered(
                capture,
                "P4E1SourceAdmissionPreflight.evaluate()",
                "observeP4E1StoreReady(server)",
                "observeP4E1Journal(server, storeWitness)",
                "P4E1SourceInventory.capture(attachmentService, journal)",
                "P4E1PlayerDataDirectorySnapshot.capture(",
                "captureOnlineIdentities(",
                "P4E1IntegratedSnapshotTraversal.captureForGlobal(server, budget)",
                "arbitrate(directory, integrated, online)");
        assertOrdered(
                capture,
                "for (var selectedSource : selected.values())",
                "processPlayerSource(context, selectedSource)",
                "directory.verifyUnchanged()",
                "processJournal(context)");

        var observation = Files.readString(PLAYER_OBSERVATION);
        assertTrue(observation.contains("finally {\n                ready = null;"));
        assertTrue(observation.contains("void discardRoots()"));
        assertTrue(observation.contains("void requireCurrentThread()"));
        assertTrue(Files.readString(PLAYER_SERVICE)
                .contains("handle.observation.requireCurrentThread()"));
    }

    @Test
    void sourceInventorySwitchIsExhaustiveAndCaptureClearsHeavyReferences() throws Exception {
        var inventory = Files.readString(STORE_ROOT.resolve("P4E1SourceInventory.java"));
        var capture = Files.readString(STORE_ROOT.resolve("P4E1GlobalSourceCapture.java"));
        assertTrue(inventory.contains("EnumSet.allOf(P4E1RootSourceFamily.class)"));
        assertTrue(inventory.contains("case PLAYER_SKILL_ATTACHMENT ->"));
        assertTrue(inventory.contains("case PENDING_ATTACHMENT_JOURNAL ->"));
        assertFalse(inventory.contains("default ->"));
        for (var cleared : List.of(
                "ownerIdentity = null",
                "serverIdentity = null",
                "creationThreadIdentity = null",
                "storeWitness = null",
                "journalWitness = null",
                "inventoryWitness = null",
                "directoryWitness = null",
                "integratedWitness = null",
                "claims = null",
                "sources = null",
                "summary = null")) {
            assertTrue(capture.contains(cleared), cleared);
        }
        assertTrue(capture.contains("claims.discard()"));
        assertTrue(capture.contains("sources.clear()"));
    }

    @Test
    void captureAndClaimedHandlesHaveNoPublicConstructionOrStaticRetention() {
        for (var type : List.of(
                P4E1GlobalSourceCapture.Captured.class,
                P4E1GlobalSourceCapture.Claimed.class)) {
            assertFalse(Modifier.isPublic(type.getModifiers()));
            assertTrue(Arrays.stream(type.getDeclaredConstructors())
                    .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                            || Modifier.isProtected(constructor.getModifiers())));
            assertTrue(Arrays.stream(type.getDeclaredFields())
                    .noneMatch(field -> Modifier.isStatic(field.getModifiers())));
        }
        assertFalse(Arrays.stream(P4E1GlobalSourceCapture.class.getDeclaredFields())
                .anyMatch(field -> Modifier.isStatic(field.getModifiers())));
    }

    @Test
    void capturedHandoffIsBoundOnlyToTheExactGroupedAuditOwner() {
        var captures = Arrays.stream(P4E1GlobalSourceCapture.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("capture"))
                .toList();
        assertEquals(1, captures.size());
        assertEquals(
                List.of(
                        net.minecraft.server.MinecraftServer.class,
                        SkillDefinitionStoreService.class,
                        PlayerSkillAttachmentService.class,
                        P4E1GroupedStoreAudit.class),
                Arrays.asList(captures.getFirst().getParameterTypes()));

        var claims = Arrays.stream(P4E1GlobalSourceCapture.Captured.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("claim"))
                .toList();
        assertEquals(1, claims.size());
        var claim = claims.getFirst();
        assertEquals(List.of(P4E1GroupedStoreAudit.class),
                Arrays.asList(claim.getParameterTypes()));
        assertEquals(P4E1GlobalSourceCapture.Claimed.class, claim.getReturnType());
        assertFalse(Modifier.isPublic(claim.getModifiers()));
        assertFalse(Modifier.isProtected(claim.getModifiers()));
        assertFalse(Modifier.isPrivate(claim.getModifiers()));
        assertFalse(Arrays.stream(P4E1GlobalSourceCapture.Captured.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("claim")
                        && method.getParameterCount() == 0));
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("project root unavailable");
        }
        return current;
    }

    private static void assertOrdered(String source, String... tokens) {
        var previous = -1;
        for (var token : tokens) {
            var next = source.indexOf(token, previous + 1);
            assertTrue(next > previous, token);
            previous = next;
        }
    }
}
