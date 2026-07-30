package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.migration.SkillMigrationFactCode;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Phase-local API and architecture gate for the package-internal P4-B1 carrier boundary. */
class P4B1ApiGateTest {
    private static final String STORE_PACKAGE =
            "com.yo1no.gramarye.magic.definition.store.";
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path STORE_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store");
    private static final Pattern TOP_LEVEL_TYPE_DECLARATION = Pattern.compile(
            "(?m)^(?:(?:public|protected|private|abstract|final|non-sealed|sealed|static)\\s+)*"
                    + "(?:class|record|interface|enum)\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\b");

    @Test
    void exactB1TypesExistAndRemainPackagePrivate() throws Exception {
        var loaded = P4B1PhaseTypes.TOP_LEVEL_TYPE_NAMES.stream()
                .map(P4B1ApiGateTest::loadWithoutInitialization)
                .toList();
        var declared = b1Sources().stream()
                .flatMap(path -> TOP_LEVEL_TYPE_DECLARATION
                        .matcher(withoutCommentsAndLiterals(read(path)))
                        .results()
                        .map(match -> match.group(1)))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertAll(
                () -> assertEquals(P4B1PhaseTypes.TOP_LEVEL_TYPE_NAMES, declared),
                () -> assertEquals(
                        P4B1PhaseTypes.TOP_LEVEL_TYPE_NAMES,
                        loaded.stream().map(Class::getSimpleName).collect(Collectors.toSet())),
                () -> assertTrue(loaded.stream().allMatch(type -> {
                    var modifiers = type.getModifiers();
                    return !Modifier.isPublic(modifiers)
                            && !Modifier.isProtected(modifiers)
                            && !Modifier.isPrivate(modifiers);
                })));
    }

    @Test
    void b1AddsOnlyTheApprovedPublicCeilingsAndMigrationFact() {
        var ceilingNames = Arrays.stream(MagicSafetyCeilings.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .map(field -> field.getName())
                .collect(Collectors.toSet());
        var factNames = Arrays.stream(SkillMigrationFactCode.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(
                        1_048_576,
                        MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES),
                () -> assertEquals(
                        69_206_016,
                        MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES),
                () -> assertEquals(
                        4_096,
                        MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES),
                () -> assertTrue(ceilingNames.containsAll(Set.of(
                        "MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES",
                        "MAX_PENDING_ATTACHMENT_UPDATES",
                        "MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES"))),
                // P4-C2-A phase-local: its service reuses the five reviewed C1 ceilings through
                // the universal bridge; the constants remain asserted by P4C1ApiGateTest.
                () -> assertTrue(Set.of(
                                "MAX_STORE_QUARANTINE_ENTRY_BYTES",
                                "MAX_STORE_QUARANTINE_TOTAL_BYTES")
                        .stream().noneMatch(ceilingNames::contains)),
                () -> assertEquals(Set.of(
                                "STEP_APPLIED",
                                "PAYLOAD_STEP_APPLIED",
                                "STORE_STEP_APPLIED",
                                "SAVED_DATA_STEP_APPLIED"),
                        factNames));
    }

    @Test
    void p3dStorePublicSurfaceRemainsUnchanged() {
        var publicMethods = Arrays.stream(SkillDefinitionStore.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                        "find", "latestReference", "ownerOf", "committedSkillCount",
                        "pin", "reclaim", "commit"),
                publicMethods);
    }

    @Test
    void carrierMatchingSeamIsNarrowPackagePrivateAndHasReviewedCallSites()
            throws Exception {
        var method = EncodedSkillStoreCarrier.class.getDeclaredMethod(
                "matchesStoreBlob", ImmutableStoreBlob.class);
        var production = productionSources(STORE_ROOT);

        assertAll(
                () -> assertEquals(boolean.class, method.getReturnType()),
                () -> assertFalse(Modifier.isPublic(method.getModifiers())),
                () -> assertFalse(Modifier.isProtected(method.getModifiers())),
                () -> assertFalse(Modifier.isPrivate(method.getModifiers())),
                () -> assertTrue(Arrays.stream(EncodedSkillStoreCarrier.class
                                .getDeclaredMethods())
                        .noneMatch(candidate -> candidate.getReturnType() == byte[].class)),
                () -> assertEquals(
                        Set.of(
                                "EncodedSkillStoreCarrier.java",
                                "SkillSavedDataCarrierPersistenceBridge.java"),
                        filesContaining(production, "matchesStoreBlob(")));
    }

