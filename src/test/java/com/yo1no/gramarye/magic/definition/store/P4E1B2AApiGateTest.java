package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** Exact package, Store-observation, and later-phase absence gate for P4-E1-B2-A. */
final class P4E1B2AApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path STORE_ROOT = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye/magic/definition/store");

    @Test
    void exactB2AOwnersArePackagePrivateWithNoPublicConstruction() {
        for (var type : List.of(
                P4E1StoreHistoryObservation.class,
                P4E1GroupedStoreAudit.class,
                P4E1AuditedCapture.class)) {
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getSimpleName());
            assertFalse(Modifier.isProtected(type.getModifiers()), type.getSimpleName());
            assertTrue(Arrays.stream(type.getDeclaredConstructors())
                    .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                            || Modifier.isProtected(constructor.getModifiers())),
                    type.getSimpleName());
            assertTrue(Arrays.stream(type.getDeclaredFields())
                    .noneMatch(field -> Modifier.isStatic(field.getModifiers())),
                    type.getSimpleName());
        }
    }

    @Test
    void opaqueHistoryObservationHasOnlyTheReviewedClearableOperations()
            throws Exception {
        assertTrue(P4E1StoreHistoryObservation.class.isSealed());
        assertEquals(
                Set.of(
                        P4E1StoreHistoryObservation.Absent.class,
                        P4E1StoreHistoryObservation.Present.class),
                Set.of(P4E1StoreHistoryObservation.class.getPermittedSubclasses()));
        assertTrue(P4E1StoreHistoryObservation.Absent.class.isEnum());
        assertTrue(Modifier.isFinal(P4E1StoreHistoryObservation.Present.class.getModifiers()));

        var present = P4E1StoreHistoryObservation.Present.class;
        assertEquals(
                Set.of("ownerMatches", "contains", "discard", "requireActive"),
                Arrays.stream(present.getDeclaredMethods())
                        .map(method -> method.getName())
                        .collect(Collectors.toSet()));
        var ownerMatches = present.getDeclaredMethod("ownerMatches", SkillOwnerId.class);
        var contains = present.getDeclaredMethod("contains", SkillReference.class);
        var discard = present.getDeclaredMethod("discard");
        assertEquals(boolean.class, ownerMatches.getReturnType());
        assertEquals(boolean.class, contains.getReturnType());
        assertEquals(void.class, discard.getReturnType());
        for (var operation : List.of(ownerMatches, contains, discard)) {
            assertFalse(Modifier.isPublic(operation.getModifiers()));
            assertFalse(Modifier.isProtected(operation.getModifiers()));
            assertFalse(Modifier.isPrivate(operation.getModifiers()));
        }
        assertEquals(Set.of("skillId", "history"),
                Arrays.stream(present.getDeclaredFields())
                        .map(field -> field.getName())
                        .collect(Collectors.toSet()));
        assertTrue(Arrays.stream(present.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && !Modifier.isStatic(field.getModifiers())));

        var primitive = SkillDefinitionStore.class.getDeclaredMethod(
                "observeExactHistoryForRootAudit", SkillId.class);
        assertEquals(P4E1StoreHistoryObservation.class, primitive.getReturnType());
        assertFalse(Modifier.isPublic(primitive.getModifiers()));
        assertFalse(Modifier.isProtected(primitive.getModifiers()));
        assertFalse(Modifier.isPrivate(primitive.getModifiers()));
        assertEquals(1, Arrays.stream(SkillDefinitionStore.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(
                        "observeExactHistoryForRootAudit"))
                .count());

        var lookup = SkillDefinitionStore.P4E1HistoryLookup.class;
        assertTrue(lookup.isInterface());
        assertFalse(Modifier.isPublic(lookup.getModifiers()));
        assertFalse(Modifier.isProtected(lookup.getModifiers()));
        assertEquals(Set.of("observe"), Arrays.stream(lookup.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet()));
        var observe = lookup.getDeclaredMethod("observe", SkillId.class);
        assertEquals(P4E1StoreHistoryObservation.class, observe.getReturnType());

        var injectedAudit = SkillDefinitionStore.class.getDeclaredMethod(
                "auditJournalTargets",
                PendingAttachmentJournal.class,
                SkillDefinitionStore.P4E1HistoryLookup.class);
        assertEquals(JournalTargetAuditResult.class, injectedAudit.getReturnType());
        assertFalse(Modifier.isPublic(injectedAudit.getModifiers()));
        assertFalse(Modifier.isProtected(injectedAudit.getModifiers()));
        assertFalse(Modifier.isPrivate(injectedAudit.getModifiers()));
    }

    @Test
    void b2AOwnersExposeNoPublicRawOrStoreTruthSignature() {
        for (var type : List.of(
                P4E1GroupedStoreAudit.class,
                P4E1AuditedCapture.class,
                P4E1StoreHistoryObservation.Present.class)) {
            assertTrue(Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers())
                            || Modifier.isProtected(method.getModifiers()))
                    .flatMap(method -> java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(method.getReturnType()),
                            Arrays.stream(method.getParameterTypes())))
                    .noneMatch(P4E1B2AApiGateTest::isForbiddenSurfaceType),
                    type.getSimpleName());
        }
    }

    @Test
    void b2BAndMutationSurfacesRemainAbsent() throws Exception {
        var audited = Files.readString(STORE_ROOT.resolve("P4E1AuditedCapture.java"));
        assertFalse(audited.contains("P4E1StoreHistoryObservation"));
        assertFalse(audited.contains("StoredSkillHistory"));
        assertFalse(audited.contains("new P4E1RawClaimBuffer"));
        assertFalse(audited.contains("new ArrayList"));
        assertTrue(audited.contains(
                "this.claims = Objects.requireNonNull(claims, \"claims\")"));
        var b2a = Files.readString(STORE_ROOT.resolve("P4E1StoreHistoryObservation.java"))
                + Files.readString(STORE_ROOT.resolve("P4E1GroupedStoreAudit.java"))
                + audited;
        for (var forbidden : List.of(
                "SkillRetentionRootAuditService",
                "SkillRetentionRootAuditResult",
                "P4E1RootIndex",
                "P4E1RootHandoff",
                "P4E1Complete",
                "SkillRetentionRootSnapshot",
                "List<SkillReference>",
                "ArrayList<SkillReference>",
                ".pin(",
                ".snapshot(",
                ".reclaim(",
                ".setDirty(",
                ".setData(",
                "Files.write",
                "Files.move",
                "Files.delete",
                "Files.newOutputStream",
                "ServerStartingEvent",
                "ServerStoppedEvent",
                "Executor",
                "Future",
                "new Thread(",
                "parallelStream(",
                "CustomPacketPayload",
                "exceptionClassName.length() > 160",
                "@SuppressWarnings",
                "java.lang.reflect",
                "sun.misc.Unsafe",
                "catch (Error",
                "catch (OutOfMemoryError",
                "catch (Throwable")) {
            assertFalse(b2a.contains(forbidden), forbidden);
        }
        for (var forbiddenFile : List.of(
                "SkillRetentionRootAuditService.java",
                "P4E1RootIndex.java",
                "P4E1RootHandoff.java",
                "P4E1Complete.java")) {
            assertFalse(Files.exists(STORE_ROOT.resolve(forbiddenFile)), forbiddenFile);
        }
    }

    @Test
    void errorCleanupUsesNonAllocatingChainsAndIndexLoops() throws Exception {
        var grouped = Files.readString(STORE_ROOT.resolve("P4E1GroupedStoreAudit.java"));
        var audited = Files.readString(STORE_ROOT.resolve("P4E1AuditedCapture.java"));
        var store = Files.readString(STORE_ROOT.resolve("SkillDefinitionStore.java"));
        var capture = Files.readString(STORE_ROOT.resolve("P4E1GlobalSourceCapture.java"));
        var raw = Files.readString(STORE_ROOT.resolve("P4E1RawClaimBuffer.java"));

        assertFalse(grouped.contains("distinct.entrySet()"));
        assertFalse(grouped.contains("distinct.values()"));
        assertTrue(grouped.contains("while (slot != null)"));
        assertFalse(store.contains(
                "observed.values().forEach(SkillDefinitionStore::discardHistoryObservation)"));
        assertTrue(store.contains("while (slot != null)"));
        var unpublishedCleanup = capture.substring(
                capture.indexOf("static void cleanupUnpublished("),
                capture.indexOf("private record OnlineIdentity("));
        assertFalse(unpublishedCleanup.contains("for (var source : sources)"));
        assertTrue(unpublishedCleanup.contains(
                "for (var index = 0; index < sources.size(); index++)"));
        assertFalse(raw.contains("for (var segment : segments)"));
        assertTrue(grouped.contains(
                "pendingAudited.discardAfterResultPublicationFailure()"));
        var failedPublicationCleanup = audited.substring(
                audited.indexOf("void discardAfterResultPublicationFailure()"),
                audited.indexOf("private void requireExactBinding("));
        assertFalse(failedPublicationCleanup.contains("moveReferences()"));
        assertFalse(failedPublicationCleanup.contains("new "));
        try (var files = Files.list(STORE_ROOT)) {
            assertEquals(
                    Set.of("P4E1AuditedCapture.java", "P4E1GroupedStoreAudit.java"),
                    files.filter(path -> path.getFileName().toString().endsWith(".java"))
                            .filter(path -> {
                                try {
                                    return Files.readString(path).contains(
                                            "discardAfterResultPublicationFailure(");
                                } catch (java.io.IOException exception) {
                                    throw new java.io.UncheckedIOException(exception);
                                }
                            })
                            .map(path -> path.getFileName().toString())
                            .collect(Collectors.toSet()));
        }
    }

    private static boolean isForbiddenSurfaceType(Class<?> type) {
        return type == byte[].class
                || Tag.class.isAssignableFrom(type)
                || Path.class.isAssignableFrom(type)
                || java.io.File.class.isAssignableFrom(type)
                || java.util.Collection.class.isAssignableFrom(type)
                || Iterable.class.isAssignableFrom(type)
                || SkillDefinitionStore.class.isAssignableFrom(type)
                || StoredSkillHistory.class.isAssignableFrom(type)
                || type.getSimpleName().contains("Carrier")
                || type.getSimpleName().contains("SavedData")
                || type.getSimpleName().contains("JournalTargetAuditProof");
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
}
