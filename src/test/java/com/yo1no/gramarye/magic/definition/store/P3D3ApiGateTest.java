package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPlan;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class P3D3ApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path STORE_SOURCE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store/SkillDefinitionStore.java");

    @Test
    void storeHasExactlySevenReviewedDomainMethodsAndTwoTruthStateMaps() throws Exception {
        var publicMethods = Arrays.stream(SkillDefinitionStore.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();
        var constructors = SkillDefinitionStore.class.getDeclaredConstructors();

        assertEquals(Set.of(
                        "find", "latestReference", "ownerOf", "committedSkillCount",
                        "commit", "pin", "reclaim"),
                publicMethods.stream().map(method -> method.getName()).collect(Collectors.toSet()));
        assertEquals(7, publicMethods.size());
        assertEquals(Optional.class,
                SkillDefinitionStore.class.getMethod("find", SkillReference.class).getReturnType());
        assertGenericReturn(
                SkillDefinitionStore.class.getMethod("find", SkillReference.class),
                Optional.class,
                SkillDocument.class);
        assertEquals(Optional.class,
                SkillDefinitionStore.class.getMethod("latestReference", SkillId.class).getReturnType());
        assertGenericReturn(
                SkillDefinitionStore.class.getMethod("latestReference", SkillId.class),
                Optional.class,
                SkillReference.class);
        assertEquals(Optional.class,
                SkillDefinitionStore.class.getMethod("ownerOf", SkillId.class).getReturnType());
        assertGenericReturn(
                SkillDefinitionStore.class.getMethod("ownerOf", SkillId.class),
                Optional.class,
                SkillOwnerId.class);
        assertEquals(int.class,
                SkillDefinitionStore.class.getMethod("committedSkillCount", SkillOwnerId.class)
                        .getReturnType());
        assertEquals(SkillStoreCommitResult.class,
                SkillDefinitionStore.class.getMethod(
                                "commit", SkillSubmissionPlan.class, SkillQuota.class)
                        .getReturnType());
        assertEquals(Optional.class,
                SkillDefinitionStore.class.getMethod("pin", SkillReference.class).getReturnType());
        assertGenericReturn(
                SkillDefinitionStore.class.getMethod("pin", SkillReference.class),
                Optional.class,
                SkillRevisionPin.class);
        assertEquals(SkillReclaimResult.class,
                SkillDefinitionStore.class.getMethod(
                                "reclaim", SkillRetentionRootSnapshot.class)
                        .getReturnType());
        assertTrue(publicMethods.stream().noneMatch(method -> Modifier.isSynchronized(
                method.getModifiers())));
        assertTrue(publicMethods.stream().noneMatch(method -> Modifier.isStatic(
                method.getModifiers())));

        assertEquals(2, constructors.length);
        assertEquals(1, Arrays.stream(constructors)
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers())
                        && constructor.getParameterCount() == 0)
                .count());
        assertPackagePrivate(SkillDefinitionStore.class.getDeclaredMethod("snapshot").getModifiers());
        var restore = SkillDefinitionStore.class.getDeclaredMethod(
                "restore", SkillDefinitionStoreSnapshot.class);
        assertPackagePrivate(restore.getModifiers());
        assertTrue(Modifier.isStatic(restore.getModifiers()));
        assertEquals(SkillDefinitionStoreRestoreResult.class, restore.getReturnType());

        var fields = Arrays.stream(SkillDefinitionStore.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        assertEquals(Set.of("histories", "activePinCounts"),
                fields.stream().map(field -> field.getName()).collect(Collectors.toSet()));
        assertMapField(
                SkillDefinitionStore.class.getDeclaredField("histories"),
                SkillId.class,
                StoredSkillHistory.class);
        assertMapField(
                SkillDefinitionStore.class.getDeclaredField("activePinCounts"),
                SkillReference.class,
                Integer.class);
    }

    @Test
    void storedHistoryAddsOnlyTheReviewedPackagePrivateRetainHelper() throws Exception {
        var fields = Arrays.stream(StoredSkillHistory.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        var retain = StoredSkillHistory.class.getDeclaredMethod("retainRevisions", Set.class);
        var parameter = assertInstanceOf(
                ParameterizedType.class, retain.getGenericParameterTypes()[0]);

        assertEquals(Set.of("owner", "revisions"),
                fields.stream().map(field -> field.getName()).collect(Collectors.toSet()));
        var owner = StoredSkillHistory.class.getDeclaredField("owner");
        assertEquals(SkillOwnerId.class, owner.getType());
        assertTrue(Modifier.isPrivate(owner.getModifiers()));
        assertTrue(Modifier.isFinal(owner.getModifiers()));
        var revisions = StoredSkillHistory.class.getDeclaredField("revisions");
        assertEquals(NavigableMap.class, revisions.getType());
        var revisionMap = assertInstanceOf(ParameterizedType.class, revisions.getGenericType());
        assertEquals(NavigableMap.class, revisionMap.getRawType());
        assertEquals(List.of(SkillRevision.class, SkillDocument.class),
                Arrays.asList(revisionMap.getActualTypeArguments()));
        assertTrue(Modifier.isPrivate(revisions.getModifiers()));
        assertTrue(Modifier.isFinal(revisions.getModifiers()));
        assertFalse(Modifier.isPublic(retain.getModifiers()));
        assertFalse(Modifier.isPrivate(retain.getModifiers()));
        assertFalse(Modifier.isProtected(retain.getModifiers()));
        assertFalse(Modifier.isStatic(retain.getModifiers()));
        assertEquals(StoredSkillHistory.class, retain.getReturnType());
        assertEquals(Set.class, parameter.getRawType());
        assertEquals(List.of(SkillRevision.class),
                Arrays.asList(parameter.getActualTypeArguments()));
        assertTrue(Arrays.stream(StoredSkillHistory.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("reclaim")));
    }

    @Test
    void rootSnapshotHasExactSealedShapeAndNoFactoryBypass() throws Exception {
        assertTrue(Modifier.isPublic(SkillRetentionRootSnapshot.class.getModifiers()));
        assertTrue(SkillRetentionRootSnapshot.class.isSealed());
        assertEquals(Set.of(
                        SkillRetentionRootSnapshot.Complete.class,
                        SkillRetentionRootSnapshot.Incomplete.class,
                        SkillRetentionRootSnapshot.Truncated.class,
                        SkillRetentionRootSnapshot.OverLimit.class),
                Set.of(SkillRetentionRootSnapshot.class.getPermittedSubclasses()));

        var factory = SkillRetentionRootSnapshot.class.getDeclaredMethod(
                "fromCompleteRoots", Iterable.class);
        var factoryInput = assertInstanceOf(
                ParameterizedType.class, factory.getGenericParameterTypes()[0]);
        assertTrue(Modifier.isPublic(factory.getModifiers()));
        assertTrue(Modifier.isStatic(factory.getModifiers()));
        assertEquals(SkillRetentionRootSnapshot.class, factory.getReturnType());
        assertEquals(Iterable.class, factoryInput.getRawType());
        assertEquals(List.of(SkillReference.class),
                Arrays.asList(factoryInput.getActualTypeArguments()));
        assertEquals(1, Arrays.stream(SkillRetentionRootSnapshot.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .count());

        var complete = SkillRetentionRootSnapshot.Complete.class;
        var constructors = complete.getDeclaredConstructors();
        var fields = complete.getDeclaredFields();
        assertTrue(Modifier.isPublic(complete.getModifiers()));
        assertTrue(Modifier.isFinal(complete.getModifiers()));
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertEquals(List.of(List.class), Arrays.asList(constructors[0].getParameterTypes()));
        var constructorInput = assertInstanceOf(
                ParameterizedType.class, constructors[0].getGenericParameterTypes()[0]);
        assertEquals(List.class, constructorInput.getRawType());
        assertEquals(List.of(SkillReference.class),
                Arrays.asList(constructorInput.getActualTypeArguments()));
        assertEquals(1, fields.length);
        assertEquals("roots", fields[0].getName());
        assertListField(fields[0], SkillReference.class);
        assertEquals(Set.of("roots", "toString"),
                Arrays.stream(complete.getDeclaredMethods())
                        .map(method -> method.getName())
                        .collect(Collectors.toSet()));
        assertEquals(2, complete.getDeclaredMethods().length);
        var roots = complete.getDeclaredMethod("roots");
        assertTrue(Modifier.isPublic(roots.getModifiers()));
        assertEquals(List.class, roots.getReturnType());
        var rootsReturn = assertInstanceOf(ParameterizedType.class, roots.getGenericReturnType());
        assertEquals(List.of(SkillReference.class),
                Arrays.asList(rootsReturn.getActualTypeArguments()));
        assertEquals(String.class, complete.getDeclaredMethod("toString").getReturnType());
        assertTrue(Arrays.stream(complete.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("equals")
                        || method.getName().equals("hashCode")));

        assertEquals(List.of("observedAtLeast", "maximum"),
                componentNames(SkillRetentionRootSnapshot.OverLimit.class));
        assertEquals(List.of(int.class, int.class),
                componentTypes(SkillRetentionRootSnapshot.OverLimit.class));
        assertEquals(
                List.of(SkillRetentionRootSnapshot.Incomplete.INSTANCE),
                Arrays.asList(SkillRetentionRootSnapshot.Incomplete.values()));
        assertEquals(
                List.of(SkillRetentionRootSnapshot.Truncated.INSTANCE),
                Arrays.asList(SkillRetentionRootSnapshot.Truncated.values()));
    }

    @Test
    void reclaimFailureResultAndReportHaveOnlyBoundedTypedComponents() {
        assertTrue(Modifier.isPublic(SkillReclaimFailure.class.getModifiers()));
        assertTrue(SkillReclaimFailure.class.isSealed());
        assertEquals(Set.of(
                        SkillReclaimFailure.IncompleteRootSnapshot.class,
                        SkillReclaimFailure.TruncatedRootSnapshot.class,
                        SkillReclaimFailure.RootCapacityExceeded.class,
                        SkillReclaimFailure.MissingExternalRoot.class),
                Set.of(SkillReclaimFailure.class.getPermittedSubclasses()));
        assertEquals(
                List.of(int.class, int.class),
                componentTypes(SkillReclaimFailure.RootCapacityExceeded.class));
        assertEquals(List.of("observedAtLeast", "maximum"),
                componentNames(SkillReclaimFailure.RootCapacityExceeded.class));
        assertEquals(List.of("reference"),
                componentNames(SkillReclaimFailure.MissingExternalRoot.class));
        assertEquals(List.of(SkillReference.class),
                componentTypes(SkillReclaimFailure.MissingExternalRoot.class));
        assertEquals(
                List.of(SkillReclaimFailure.IncompleteRootSnapshot.INSTANCE),
                Arrays.asList(SkillReclaimFailure.IncompleteRootSnapshot.values()));
        assertEquals(
                List.of(SkillReclaimFailure.TruncatedRootSnapshot.INSTANCE),
                Arrays.asList(SkillReclaimFailure.TruncatedRootSnapshot.values()));

        assertTrue(Modifier.isPublic(SkillReclaimReport.class.getModifiers()));
        assertTrue(Modifier.isFinal(SkillReclaimReport.class.getModifiers()));
        assertEquals(
                List.of("historiesScanned", "revisionsScanned", "historiesChanged",
                        "revisionsReclaimed"),
                componentNames(SkillReclaimReport.class));
        assertEquals(List.of(int.class, int.class, int.class, int.class),
                componentTypes(SkillReclaimReport.class));

        assertTrue(Modifier.isPublic(SkillReclaimResult.class.getModifiers()));
        assertTrue(SkillReclaimResult.class.isSealed());
        assertEquals(Set.of(SkillReclaimResult.Completed.class, SkillReclaimResult.Rejected.class),
                Set.of(SkillReclaimResult.class.getPermittedSubclasses()));
        assertEquals(List.of("report"), componentNames(SkillReclaimResult.Completed.class));
        assertEquals(List.of(SkillReclaimReport.class),
                componentTypes(SkillReclaimResult.Completed.class));
        assertEquals(List.of("failure"), componentNames(SkillReclaimResult.Rejected.class));
        assertEquals(List.of(SkillReclaimFailure.class),
                componentTypes(SkillReclaimResult.Rejected.class));

        var records = List.of(
                SkillReclaimFailure.RootCapacityExceeded.class,
                SkillReclaimFailure.MissingExternalRoot.class,
                SkillReclaimReport.class,
                SkillReclaimResult.Completed.class,
                SkillReclaimResult.Rejected.class);
        assertTrue(records.stream()
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .noneMatch(component -> component.getType() == String.class
                        || Throwable.class.isAssignableFrom(component.getType())
                        || SkillDocument.class.isAssignableFrom(component.getType())
                        || SkillOwnerId.class.isAssignableFrom(component.getType())
                        || Map.class.isAssignableFrom(component.getType())
                        || List.class.isAssignableFrom(component.getType())));
    }

    @Test
    void persistenceSnapshotShapeStillContainsNoPinRootOrReportState() {
        assertEquals(Set.of("histories"), declaredFieldNames(SkillDefinitionStoreSnapshot.class));
        assertEquals(Set.of("skillId", "owner", "revisions"),
                declaredFieldNames(SkillHistorySnapshot.class));
        assertEquals(Set.of("revision", "document"),
                declaredFieldNames(SkillRevisionSnapshot.class));
        assertTrue(List.of(
                        SkillDefinitionStoreSnapshot.class,
                        SkillHistorySnapshot.class,
                        SkillRevisionSnapshot.class)
                .stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .noneMatch(field -> field.getName().toLowerCase().contains("pin")
                        || field.getName().toLowerCase().contains("root")
                        || field.getName().toLowerCase().contains("reclaim")
                        || field.getType() == SkillReclaimReport.class));
    }

    @Test
    void reclaimConstructionPointsAreCentralizedAtTheStoreBoundary() throws Exception {
        var sources = productionSources();
        var constructedTypes = List.of(
                "SkillReclaimResult.Completed",
                "SkillReclaimResult.Rejected",
                "SkillReclaimFailure.RootCapacityExceeded",
                "SkillReclaimFailure.MissingExternalRoot",
                "SkillReclaimReport");
        for (var constructedType : constructedTypes) {
            var sites = sources.stream()
                    .filter(path -> containsConstruction(path, constructedType))
                    .toList();
            assertEquals(List.of(STORE_SOURCE), sites, constructedType);
            assertEquals(1, sources.stream()
                    .mapToInt(path -> constructionCount(path, constructedType))
                    .sum(), constructedType);
        }

        var rootSource = MAIN_JAVA.resolve(
                "com/yo1no/gramarye/magic/definition/store/SkillRetentionRootSnapshot.java");
        assertEquals(List.of(rootSource), sources.stream()
                .filter(path -> rootVariantConstructionCount(
                                path,
                                rootSource,
                                "SkillRetentionRootSnapshot.Complete",
                                "Complete")
                        > 0)
                .toList());
        assertEquals(1, sources.stream()
                .mapToInt(path -> rootVariantConstructionCount(
                        path,
                        rootSource,
                        "SkillRetentionRootSnapshot.Complete",
                        "Complete"))
                .sum());
        assertEquals(List.of(rootSource), sources.stream()
                .filter(path -> rootVariantConstructionCount(
                                path,
                                rootSource,
                                "SkillRetentionRootSnapshot.OverLimit",
                                "OverLimit")
                        > 0)
                .toList());
        assertEquals(1, sources.stream()
                .mapToInt(path -> rootVariantConstructionCount(
                        path,
                        rootSource,
                        "SkillRetentionRootSnapshot.OverLimit",
                        "OverLimit"))
                .sum());
    }

    @Test
    void constructionScannerRecognizesQualifiedImportedWildcardAndReferenceSpellings() {
        var type = "SkillReclaimResult.Completed";
        assertEquals(1, constructionCountSource(
                "class Probe { Object value() { return new "
                        + "com.yo1no.gramarye.magic.definition.store.SkillReclaimResult.Completed(null); } }",
                type));
        assertEquals(1, constructionCountSource(
                "import com.yo1no.gramarye.magic.definition.store.SkillReclaimResult.Completed; "
                        + "class Probe { Object value() { return new Completed(null); } }",
                type));
        assertEquals(1, constructionCountSource(
                "import com.yo1no.gramarye.magic.definition.store.SkillReclaimResult.*; "
                        + "class Probe { Object value() { return new Completed(null); } }",
                type));
        assertEquals(1, constructionCountSource(
                "class Probe { java.util.function.Function<Object,Object> value = "
                        + "SkillReclaimResult.Completed::new; }",
                type));
        assertEquals(1, constructionCountSource(
                "import com.yo1no.gramarye.magic.definition.store.SkillReclaimResult.Completed; "
                        + "class Probe { java.util.function.Function<Object,Object> value = "
                        + "SkillReclaimResult.Completed::new; }",
                type));
        assertEquals(0, constructionCountSource(
                "class Completed {} class Probe { Object value = Completed::new; }",
                type));

        var rootType = "SkillRetentionRootSnapshot.OverLimit";
        var qualifiedRootConstruction =
                "class Probe { Object value() { return new "
                        + "SkillRetentionRootSnapshot.OverLimit(2, 1); } }";
        assertEquals(1, constructionCountSource(qualifiedRootConstruction, rootType));
        assertEquals(0, localConstructionCountSource(qualifiedRootConstruction, "OverLimit"));
        var localRootConstruction =
                "class Probe { Object value() { return new OverLimit(2, 1); } }";
        assertEquals(0, constructionCountSource(localRootConstruction, rootType));
        assertEquals(1, localConstructionCountSource(localRootConstruction, "OverLimit"));
    }

    @Test
    void reclaimSourceKeepsFixedPrecedenceAndPrebuildsBeforePublication() {
        var source = readSanitized(STORE_SOURCE);
        var start = source.indexOf("public SkillReclaimResult reclaim");
        var end = source.indexOf("void releasePin", start);
        var reclaim = source.substring(start, end);
        var nonNull = reclaim.indexOf("Objects.requireNonNull(externalRoots");
        var incomplete = reclaim.indexOf("SkillRetentionRootSnapshot.Incomplete.INSTANCE");
        var truncated = reclaim.indexOf("SkillRetentionRootSnapshot.Truncated.INSTANCE");
        var overLimit = reclaim.indexOf("SkillRetentionRootSnapshot.OverLimit");
        var missingLoop = reclaim.indexOf("for (var root : complete.roots())");
        var dedup = reclaim.indexOf("new HashSet<>(complete.roots())");
        var pins = reclaim.indexOf("requireActivePinInvariants()");
        var replacements = reclaim.indexOf("new HashMap<SkillId, StoredSkillHistory>()");
        var report = reclaim.indexOf("new SkillReclaimReport");
        var completed = reclaim.indexOf("new SkillReclaimResult.Completed");
        var publish = reclaim.indexOf("histories.put");
        var returned = reclaim.indexOf("return completed", publish);

        assertTrue(0 <= nonNull && nonNull < incomplete);
        assertTrue(incomplete < truncated && truncated < overLimit);
        assertTrue(overLimit < missingLoop && missingLoop < dedup);
        assertTrue(dedup < pins && pins < replacements);
        assertTrue(replacements < report && report < completed);
        assertTrue(completed < publish && publish < returned);
        assertFalse(reclaim.contains("activePinCounts.put"));
        assertFalse(reclaim.contains("activePinCounts.remove"));
        assertFalse(reclaim.contains("histories.remove"));
        assertFalse(reclaim.contains(".compute("));
        assertFalse(reclaim.contains("replaceAll"));
        for (var forbiddenMutation : List.of(
                "histories.putAll", "histories.merge", "histories.replace",
                "histories.clear", "activePinCounts.put", "activePinCounts.putAll",
                "activePinCounts.merge", "activePinCounts.replace",
                "activePinCounts.compute", "activePinCounts.clear")) {
            assertFalse(reclaim.contains(forbiddenMutation), forbiddenMutation);
        }
    }

    @Test
    void storePackageContainsNoP4RuntimeDiscoveryOrDestructiveSurface() throws Exception {
        var storeSources = productionSources().stream()
                .filter(path -> path.toString().contains("/magic/definition/store/"))
                .filter(P3D3ApiGateTest::isP3dStoreSource)
                .toList();
        var forbiddenSourceTokens = List.of(
                "net.minecraft.", "net.neoforged.", "SavedData", "Attachment",
                "Codec", "DynamicOps", "Tag", "RootProvider", "RootCollector",
                "ReclaimService", "ReclaimOptions", "ForceReclaim", "PinRegistry",
                "setDirty", "forceReclaim", "bestEffortReclaim", "releaseQuota",
                "retire(", "delete(", "tombstone", "generation", "epoch", "synchronized",
                "Player", "ServerPlayer", "Level", "SkillInstance", "Marker", "Construct",
                "Schedule", "SkillMigration", "java.util.concurrent", "Thread", "Cleaner",
                "Executor", "Timer", "scheduler");

        for (var source : storeSources) {
            var text = withoutExactD1StoreIntegration(source, readSanitized(source));
            for (var token : forbiddenSourceTokens) {
                assertFalse(text.contains(token), source + " contains " + token);
            }
        }

        // C1/C2-A player/document production and C2-B test-only types never join Store production.
        assertTrue(storeSources.stream()
                .map(path -> path.getFileName().toString())
                .noneMatch(name -> P4C1PhaseTypes.containsSourceFileName(name)
                        || P4C2PhaseTypes.containsSourceFileName(name)
                        || P4C2BPhaseTypes.containsSourceFileName(name)));

        var productionNames = productionClassNames();
        assertTrue(productionNames.stream()
                .map(P3D3ApiGateTest::simpleTopLevelName)
                .map(String::toLowerCase)
                .noneMatch(name -> name.contains("rootprovider")
                        || name.contains("rootcollector")
                        || name.contains("forcereclaim")
                        || name.contains("reclaimoptions")));
        assertFalse(productionNames.contains(
                "com.yo1no.gramarye.magic.definition.store.StoreTestFixtures"));
    }

    @Test
    void retentionRootCeilingHasOneProductionTruthAndFactoryUsesIt() throws Exception {
        var ceilingSource = readSanitized(MAIN_JAVA.resolve(
                "com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java"));
        assertEquals(1, countMatches(
                Pattern.compile("\\bpublic\\s+static\\s+final\\s+int\\s+"
                        + "MAX_RETENTION_ROOTS_PER_RECLAIM\\s*=\\s*65_536\\s*;"),
                ceilingSource));
        var rootSource = readSanitized(MAIN_JAVA.resolve(
                "com/yo1no/gramarye/magic/definition/store/SkillRetentionRootSnapshot.java"));
        var factoryStart = rootSource.indexOf("static SkillRetentionRootSnapshot fromCompleteRoots");
        var factoryEnd = rootSource.indexOf("final class Complete", factoryStart);
        assertTrue(factoryStart >= 0 && factoryEnd > factoryStart);
        var factorySource = rootSource.substring(factoryStart, factoryEnd);
        assertTrue(factorySource.contains(
                "MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM"));
    }

    @Test
    void finalArchitectureLedgerRecordsP4MigrationAndDirtyObligations() throws Exception {
        var ledger = PROJECT_ROOT.resolve("docs/architecture/P3-D-skill-definition-store.md");
        assertTrue(Files.isRegularFile(ledger));
        var readme = Files.readString(PROJECT_ROOT.resolve("docs/architecture/README.md"));
        var d0 = Files.readString(PROJECT_ROOT.resolve(
                "docs/architecture/P3-D0-store-boundary.md"));
        var normalized = Files.readString(ledger).toLowerCase();

        assertTrue(readme.contains("P3-D-skill-definition-store.md"));
        assertTrue(d0.contains("P3-D-skill-definition-store.md"));
        assertTrue(normalized.contains("../codex-spec/17_p3"));
        assertTrue(normalized.contains("../codex-spec/16_"));
        assertTrue(normalized.contains("../codex-spec/codex_"));
        assertTrue(normalized.contains("../codex-spec/neoforge1.21.1_"));
        assertTrue(normalized.contains("## d0"));
        assertTrue(normalized.contains("## d1"));
        assertTrue(normalized.contains("## d2"));
        assertTrue(normalized.contains("## d3-a"));
        assertTrue(normalized.contains("## d3-b"));
        assertTrue(normalized.contains("dirty-state matrix"));
        assertTrue(normalized.contains(
                "p3-d store `committed` + prebuilt store carrier/journal published | dirty"));
        assertTrue(normalized.contains("commit typed failure | not dirty"));
        assertTrue(normalized.contains("pin / close | not dirty"));
        assertTrue(normalized.contains("reclaim `rejected` | not dirty"));
        assertTrue(normalized.contains("reclaim `completed`, reclaimed = 0 | not dirty"));
        assertTrue(normalized.contains("reclaim `completed`, reclaimed > 0 | dirty"));
        assertTrue(normalized.contains("snapshot / read | not dirty"));
        assertTrue(normalized.contains("## p4 obligations"));
        assertTrue(normalized.contains("migration before restore"));
        assertTrue(normalized.contains("old schema -> migration -> current-schema snapshot -> restore success"));
        assertTrue(normalized.contains(
                "same old document without migration -> unsupporteddocumentschema"));
        assertTrue(normalized.contains("migration failure"));
        assertTrue(normalized.contains("restore rejection"));
        assertTrue(normalized.contains("corruption/quarantine"));

        var storePackageText = productionSources().stream()
                .filter(path -> path.toString().contains("/magic/definition/store/"))
                .filter(P3D3ApiGateTest::isP3dStoreSource)
                .map(P3D3ApiGateTest::readSanitized)
                .collect(Collectors.joining("\n"));
        assertFalse(storePackageText.contains("SkillDocumentMigrator"));
        assertFalse(storePackageText.contains("SkillMigrationPlan"));
        assertFalse(storePackageText.contains("RawSkillDocumentSnapshot"));
        assertFalse(storePackageText.contains("magic.definition.migration"));
        assertFalse(storePackageText.contains("P4B2Probe"));
        assertFalse(storePackageText.contains("P4B2Memory"));
        var storeSource = readSanitized(STORE_SOURCE);
        assertTrue(storeSource.contains(
                "restore(SkillDefinitionStoreSnapshot snapshot)"));
        assertTrue(storeSource.contains("SkillDocument.CURRENT_SCHEMA_VERSION"));
    }

    private static boolean isP3dStoreSource(Path path) {
        var name = path.getFileName().toString();
        return !name.startsWith("StorePersistence")
                && !name.equals("SkillSubmissionRecoveryGameTests.java")
                && !name.equals("StoreNbtFraming.java")
                && !name.equals("StorePersistentEnvelopeV0.java")
                && !name.equals("ImmutableStoreBlob.java")
                && !name.equals("SkillDefinitionStorePersistenceBridge.java")
                && !P4B1PhaseTypes.containsSourceFileName(name)
                && !P4B2PhaseTypes.containsSourceFileName(name)
                && !P4DPhaseTypes.containsNewStoreSourceFileName(name)
                && !Set.of(
                                "StoreEncodingLayout.java",
                                "StoreLayoutEncodeResult.java",
                                "EncodedSkillStoreCarrier.java",
                                "EncodedHistoryIndex.java",
                                "EncodedRevisionIndex.java",
                                "PreparedCarrierUpdate.java",
                                "CarrierUpdateKind.java",
                                "SkillStoreCarrierBuilder.java",
                                "CarrierBuildResult.java",
                                "CarrierUpdateResult.java",
                                "CarrierInvariantException.java",
                                "HistoryBlobSource.java",
                                "RevisionBlobSource.java",
                                "StoreHistoryBlobSlice.java",
                                "StoreRevisionBlobSlice.java")
                        .contains(name);
    }

    private static String withoutExactD1StoreIntegration(Path source, String text) {
        if (!source.getFileName().toString().equals("SkillDefinitionStore.java")) {
            return text;
        }
        var start = text.indexOf("StoreSubmissionAuthorityObservation observeSubmissionAuthority(");
        var end = text.indexOf("public Optional<SkillRevisionPin> pin(", start);
        assertTrue(start >= 0, "missing reviewed D1 authority observation slice");
        assertTrue(end > start, "missing end of reviewed D1 Store integration slice");
        return text.substring(0, start) + text.substring(end);
    }

    private static void assertMapField(
            java.lang.reflect.Field field,
            Class<?> key,
            Class<?> value) {
        var generic = assertInstanceOf(ParameterizedType.class, field.getGenericType());
        assertEquals(Map.class, field.getType());
        assertEquals(Map.class, generic.getRawType());
        assertEquals(List.of(key, value), Arrays.asList(generic.getActualTypeArguments()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    private static void assertPackagePrivate(int modifiers) {
        assertFalse(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isProtected(modifiers));
        assertFalse(Modifier.isPrivate(modifiers));
    }

    private static void assertListField(java.lang.reflect.Field field, Class<?> element) {
        var generic = assertInstanceOf(ParameterizedType.class, field.getGenericType());
        assertEquals(List.class, field.getType());
        assertEquals(List.class, generic.getRawType());
        assertEquals(List.of(element), Arrays.asList(generic.getActualTypeArguments()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    private static void assertGenericReturn(
            java.lang.reflect.Method method,
            Class<?> rawType,
            Type... arguments) {
        var generic = assertInstanceOf(ParameterizedType.class, method.getGenericReturnType());
        assertEquals(rawType, generic.getRawType());
        assertEquals(List.of(arguments), Arrays.asList(generic.getActualTypeArguments()));
    }

    private static List<String> componentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(component -> component.getName()).toList();
    }

    private static List<Class<?>> componentTypes(Class<? extends Record> type) {
        var types = new java.util.ArrayList<Class<?>>();
        Arrays.stream(type.getRecordComponents()).forEach(component -> types.add(component.getType()));
        return List.copyOf(types);
    }

    private static Set<String> declaredFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getName())
                .collect(Collectors.toSet());
    }

    private static List<Path> productionSources() throws Exception {
        try (var paths = Files.walk(MAIN_JAVA)) {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static Set<String> productionClassNames() throws Exception {
        var root = PROJECT_ROOT.resolve("build/classes/java/main");
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".class"))
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(name -> name.substring(0, name.length() - ".class".length()))
                    .map(name -> name.replace(java.io.File.separatorChar, '.'))
                    .collect(Collectors.toSet());
        }
    }

    private static boolean containsConstruction(Path path, String typeName) {
        return containsConstructionSource(readSanitized(path), typeName);
    }

    private static int constructionCount(Path path, String typeName) {
        return constructionCountSource(readSanitized(path), typeName);
    }

    private static int rootVariantConstructionCount(
            Path path,
            Path rootSource,
            String qualifiedTypeName,
            String localTypeName) {
        var count = constructionCount(path, qualifiedTypeName);
        return path.equals(rootSource)
                ? count + localConstructionCountSource(readSanitized(path), localTypeName)
                : count;
    }

    private static boolean containsConstructionSource(String source, String typeName) {
        return constructionCountSource(withoutCommentsAndLiterals(source), typeName) > 0;
    }

    private static int constructionCountSource(String source, String typeName) {
        var simple = typeName.substring(typeName.lastIndexOf('.') + 1);
        var outer = typeName.contains(".")
                ? typeName.substring(0, typeName.lastIndexOf('.'))
                : "";
        var qualifiedPattern = Pattern.compile("\\bnew\\s+(?:[\\w$.]+\\.)?"
                + Pattern.quote(typeName) + "\\s*\\(");
        var nestedImport = source.contains("import com.yo1no.gramarye.magic.definition.store."
                + typeName + ";")
                || source.contains("import static com.yo1no.gramarye.magic.definition.store."
                        + typeName + ";");
        var wildcardImport = !outer.isEmpty()
                && (source.contains("import com.yo1no.gramarye.magic.definition.store."
                                + outer + ".*;")
                        || source.contains("import static com.yo1no.gramarye.magic.definition.store."
                                + outer + ".*;"));
        var simplePattern = Pattern.compile("\\bnew\\s+" + Pattern.quote(simple) + "\\s*\\(");
        var qualifiedReferencePattern = Pattern.compile("\\b(?:[\\w$.]+\\.)?"
                + Pattern.quote(typeName) + "\\s*::\\s*new");
        var simpleReferencePattern = Pattern.compile(
                "(?<![\\w$.])" + Pattern.quote(simple) + "\\s*::\\s*new");
        var count = countMatches(qualifiedPattern, source);
        if (typeName.contains(".") && (nestedImport || wildcardImport)) {
            count += countMatches(simplePattern, source);
        }
        count += countMatches(qualifiedReferencePattern, source);
        if (typeName.contains(".") && (nestedImport || wildcardImport)) {
            count += countMatches(simpleReferencePattern, source);
        }
        return count;
    }

    private static int localConstructionCountSource(String source, String simpleTypeName) {
        return countMatches(
                Pattern.compile("\\bnew\\s+" + Pattern.quote(simpleTypeName) + "\\s*\\("),
                withoutCommentsAndLiterals(source));
    }

    private static int countMatches(Pattern pattern, String source) {
        var matcher = pattern.matcher(source);
        var count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String readSanitized(Path path) {
        try {
            return withoutCommentsAndLiterals(Files.readString(path));
        } catch (java.io.IOException exception) {
            throw new AssertionError("Unable to read source " + path, exception);
        }
    }

    private static String withoutCommentsAndLiterals(String source) {
        var result = new StringBuilder(source.length());
        var block = false;
        var line = false;
        var string = false;
        var character = false;
        var escaped = false;
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            var next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (line) {
                if (current == '\n') {
                    line = false;
                    result.append('\n');
                } else {
                    result.append(' ');
                }
            } else if (block) {
                if (current == '*' && next == '/') {
                    result.append("  ");
                    index++;
                    block = false;
                } else {
                    result.append(current == '\n' ? '\n' : ' ');
                }
            } else if (string || character) {
                result.append(current == '\n' ? '\n' : ' ');
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if ((string && current == '"') || (character && current == '\'')) {
                    string = false;
                    character = false;
                }
            } else if (current == '/' && next == '/') {
                result.append("  ");
                index++;
                line = true;
            } else if (current == '/' && next == '*') {
                result.append("  ");
                index++;
                block = true;
            } else if (current == '"') {
                result.append(' ');
                string = true;
            } else if (current == '\'') {
                result.append(' ');
                character = true;
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("build.gradle"))
                    && Files.isDirectory(candidate.resolve("src/main/java"))) {
                return candidate;
            }
        }
        throw new AssertionError("Unable to locate project root");
    }

    private static String simpleTopLevelName(String className) {
        var simple = className.substring(className.lastIndexOf('.') + 1);
        var nested = simple.indexOf('$');
        return nested < 0 ? simple : simple.substring(0, nested);
    }
}
