package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.EncodedSkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentStorePersistenceFacade;
import com.yo1no.gramarye.magic.definition.migration.OpaqueSkillDocumentMigrationFacade;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class P4A2ApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path DEFINITION_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition");
    private static final Path STORE_ROOT = DEFINITION_ROOT.resolve("store");

    @Test
    void exactlyThreePublicDocumentMigrationFacadesOwnTheReviewedCrossPackageSeams()
            throws Exception {
        var facadeDeclaration = Pattern.compile(
                "\\bpublic\\s+final\\s+class\\s+([A-Za-z0-9_]*Facade)\\b");
        var publicFacades = productionSources(DEFINITION_ROOT).stream()
                .flatMap(path -> facadeDeclaration.matcher(read(path)).results())
                .map(match -> match.group(1))
                .collect(Collectors.toSet());
        var documentMethods = Arrays.stream(
                        SkillDocumentStorePersistenceFacade.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(Set.of(
                                "SkillDocumentStorePersistenceFacade",
                                "SkillDraftPersistenceFacade",
                                "OpaqueSkillDocumentMigrationFacade"),
                        publicFacades),
                () -> assertEquals(Set.of("encodeCurrent", "load"), documentMethods),
                () -> assertTrue(Modifier.isPublic(
                        SkillDocumentStorePersistenceFacade.class.getModifiers())),
                () -> assertTrue(Modifier.isPublic(
                        OpaqueSkillDocumentMigrationFacade.class.getModifiers())),
                () -> assertTrue(Modifier.isPublic(
                        SkillDraftPersistenceFacade.class.getModifiers())),
                () -> assertEquals(
                        Set.of("encodeCurrent", "loadAlwaysMigrating"),
                        Arrays.stream(SkillDraftPersistenceFacade.class.getDeclaredMethods())
                                .filter(method -> Modifier.isPublic(method.getModifiers()))
                                .map(method -> method.getName())
                                .collect(Collectors.toSet())),
                () -> assertTrue(Modifier.isPublic(EncodedSkillDocument.class.getModifiers())),
                () -> assertTrue(Arrays.stream(
                                SkillDocumentStorePersistenceFacade.class.getDeclaredMethods())
                        .noneMatch(method -> Set.of(
                                        "hydrateCurrent", "decodeCurrent", "loadCurrent",
                                        "skipMigration")
                                .contains(method.getName()))));
    }

    @Test
    void storePhysicalDtosPlansResultsAndBridgeRemainPackageInternal() {
        var internalTypes = List.of(
                ImmutableStoreBlob.class,
                ImmutableHistoryBlob.class,
                ImmutableRevisionBlob.class,
                StorePersistentEnvelopeV0.class,
                HistoryPersistentEnvelopeV0.class,
                RevisionPersistentEnvelopeV0.class,
                StorePersistenceMigrationStep.class,
                StorePersistenceMigrationStepOutput.class,
                StorePersistenceMigrationPlan.class,
                StorePersistenceMigrationPlans.class,
                StorePersistenceMigrationFailure.class,
                StorePersistenceMigrationResult.class,
                StorePersistenceMigrator.class,
                StorePersistenceFailure.class,
                StorePersistenceEncodeResult.class,
                StorePersistenceLoadResult.class,
                StoreNbtFraming.class,
                SkillDefinitionStorePersistenceBridge.class,
                SkillDefinitionStoreSnapshot.class,
                SkillHistorySnapshot.class,
                SkillRevisionSnapshot.class);

        assertTrue(internalTypes.stream().noneMatch(type ->
                Modifier.isPublic(type.getModifiers())
                        || Modifier.isProtected(type.getModifiers())
                        || Modifier.isPrivate(type.getModifiers())));
    }

    @Test
    void p3dStoreSurfaceAndSnapshotShapeAreUnchanged() {
        var publicMethods = Arrays.stream(SkillDefinitionStore.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(Set.of(
                                "find", "latestReference", "ownerOf", "committedSkillCount",
                                "pin", "reclaim", "commit"),
                        publicMethods),
                () -> assertEquals(Set.of("histories"),
                        fieldNames(SkillDefinitionStoreSnapshot.class)),
                () -> assertEquals(Set.of("skillId", "owner", "revisions"),
                        fieldNames(SkillHistorySnapshot.class)),
                () -> assertEquals(Set.of("revision", "document"),
                        fieldNames(SkillRevisionSnapshot.class)));
    }

    @Test
    void storeEncodeDelegatesToTheDocumentFacadeWithoutAParallelSerializer() throws Exception {
        var bridge = read(STORE_ROOT.resolve("SkillDefinitionStorePersistenceBridge.java"));
        var framing = read(STORE_ROOT.resolve("StoreNbtFraming.java"));
        var reviewed = bridge + "\n" + framing;

        assertAll(
                () -> assertTrue(bridge.contains(
                        "SkillDocumentStorePersistenceFacade.encodeCurrent(")),
                () -> assertTrue(bridge.contains(
                        "SkillDocumentStorePersistenceFacade::load")),
                () -> assertFalse(reviewed.contains("SkillDocumentPersistenceBridge")),
                () -> assertFalse(reviewed.contains("RawTreeEnvelope")),
                () -> assertFalse(reviewed.contains("PhysicalSkillDocument")),
                () -> assertFalse(reviewed.contains("AppearanceStorageCodec")),
                () -> assertFalse(reviewed.contains("SkillDocumentWriter")),
                () -> assertFalse(reviewed.contains("Codec.PASSTHROUGH")),
                () -> assertFalse(framing.contains("document().copyBytes()")),
                () -> assertTrue(framing.contains("document().copyInto")
                        || framing.contains("value.copyInto(bytes, position)")));
    }

    @Test
    void p4a2DoesNotDuplicateDomainCapacityOrIntroduceLaterLifecycle() throws Exception {
        var a2Sources = List.of(
                STORE_ROOT.resolve("ImmutableStoreBlob.java"),
                STORE_ROOT.resolve("SkillDefinitionStorePersistenceBridge.java"),
                STORE_ROOT.resolve("StoreNbtFraming.java"),
                STORE_ROOT.resolve("StorePersistenceFailure.java"),
                STORE_ROOT.resolve("StorePersistenceLoadResult.java"),
                STORE_ROOT.resolve("StorePersistenceMigrationFailure.java"),
                STORE_ROOT.resolve("StorePersistenceMigrationPlan.java"),
                STORE_ROOT.resolve("StorePersistenceMigrationResult.java"),
                STORE_ROOT.resolve("StorePersistenceMigrationStep.java"),
                STORE_ROOT.resolve("StorePersistenceMigrator.java"),
                STORE_ROOT.resolve("StorePersistenceSchema.java"),
                STORE_ROOT.resolve("StorePersistentEnvelopeV0.java"));
        var text = a2Sources.stream().map(P4A2ApiGateTest::read)
                .collect(Collectors.joining("\n"));
        var forbiddenLater = List.of(
                "SavedData", "DimensionDataStorage", "Attachment", "Journal",
                "EncodedSkillStoreCarrier", "setDirty", "ServerPlayer", "ServerLevel",
                "Network", "StreamCodec");
        var forbiddenDomainCeilings = List.of(
                "MAX_COMMITTED_SKILLS_PER_OWNER", "MAX_COMMITTED_SKILLS_GLOBAL",
                "MAX_RETAINED_REVISIONS_PER_SKILL", "MAX_RETAINED_REVISIONS_GLOBAL");

        assertAll(
                () -> assertTrue(forbiddenLater.stream().noneMatch(text::contains)),
                () -> assertTrue(forbiddenDomainCeilings.stream().noneMatch(text::contains)),
                () -> assertFalse(text.contains("StoreCountCapacityExceeded")),
                () -> assertFalse(text.contains("HistoryCountCapacityExceeded")),
                () -> assertFalse(text.contains("@SuppressWarnings(\"unchecked\")")),
                () -> assertTrue(Arrays.stream(
                                StorePersistenceMigrationResult.Failure.class.getDeclaredMethods())
                        .noneMatch(method -> method.getName().equals("originalTree"))),
                () -> assertFalse(text.contains("generation()")));
    }

    @Test
    void productionStoreMigrationPlanHasOneProviderAndNoLaterCompositionOrProbeTypes()
            throws Exception {
        var planSource = read(STORE_ROOT.resolve("StorePersistenceMigrationPlan.java"));
        var allSource = productionSources(MAIN_JAVA).stream()
                .map(P4A2ApiGateTest::read)
                .collect(Collectors.joining("\n"));

        assertAll(
                () -> assertEquals(1, occurrences(planSource,
                        "static StorePersistenceMigrationPlan production()")),
                () -> assertEquals(1, occurrences(planSource,
                        "private static final StorePersistenceMigrationPlan PRODUCTION")),
                // P4-C2-A phase-local: its exact registration/service lifecycle is reviewed
                // production. Journal/composition and isolated probe code remain absent.
                () -> assertTrue(List.of(
                                "PendingAttachmentJournal", "P4A3HeapProbeMain",
                                "P4A3CarrierGameTests")
                        .stream().noneMatch(allSource::contains)));
    }

    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getName())
                .collect(Collectors.toSet());
    }

    private static int occurrences(String text, String token) {
        var count = 0;
        for (var index = text.indexOf(token); index >= 0;
                index = text.indexOf(token, index + token.length())) {
            count++;
        }
        return count;
    }

    private static List<Path> productionSources(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new AssertionError("unable to inspect " + path, exception);
        }
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("build.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root not found");
    }
}