    @Test
    void bridgeOwnsTheProductionPlanAndDoesNotExposeAnInjectableLoadPlan()
            throws Exception {
        var bridge = loadWithoutInitialization("SkillSavedDataCarrierPersistenceBridge");
        var plan = loadWithoutInitialization("SkillSavedDataCarrierMigrationPlan");
        var loadMethods = Arrays.stream(bridge.getDeclaredMethods())
                .filter(method -> method.getName().equals("loadDecompressed"))
                .toList();
        var productionLoad = loadMethods.stream()
                .filter(method -> Arrays.equals(
                        method.getParameterTypes(),
                        new Class<?>[] {InputStream.class, Optional.class}))
                .findFirst()
                .orElse(null);

        assertAll(
                () -> assertNotNull(productionLoad),
                () -> assertEquals(
                        "SkillSavedDataCarrierLoadResult",
                        productionLoad.getReturnType().getSimpleName()),
                () -> assertFalse(Modifier.isPublic(productionLoad.getModifiers())),
                () -> assertTrue(Arrays.stream(bridge.getDeclaredMethods())
                        .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                        .noneMatch(plan::equals)),
                () -> assertTrue(Arrays.stream(bridge.getDeclaredMethods())
                        .noneMatch(method -> Modifier.isPublic(method.getModifiers()))));
    }

    @Test
    void readyAndInnerCarrierFactoriesHaveOnlyTheB1BridgeAsProductionCaller()
            throws Exception {
        var production = productionSources(STORE_ROOT);

        assertAll(
                () -> assertEquals(
                        Set.of(
                                "SkillSavedDataInnerCarrier.java",
                                "GramaryeSkillSavedData.java",
                                "SkillSavedDataCarrierPersistenceBridge.java",
                                "SkillSubmissionRecoveryGameTests.java",
                                "SkillDefinitionStoreSubmissionPort.java"),
                        filesContaining(production, "fromPrevalidatedFraming(")),
                () -> assertEquals(
                        Set.of(
                                "SkillSavedDataReadyCandidate.java",
                                "SkillSubmissionRecoveryGameTests.java",
                                "SkillSavedDataCarrierPersistenceBridge.java"),
                        filesContaining(production, "afterCarrierRebuild(")),
                () -> assertEquals(
                        Set.of("SkillSavedDataCarrierMigrator.java"),
                        filesContaining(
                                production,
                                "SkillSavedDataCarrierMigrationPlans.production()")));
    }

    @Test
    void failureAndResultTypesDoNotRetainRawOrRuntimeState() {
        var reviewedRoots = List.of(
                loadWithoutInitialization("SkillSavedDataCarrierFailure"),
                loadWithoutInitialization("SkillSavedDataCarrierLoadResult"),
                loadWithoutInitialization("SkillSavedDataCarrierMigrationFailure"),
                loadWithoutInitialization("SkillSavedDataCarrierMigrationResult"));
        var reviewed = nestedTypeClosure(reviewedRoots);
        var forbiddenFieldTypes = Set.of(
                byte[].class.getName(),
                InputStream.class.getName(),
                "java.nio.file.Path",
                "java.lang.Throwable",
                "net.minecraft.nbt.Tag",
                "net.minecraft.nbt.CompoundTag",
                "com.mojang.serialization.Dynamic",
                "com.yo1no.gramarye.magic.validation.ValidationResult");

        assertAll(
                () -> assertTrue(reviewed.stream()
                        .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                        .noneMatch(field -> forbiddenFieldTypes.contains(
                                        field.getType().getName())
                                || Throwable.class.isAssignableFrom(field.getType()))),
                () -> assertTrue(reviewed.stream()
                        .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .noneMatch(method -> forbiddenFieldTypes.contains(
                                        method.getReturnType().getName())
                                || Arrays.stream(method.getParameterTypes())
                                        .map(Class::getName)
                                        .anyMatch(forbiddenFieldTypes::contains))),
                () -> assertTrue(List.of(
                                "SkillSavedDataCarrierFailure.java",
                                "SkillSavedDataCarrierLoadResult.java",
                                "SkillSavedDataCarrierMigrationFailure.java",
                                "SkillSavedDataCarrierMigrationResult.java")
                        .stream()
                        .map(STORE_ROOT::resolve)
                        .map(P4B1ApiGateTest::read)
                        .map(P4B1ApiGateTest::withoutCommentsAndLiterals)
                        .noneMatch(source -> source.contains("byte[]")
                                || source.contains("InputStream")
                                || source.contains("CompoundTag")
                                || source.contains("Dynamic<")
                                || source.contains("Throwable"))));
    }

