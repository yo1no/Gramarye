package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.io.File;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** Exact visibility, result, index-owner, and zero-side-effect Gate for P4-E1-B2-B. */
final class P4E1B2BApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path STORE_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store");
    private static final Path MANA_GAME_TEST_SOURCE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/runtime/mana/ManaLifecycleGameTests.java");

    @Test
    void exactTopLevelsHaveOnlyTheReviewedVisibilityAndConstruction() {
        assertTrue(Modifier.isPublic(SkillRetentionRootAuditResult.class.getModifiers()));
        assertTrue(SkillRetentionRootAuditResult.class.isSealed());
        assertEquals(
                Set.of(
                        "Complete",
                        "Incomplete",
                        "OverLimit",
                        "ReconciliationRequired",
                        "AuditSummary",
                        "Diagnostic",
                        "IncompleteReason",
                        "ReconciliationReason",
                        "Disposition",
                        "Counter",
                        "Stage"),
                Arrays.stream(SkillRetentionRootAuditResult.class.getDeclaredClasses())
                        .filter(type -> Modifier.isPublic(type.getModifiers())
                                || Modifier.isProtected(type.getModifiers()))
                        .map(Class::getSimpleName)
                        .collect(Collectors.toSet()));
        assertEquals(
                Set.of(
                        SkillRetentionRootAuditResult.Complete.class,
                        SkillRetentionRootAuditResult.Incomplete.class,
                        SkillRetentionRootAuditResult.OverLimit.class,
                        SkillRetentionRootAuditResult.ReconciliationRequired.class),
                Set.of(SkillRetentionRootAuditResult.class.getPermittedSubclasses()));
        assertTrue(Arrays.stream(SkillRetentionRootAuditResult.class.getPermittedSubclasses())
                .allMatch(type -> Modifier.isPublic(type.getModifiers())
                        && Modifier.isFinal(type.getModifiers())));

        for (var type : List.of(
                SkillRetentionRootAuditService.class,
                P4E1FinalFreshness.class,
                P4E1CompleteRootHandoff.class)) {
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getSimpleName());
            assertFalse(Modifier.isProtected(type.getModifiers()), type.getSimpleName());
            assertTrue(Modifier.isFinal(type.getModifiers()), type.getSimpleName());
        }
        for (var constructor : SkillRetentionRootAuditService.class.getDeclaredConstructors()) {
            assertPackagePrivate(constructor.getModifiers(), "audit service constructor");
        }
        assertTrue(Arrays.stream(SkillRetentionRootAuditService.class.getDeclaredMethods())
                .noneMatch(method -> Modifier.isPublic(method.getModifiers())
                        || Modifier.isProtected(method.getModifiers())));
        for (var constructor : P4E1CompleteRootHandoff.class.getDeclaredConstructors()) {
            assertPackagePrivate(constructor.getModifiers(), "complete handoff constructor");
        }
        assertEquals(
                Set.of(Iterable.class, AutoCloseable.class),
                Set.of(P4E1CompleteRootHandoff.class.getInterfaces()));

        var complete = SkillRetentionRootAuditResult.Complete.class;
        assertTrue(Modifier.isPublic(complete.getModifiers()));
        assertTrue(Modifier.isFinal(complete.getModifiers()));
        assertFalse(complete.isRecord());
        assertTrue(Arrays.stream(complete.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
        assertEquals(
                Set.of("toString"),
                Arrays.stream(complete.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))
                        .map(method -> method.getName())
                        .collect(Collectors.toSet()));
        assertEquals(
                Set.of("summary"),
                Arrays.stream(SkillRetentionRootAuditResult.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))
                        .map(method -> method.getName())
                        .collect(Collectors.toSet()));
    }

    @Test
    void publicResultSurfaceIsBoundedAndContainsNoRootOrInternalCapability() {
        assertTrue(SkillRetentionRootAuditResult.AuditSummary.class.isRecord());
        assertEquals(
                List.of(
                        "indexGeneration",
                        "selectedOwnerCount",
                        "onlineOwnerCount",
                        "integratedOwnerCount",
                        "diskOwnerCount",
                        "playerRootClaimCount",
                        "journalRootClaimCount",
                        "totalRawRootClaimCount",
                        "distinctSkillIdCount",
                        "auditedValidClaimCount",
                        "sourceCount"),
                Arrays.stream(SkillRetentionRootAuditResult.AuditSummary.class
                                .getRecordComponents())
                        .map(component -> component.getName())
                        .toList());

        for (var type : publicResultTypes()) {
            assertBoundedPublicType(type.getGenericSuperclass(), type.getName());
            for (var contract : type.getGenericInterfaces()) {
                assertBoundedPublicType(contract, type.getName());
            }
            for (var method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())
                        && !Modifier.isProtected(method.getModifiers())) {
                    continue;
                }
                assertBoundedPublicType(method.getGenericReturnType(), method.toString());
                for (var parameter : method.getGenericParameterTypes()) {
                    assertBoundedPublicType(parameter, method.toString());
                }
            }
            for (var constructor : type.getDeclaredConstructors()) {
                if (!Modifier.isPublic(constructor.getModifiers())
                        && !Modifier.isProtected(constructor.getModifiers())) {
                    continue;
                }
                for (var parameter : constructor.getGenericParameterTypes()) {
                    assertBoundedPublicType(parameter, constructor.toString());
                }
            }
            for (var field : type.getDeclaredFields()) {
                if (Modifier.isPublic(field.getModifiers())
                        || Modifier.isProtected(field.getModifiers())) {
                    assertBoundedPublicType(field.getGenericType(), field.toString());
                }
            }
        }

        assertEquals(0L, Arrays.stream(SkillRetentionRootAuditResult.Complete.class.getMethods())
                .filter(method -> isRootCarrier(method.getReturnType()))
                .count());
        assertFalse(Iterable.class.isAssignableFrom(
                SkillRetentionRootAuditResult.Complete.class));
        assertFalse(java.io.Serializable.class.isAssignableFrom(
                SkillRetentionRootAuditResult.class));
        assertTrue(publicResultTypes().stream()
                .filter(type -> !type.isEnum())
                .noneMatch(java.io.Serializable.class::isAssignableFrom));
    }

    @Test
    void publicDiagnosticsPreserveUnknownAsEmptyAndEnforceTheExistingClassNameBound() {
        var generationOnly = SkillRetentionRootAuditResult.AuditSummary.generationOnly(7L);
        assertEquals(OptionalLong.of(7L), generationOnly.indexGeneration());
        assertEquals(
                List.of(
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty()),
                List.of(
                        generationOnly.selectedOwnerCount(),
                        generationOnly.onlineOwnerCount(),
                        generationOnly.integratedOwnerCount(),
                        generationOnly.diskOwnerCount(),
                        generationOnly.playerRootClaimCount(),
                        generationOnly.journalRootClaimCount(),
                        generationOnly.totalRawRootClaimCount(),
                        generationOnly.distinctSkillIdCount(),
                        generationOnly.auditedValidClaimCount(),
                        generationOnly.sourceCount()));

        new SkillRetentionRootAuditResult.Diagnostic(
                SkillRetentionRootAuditResult.Stage.INDEX_PUBLICATION,
                Optional.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                "x".repeat(160));
        assertThrows(IllegalArgumentException.class, () ->
                new SkillRetentionRootAuditResult.Diagnostic(
                        SkillRetentionRootAuditResult.Stage.INDEX_PUBLICATION,
                        Optional.empty(),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalInt.empty(),
                        Optional.empty(),
                        "x".repeat(161)));
    }

    @Test
    void publicMachineVocabularyIsExactAndCounterMappingRemainsExhaustive() {
        assertEquals(enumNames(P4E1AuditCounter.values()),
                enumNames(SkillRetentionRootAuditResult.Counter.values()));
        var expectedStages = new java.util.HashSet<>(enumNames(P4E1AuditStage.values()));
        expectedStages.add("FINAL_FRESHNESS");
        expectedStages.add("INDEX_PUBLICATION");
        assertEquals(expectedStages, enumNames(SkillRetentionRootAuditResult.Stage.values()));
        assertEquals(
                Set.of("STORE_REFERENCE_MISSING", "STORE_OWNER_MISMATCH"),
                enumNames(SkillRetentionRootAuditResult.ReconciliationReason.values()));
        assertEquals(
                Set.of("ONLINE", "DEFERRED_INTEGRATED", "DEFERRED_OFFLINE"),
                enumNames(SkillRetentionRootAuditResult.Disposition.values()));
        assertEquals(
                Set.of(
                        "HEAP_FLOOR_NOT_MET",
                        "HEAP_FLOOR_UNVERIFIABLE",
                        "STORE_UNAVAILABLE",
                        "JOURNAL_NOT_READY",
                        "JOURNAL_UNAVAILABLE",
                        "JOURNAL_TARGET_INVALID",
                        "INVENTORY_PROVIDER_MISSING",
                        "COUNTER_CAPACITY_EXCEEDED",
                        "DIRECTORY_UNREADABLE",
                        "DIRECTORY_TYPE_UNSUPPORTED",
                        "DIRECTORY_IDENTITY_UNAVAILABLE",
                        "DIRECTORY_RACE_DETECTED",
                        "PLAYERDATA_NAME_NONCANONICAL",
                        "PRIMARY_FILE_UNREADABLE",
                        "PRIMARY_FILE_TYPE_UNSUPPORTED",
                        "PRIMARY_FILE_IDENTITY_UNAVAILABLE",
                        "PRIMARY_FILE_RACE_DETECTED",
                        "PLATFORM_READ_FAILURE_PROVEN",
                        "STRICT_GZIP_REJECTED",
                        "STRICT_NBT_REJECTED",
                        "DATA_VERSION_MISSING",
                        "DATA_VERSION_WRONG_TYPE",
                        "DATA_VERSION_NOT_CURRENT",
                        "ATTACHMENT_ADMISSION_REJECTED",
                        "ATTACHMENT_QUARANTINED",
                        "INTEGRATED_OWNER_IDENTITY_UNAVAILABLE",
                        "INTEGRATED_OWNER_FRESHNESS_LOST",
                        "ONLINE_SOURCE_FRESHNESS_LOST",
                        "SERVER_FRESHNESS_LOST",
                        "CALL_CHAIN_FRESHNESS_LOST",
                        "INDEX_RESERVATION_LOST",
                        "STORE_SOURCE_FRESHNESS_LOST",
                        "JOURNAL_FRESHNESS_LOST",
                        "JOURNAL_TARGET_PROOF_LOST",
                        "INVENTORY_PROVIDER_FRESHNESS_LOST",
                        "SELECTED_FILE_FRESHNESS_LOST",
                        "GENERATION_EXHAUSTED",
                        "INTERNAL_RUNTIME_FAILURE"),
                enumNames(SkillRetentionRootAuditResult.IncompleteReason.values()));
    }

    @Test
    void serviceAndHandoffArePackageOwnedAndIndexStatesStayPrivate() {
        var requiredOperations = Set.of(
                "audit",
                "consumeComplete",
                "invalidateForReconciliation",
                "isReconciliationInvalidationCurrent",
                "removeServer");
        var operations = Arrays.stream(SkillRetentionRootAuditService.class.getDeclaredMethods())
                .filter(method -> requiredOperations.contains(method.getName()))
                .toList();
        assertEquals(requiredOperations,
                operations.stream().map(method -> method.getName()).collect(Collectors.toSet()));
        assertEquals(requiredOperations.size(), operations.size());
        for (var operation : operations) {
            assertPackagePrivate(operation.getModifiers(), operation.toString());
        }

        var nestedByName = Arrays.stream(SkillRetentionRootAuditService.class
                        .getDeclaredClasses())
                .collect(Collectors.toMap(Class::getSimpleName, type -> type));
        assertTrue(nestedByName.values().stream()
                .noneMatch(type -> Modifier.isPublic(type.getModifiers())
                        || Modifier.isProtected(type.getModifiers())));
        for (var name : List.of(
                "IndexSlot", "IndexState", "IndexedBacking", "PermitCell", "LeaseCell",
                "IndexedSource")) {
            assertTrue(nestedByName.containsKey(name), name);
            assertTrue(Modifier.isPrivate(nestedByName.get(name).getModifiers()), name);
        }
        var invalidation = nestedByName.get("InvalidationResult");
        assertTrue(invalidation != null, "InvalidationResult");
        assertTrue(invalidation.isSealed());
        assertPackagePrivate(invalidation.getModifiers(), invalidation.toString());
        assertEquals(
                Set.of("Accepted", "GenerationExhausted"),
                Arrays.stream(invalidation.getPermittedSubclasses())
                        .map(Class::getSimpleName)
                        .collect(Collectors.toSet()));
        var invalidationNested = Arrays.stream(invalidation.getDeclaredClasses())
                .collect(Collectors.toMap(Class::getSimpleName, type -> type));
        var accepted = invalidationNested.get("Accepted");
        var exhausted = invalidationNested.get("GenerationExhausted");
        assertTrue(accepted.isRecord());
        assertEquals(List.of("generation"), Arrays.stream(accepted.getRecordComponents())
                .map(component -> component.getName()).toList());
        assertEquals(long.class, accepted.getRecordComponents()[0].getType());
        assertTrue(exhausted.isEnum());
        assertEquals(Set.of("INSTANCE"), Arrays.stream(exhausted.getEnumConstants())
                .map(Object::toString).collect(Collectors.toSet()));
        assertEquals(
                Set.of("iterator", "close"),
                Arrays.stream(P4E1CompleteRootHandoff.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))
                        .map(method -> method.getName())
                        .collect(Collectors.toSet()));
        try {
            var forceInvalidate = P4E1CompleteRootHandoff.class.getDeclaredMethod(
                    "forceInvalidate", P4E1CompleteRootHandoff.LeaseAuthority.class);
            assertPackagePrivate(forceInvalidate.getModifiers(), forceInvalidate.toString());
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("forced server-stop handoff invalidation seam is absent",
                    exception);
        }
        assertTrue(Arrays.stream(SkillRetentionRootAuditService.class.getDeclaredFields())
                .noneMatch(field -> Modifier.isStatic(field.getModifiers())));
    }

    @Test
    void productionChainHasNoMutationSnapshotReclaimOrBackgroundEscape() throws Exception {
        var service = Files.readString(STORE_ROOT.resolve("SkillRetentionRootAuditService.java"));
        var sources = service
                + Files.readString(STORE_ROOT.resolve("SkillRetentionRootAuditResult.java"))
                + Files.readString(STORE_ROOT.resolve("P4E1FinalFreshness.java"))
                + Files.readString(STORE_ROOT.resolve("P4E1CompleteRootHandoff.java"));
        var allProduction = javaSources(MAIN_JAVA);
        var totalGameTestCount = occurrences(allProduction, "@GameTest(");
        var manaGameTestCount = occurrences(
                Files.readString(MANA_GAME_TEST_SOURCE), "@GameTest(");

        assertTrue(service.contains(
                "IdentityHashMap<MinecraftServer, IndexSlot> index = new IdentityHashMap<>()"));
        assertTrue(service.contains("var expectedBacking = transfer.backingIdentity(this)"));
        assertTrue(service.contains(
                "var backing = new IndexedBacking(expectedBacking, indexedSources)"));
        assertTrue(service.contains("transfer.releaseBacking(this, expectedBacking)"));
        assertEquals(0, occurrences(sources, "new P4E1RawClaimBuffer"));
        var audited = Files.readString(STORE_ROOT.resolve("P4E1AuditedCapture.java"));
        assertTrue(audited.contains(
                "if (claims != Objects.requireNonNull(expectedBacking, \"expectedBacking\"))"));
        assertTrue(audited.contains("claims.markIndexed()"));
        assertFalse(sources.contains("WeakHashMap"));
        var publicMappings = service.substring(
                service.indexOf(
                        "private static SkillRetentionRootAuditResult.IncompleteReason "
                                + "incompleteReason("),
                service.indexOf("private static void requireReservable("));
        assertFalse(publicMappings.contains("default ->"));
        for (var forbidden : List.of(
                "SkillRetentionRootSnapshot.fromCompleteRoots",
                ".reclaim(",
                ".commit(",
                ".pin(",
                ".snapshot(",
                ".append(",
                ".setData(",
                ".setDirty(",
                "NbtIo.write",
                "DataFixer",
                "Files.write",
                "Files.move",
                "Files.delete",
                "Files.newOutputStream",
                "ServerStartingEvent",
                "ServerStartedEvent",
                "ServerStoppedEvent",
                "@SubscribeEvent",
                "Executors.",
                "ExecutorService",
                "CompletableFuture",
                "parallelStream(",
                "new Thread(",
                "java.lang.ref.Cleaner",
                "finalize(",
                "CustomPacketPayload",
                "PayloadRegistrar",
                "setChunkForced",
                "getChunk(",
                "List<SkillReference>",
                "ArrayList<SkillReference>",
                "SkillReference[]",
                "java.lang.reflect",
                "sun.misc.Unsafe",
                "@SuppressWarnings",
                "catch (Error",
                "catch (OutOfMemoryError",
                "catch (Throwable",
                "Codec")) {
            assertFalse(sources.contains(forbidden), forbidden);
        }
        assertEquals(1, occurrences(
                allProduction, "SkillRetentionRootSnapshot.fromCompleteRoots"));
        assertEquals(1, occurrences(
                Files.readString(STORE_ROOT.resolve("SkillDefinitionStoreService.java")),
                "SkillRetentionRootSnapshot.fromCompleteRoots"));
        assertEquals(1, occurrences(
                allProduction, "new SkillRetentionRootAuditService("));
        assertEquals(1, occurrences(
                Files.readString(STORE_ROOT.resolve("SkillDefinitionStoreService.java")),
                "new SkillRetentionRootAuditService("));
        assertEquals(12, totalGameTestCount - manaGameTestCount);
        assertEquals(7, manaGameTestCount);
        assertEquals(19, totalGameTestCount);
    }

    @Test
    void captureCapabilityPublicationTransfersOwnershipAtAllocationSafeBoundaries()
            throws Exception {
        var capture = Files.readString(STORE_ROOT.resolve("P4E1GlobalSourceCapture.java"));

        var online = capture.substring(
                capture.indexOf("private static OnlineIdentityCapture captureOnlineIdentities("),
                capture.indexOf("static CaptureResult.Incomplete onlineRelevantCapacityFailure("));
        var onlineWrapper = online.indexOf("var result = new OnlineIdentityCapture(");
        var onlinePublished = online.indexOf("complete = true;", onlineWrapper);
        assertTrue(onlineWrapper >= 0);
        assertTrue(onlinePublished > onlineWrapper);

        var admitted = capture.substring(
                capture.indexOf("private static CaptureResult processAdmitted("),
                capture.indexOf("private static CaptureResult processJournal("));
        var admissionSink = admitted.indexOf("var sink = new ReservationSink(reserved);");
        var admissionDrain = admitted.indexOf("drainRootProjection(", admissionSink);
        var admissionReleased = admitted.indexOf("ownsProjection = false;", admissionSink);
        assertTrue(admissionSink >= 0);
        assertTrue(admissionReleased > admissionSink);
        assertTrue(admissionDrain > admissionReleased);

        var playerService = Files.readString(MAIN_JAVA.resolve(
                "com/yo1no/gramarye/magic/definition/player/"
                        + "PlayerSkillAttachmentService.java"));
        var projectionDrain = playerService.substring(
                playerService.indexOf("public void drainRootProjection("),
                playerService.indexOf("public void discardRootProjection("));
        var consumed = projectionDrain.indexOf("admitted.consumeAndClear()");
        var ownerChecked = projectionDrain.indexOf("if (owner != this)", consumed);
        var sinkChecked = projectionDrain.indexOf(
                "Objects.requireNonNull(sink, \"sink\")", ownerChecked);
        var callbacks = projectionDrain.indexOf("for (var state : latest)", sinkChecked);
        assertTrue(consumed >= 0
                && ownerChecked > consumed
                && sinkChecked > ownerChecked
                && callbacks > sinkChecked);

        var journal = capture.substring(
                capture.indexOf("private static CaptureResult processJournal("),
                capture.indexOf("private static void addZeroSource("));
        var journalSink = journal.indexOf(
                "P4E1PendingJournalObservation.TargetSink sink = reserved::appendJournal;");
        var journalDrain = journal.indexOf("journal.drain(", journalSink);
        var journalReleased = journal.indexOf("context.journalConsumed = true;", journalDrain);
        assertTrue(journalSink >= 0);
        assertTrue(journalDrain > journalSink);
        assertTrue(journalReleased > journalDrain);
    }

    @Test
    void exactServerRemovalSeamForceRevokesAuthorityWithoutASecondListener()
            throws Exception {
        var service = Files.readString(STORE_ROOT.resolve("SkillRetentionRootAuditService.java"));
        var removal = service.substring(
                service.indexOf("void removeServer(MinecraftServer server)"),
                service.indexOf("private PreparedComplete prepareComplete("));
        assertTrue(removal.contains("SkillDefinitionStoreService.requireServerThread(server)"));
        assertEquals(1, occurrences(removal, "index.remove(server)"));
        assertEquals(1, occurrences(service, "handoff.forceInvalidate(this)"));
    }

    private static Set<Class<?>> publicResultTypes() {
        var types = new java.util.HashSet<Class<?>>();
        types.add(SkillRetentionRootAuditResult.class);
        Arrays.stream(SkillRetentionRootAuditResult.class.getDeclaredClasses())
                .filter(type -> Modifier.isPublic(type.getModifiers())
                        || Modifier.isProtected(type.getModifiers()))
                .forEach(types::add);
        return Set.copyOf(types);
    }

    private static boolean isForbiddenPublicType(Class<?> type) {
        return type == byte[].class
                || Tag.class.isAssignableFrom(type)
                || Path.class.isAssignableFrom(type)
                || File.class.isAssignableFrom(type)
                || Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || Iterable.class.isAssignableFrom(type)
                || Iterator.class.isAssignableFrom(type)
                || Stream.class.isAssignableFrom(type)
                || SkillReference.class.isAssignableFrom(type)
                || P4E1RawClaimBuffer.class.isAssignableFrom(type)
                || P4E1AuditedCapture.class.isAssignableFrom(type)
                || P4E1CompleteRootHandoff.class.isAssignableFrom(type)
                || SkillDefinitionStore.class.isAssignableFrom(type)
                || StoredSkillHistory.class.isAssignableFrom(type)
                || type.getSimpleName().contains("Carrier")
                || type.getSimpleName().contains("SavedData")
                || type.getSimpleName().contains("JournalTargetAuditProof")
                || type.getName().equals("com.mojang.serialization.Dynamic");
    }

    private static boolean isRootCarrier(Class<?> type) {
        return isForbiddenPublicType(type)
                || type.getSimpleName().contains("Root")
                || type.getSimpleName().contains("Handoff");
    }

    private static Set<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    private static void assertBoundedPublicType(Type type, String subject) {
        if (type == null) {
            return;
        }
        switch (type) {
            case Class<?> raw -> {
                assertFalse(isForbiddenPublicType(raw), subject);
                if (raw.isArray()) {
                    assertBoundedPublicType(raw.getComponentType(), subject);
                } else if (raw.getPackageName().startsWith("com.yo1no.gramarye")
                        && !isPublicResultType(raw)) {
                    throw new AssertionError(subject + " exposes internal type " + raw.getName());
                }
            }
            case ParameterizedType parameterized -> {
                assertBoundedPublicType(parameterized.getRawType(), subject);
                for (var argument : parameterized.getActualTypeArguments()) {
                    assertBoundedPublicType(argument, subject);
                }
            }
            case GenericArrayType array ->
                    assertBoundedPublicType(array.getGenericComponentType(), subject);
            case WildcardType wildcard -> {
                for (var bound : wildcard.getUpperBounds()) {
                    assertBoundedPublicType(bound, subject);
                }
                for (var bound : wildcard.getLowerBounds()) {
                    assertBoundedPublicType(bound, subject);
                }
            }
            case TypeVariable<?> variable -> {
                for (var bound : variable.getBounds()) {
                    assertBoundedPublicType(bound, subject);
                }
            }
            default -> throw new AssertionError(
                    subject + " has unsupported public signature type " + type);
        }
    }

    private static boolean isPublicResultType(Class<?> type) {
        for (var current = type; current != null; current = current.getEnclosingClass()) {
            if (current == SkillRetentionRootAuditResult.class) {
                return true;
            }
        }
        return false;
    }

    private static void assertPackagePrivate(int modifiers, String subject) {
        assertFalse(Modifier.isPublic(modifiers), subject);
        assertFalse(Modifier.isProtected(modifiers), subject);
        assertFalse(Modifier.isPrivate(modifiers), subject);
    }

    private static String javaSources(Path root) throws Exception {
        var text = new StringBuilder();
        try (var stream = Files.walk(root)) {
            for (var path : stream.filter(path -> path.toString().endsWith(".java")).toList()) {
                text.append(Files.readString(path)).append('\n');
            }
        }
        return text.toString();
    }

    private static int occurrences(String text, String token) {
        var count = 0;
        var offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
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