    @Test
    void b1SourcesStayInsideCarrierAndMigrationBoundary() throws Exception {
        var code = b1Sources().stream()
                .map(P4B1ApiGateTest::read)
                .map(P4B1ApiGateTest::withoutCommentsAndLiterals)
                .collect(Collectors.joining("\n"));
        var forbiddenTokens = List.of(
                "net.minecraft.world.level.saveddata.SavedData",
                "DimensionDataStorage",
                "ServerStartingEvent",
                "GZIPInputStream",
                "GZIPOutputStream",
                "NbtAccounter.unlimitedHeap",
                "MAX_SKILL_SAVED_DATA_FILE_BYTES",
                "java.nio.file",
                "java.lang.reflect",
                "setDirty",
                "PlayerSkillAttachment",
                "PendingAttachmentJournal",
                "AttachmentType",
                "IAttachmentHolder",
                "ServerPlayer",
                "ServerLevel",
                "SkillDefinitionSubmissionService",
                "RootCollector",
                "RootIndex",
                "StreamCodec",
                "Quarantined",
                "SubsystemUnavailable",
                "mutationGeneration",
                "expectedAttachmentGeneration",
                "targetAttachmentGeneration",
                "P4B2Probe",
                "P4B2MemoryGameTests",
                "P4C2Probe",
                "P4C2MemoryGameTests",
                "@SuppressWarnings");

        assertAll(
                () -> assertTrue(forbiddenTokens.stream().noneMatch(code::contains)),
                () -> assertFalse(Pattern.compile("\\bextends\\s+SavedData\\b")
                        .matcher(code).find()),
                () -> assertFalse(Pattern.compile("\\bcatch\\s*\\(\\s*(?:Error|Throwable)\\b")
                        .matcher(code).find()),
                () -> assertFalse(Pattern.compile("\\.\\s*commit\\s*\\(")
                        .matcher(code).find()),
                () -> assertFalse(Pattern.compile("\\.\\s*reclaim\\s*\\(")
                        .matcher(code).find()),
                () -> assertFalse(Pattern.compile(
                                "(?m)^\\s*(?:public\\s+)?(?:final\\s+)?"
                                        + "(?:class|record|interface|enum)\\s+"
                                        + "(?:Test|Fixture|Fake|Dummy|Noop|Stub)\\w*")
                        .matcher(code).find()));
    }

    private static List<Path> b1Sources() throws Exception {
        return productionSources(STORE_ROOT).stream()
                .filter(path -> {
                    var matcher = TOP_LEVEL_TYPE_DECLARATION.matcher(
                            withoutCommentsAndLiterals(read(path)));
                    while (matcher.find()) {
                        if (P4B1PhaseTypes.containsTopLevelName(matcher.group(1))) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
    }

    private static List<Path> productionSources(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static Set<Class<?>> nestedTypeClosure(List<Class<?>> roots) {
        var result = new LinkedHashSet<Class<?>>();
        var pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            var type = pending.removeFirst();
            if (!result.add(type)) {
                continue;
            }
            pending.addAll(Arrays.asList(type.getDeclaredClasses()));
            var permitted = type.getPermittedSubclasses();
            if (permitted != null) {
                pending.addAll(Arrays.asList(permitted));
            }
        }
        return Set.copyOf(result);
    }

    private static Set<String> filesContaining(List<Path> sources, String fragment) {
        return sources.stream()
                .filter(path -> read(path).contains(fragment))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
    }

    private static Class<?> loadWithoutInitialization(String simpleName) {
        try {
            return Class.forName(
                    STORE_PACKAGE + simpleName,
                    false,
                    P4B1ApiGateTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Missing reviewed P4-B1 class: " + simpleName, exception);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("Unable to inspect " + path, exception);
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

    private static String withoutCommentsAndLiterals(String source) {
        var sanitized = new StringBuilder(source.length());
        var state = LexicalState.CODE;
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            var next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    if (current == '/' && next == '/') {
                        sanitized.append("  ");
                        index++;
                        state = LexicalState.LINE_COMMENT;
                    } else if (current == '/' && next == '*') {
                        sanitized.append("  ");
                        index++;
                        state = LexicalState.BLOCK_COMMENT;
                    } else if (current == '"') {
                        sanitized.append(' ');
                        state = LexicalState.STRING;
                    } else if (current == '\'') {
                        sanitized.append(' ');
                        state = LexicalState.CHARACTER;
                    } else {
                        sanitized.append(current);
                    }
                }
                case LINE_COMMENT -> {
                    sanitized.append(current == '\n' ? '\n' : ' ');
                    if (current == '\n') {
                        state = LexicalState.CODE;
                    }
                }
                case BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        sanitized.append("  ");
                        index++;
                        state = LexicalState.CODE;
                    } else {
                        sanitized.append(current == '\n' ? '\n' : ' ');
                    }
                }
                case STRING, CHARACTER -> {
                    var delimiter = state == LexicalState.STRING ? '"' : '\'';
                    if (current == '\\' && next != '\0') {
                        sanitized.append("  ");
                        index++;
                    } else {
                        sanitized.append(current == '\n' ? '\n' : ' ');
                        if (current == delimiter) {
                            state = LexicalState.CODE;
                        }
                    }
                }
            }
        }
        return sanitized.toString();
    }

    private enum LexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER
    }
}
