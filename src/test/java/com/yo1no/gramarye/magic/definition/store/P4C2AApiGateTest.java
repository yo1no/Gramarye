package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.Gramarye;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.junit.jupiter.api.Test;

/** Phase-local API, ownership, and absence gate for P4-C2-A. */
class P4C2AApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path PLAYER_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/player");
    private static final Pattern TOP_LEVEL_TYPE_DECLARATION = Pattern.compile(
            "(?m)^(?:(?:public|protected|private|abstract|final|non-sealed|sealed|static)\\s+)*"
                    + "(?:class|record|interface|enum)\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\b");

    @Test
    void exactC1AndC2APlayerSourcesExistWithOnlyReviewedPublicTopLevels()
            throws Exception {
        var allPlayerSources = javaSources(PLAYER_ROOT);
        var c2Sources = P4C2PhaseTypes.PLAYER_SOURCE_FILE_NAMES.stream()
                .map(PLAYER_ROOT::resolve)
                .sorted()
                .toList();
        var expectedSources = new LinkedHashSet<>(P4C1PhaseTypes.PLAYER_SOURCE_FILE_NAMES);
        expectedSources.addAll(P4C2PhaseTypes.PLAYER_SOURCE_FILE_NAMES);
        P4EPhaseTypes.PLAYER_TYPE_NAMES.stream()
                .map(name -> name + ".java")
                .forEach(expectedSources::add);
        var loaded = P4C2PhaseTypes.PLAYER_TOP_LEVEL_TYPE_NAMES.stream()
                .map(name -> load(P4C2PhaseTypes.PLAYER_PACKAGE + name))
                .toList();
        var publicTopLevels = loaded.stream()
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(expectedSources, allPlayerSources.stream()
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.toSet())),
                () -> assertTrue(c2Sources.stream().allMatch(Files::isRegularFile)),
                () -> assertEquals(
                        P4C2PhaseTypes.PLAYER_TOP_LEVEL_TYPE_NAMES,
                        topLevelDeclarations(c2Sources)),
                () -> assertEquals(P4C2PhaseTypes.PUBLIC_TOP_LEVEL_TYPE_NAMES, publicTopLevels));
    }

    @Test
    void registrationOwnsTheStableTypeAndWiresFreshDefaultSerializerDeathAndNoSync()
            throws Exception {
        var registrationPath = PLAYER_ROOT.resolve("PlayerSkillAttachments.java");
        var registration = read(registrationPath);
        var code = withoutCommentsAndLiterals(registration);
        var production = javaSources(MAIN_JAVA);
        var registrationRelative = relative(registrationPath);

        assertAll(
                () -> assertTrue(registration.contains("DeferredRegister<AttachmentType<?>>")),
                () -> assertTrue(registration.contains("DeferredHolder<")),
                () -> assertTrue(registration.contains("NeoForgeRegistries.Keys.ATTACHMENT_TYPES")),
                () -> assertTrue(registration.contains("\"player_skills\"")),
                () -> assertOrdered(
                        registration,
                        "PlayerSkillAttachmentPersistenceBridge::freshEmptyReady",
                        ".serialize(PlayerSkillAttachmentSerializer.INSTANCE)",
                        ".copyOnDeath()",
                        ".build()"),
                () -> assertFalse(code.contains(".sync(")),
                () -> assertEquals(Set.of(registrationRelative),
                        relativeFilesContaining(production, "AttachmentType")),
                () -> assertEquals(Set.of(registrationRelative),
                        relativeFilesContaining(
                                production, "DeferredRegister<AttachmentType<?>>")),
                () -> assertEquals(Set.of(registrationRelative),
                        relativeFilesContaining(production, "DeferredHolder")),
                () -> assertTrue(relativeFilesContaining(production, "\"player_skills\"")
                        .stream().allMatch(path -> path.equals(registrationRelative)
                                || path.endsWith("PlayerSkillAttachmentGameTests.java"))));
    }

    @Test
    void universalRebuildAndObservationSeamsArePackagePrivateAndComplete() {
        var bridge = load(P4C2PhaseTypes.PLAYER_PACKAGE
                + "PlayerSkillAttachmentPersistenceBridge");
        var buildResult = load(P4C2PhaseTypes.PLAYER_PACKAGE
                + "PlayerSkillAttachmentBuildResult");
        var observed = load(P4C2PhaseTypes.PLAYER_PACKAGE
                + "ObservedPlayerSkillAttachment");
        var methods = Arrays.stream(bridge.getDeclaredMethods())
                .filter(method -> Set.of("freshEmptyReady", "rebuildReady")
                        .contains(method.getName()))
                .toList();

        assertAll(
                () -> assertEquals(Set.of("freshEmptyReady", "rebuildReady"), methods.stream()
                        .map(method -> method.getName()).collect(Collectors.toSet())),
                () -> assertTrue(methods.stream().allMatch(method ->
                        Modifier.isStatic(method.getModifiers())
                                && !Modifier.isPublic(method.getModifiers())
                                && !Modifier.isProtected(method.getModifiers())
                                && !Modifier.isPrivate(method.getModifiers()))),
                () -> assertTrue(buildResult.isSealed()),
                () -> assertEquals(Set.of("Built", "Rejected"),
                        Arrays.stream(buildResult.getPermittedSubclasses())
                                .map(Class::getSimpleName).collect(Collectors.toSet())),
                () -> assertTrue(observed.isSealed()),
                () -> assertEquals(Set.of("Missing", "Ready", "Quarantined"),
                        Arrays.stream(observed.getPermittedSubclasses())
                                .map(Class::getSimpleName).collect(Collectors.toSet())),
                () -> assertTrue(Set.of(buildResult, observed).stream()
                        .noneMatch(type -> Modifier.isPublic(type.getModifiers()))));
    }

    @Test
    void publicServiceAndOpaquePreparedTokenExposeOnlyReviewedImmutableTypes() {
        var service = load(P4C2PhaseTypes.PLAYER_PACKAGE + "PlayerSkillAttachmentService");
        var nested = Arrays.stream(service.getDeclaredClasses())
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .toList();
        var prepared = nested.stream()
                .filter(type -> type.getSimpleName().equals("PreparedPlayerSkillTransition"))
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertTrue(Modifier.isPublic(service.getModifiers())),
                () -> assertTrue(Modifier.isFinal(service.getModifiers())),
                () -> assertTrue(Arrays.stream(service.getDeclaredConstructors())
                        .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers()))),
                () -> assertEquals(P4C2PhaseTypes.SERVICE_PUBLIC_METHOD_NAMES,
                        Arrays.stream(service.getDeclaredMethods())
                                .filter(method -> Modifier.isPublic(method.getModifiers()))
                                .filter(method -> !method.isBridge() && !method.isSynthetic())
                                .map(method -> method.getName())
                                .collect(Collectors.toSet())),
                () -> assertEquals(P4C2PhaseTypes.SERVICE_PUBLIC_NESTED_TYPE_NAMES,
                        nested.stream().map(Class::getSimpleName).collect(Collectors.toSet())),
                () -> assertFalse(prepared.isRecord()),
                () -> assertTrue(Arrays.stream(prepared.getDeclaredConstructors())
                        .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers()))),
                () -> assertEquals(
                        Set.of(
                                "expectedGeneration",
                                "expectedPointer",
                                "isBoundTo",
                                "isNoOp",
                                "owner",
                                "skillId",
                                "targetGeneration",
                                "targetPointer",
                                "toString"),
                        Arrays.stream(prepared.getDeclaredMethods())
                                .filter(method -> Modifier.isPublic(method.getModifiers()))
                                .filter(method -> !method.isBridge() && !method.isSynthetic())
                                .map(method -> method.getName())
                                .collect(Collectors.toSet())),
                () -> assertTrue(java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(service), nested.stream())
                        .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .noneMatch(method -> exposesForbiddenPublicType(method.getGenericReturnType())
                                || Arrays.stream(method.getGenericParameterTypes())
                                        .anyMatch(P4C2AApiGateTest::exposesForbiddenPublicType))),
                () -> assertTrue(java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(service), nested.stream())
                        .flatMap(type -> Arrays.stream(type.getDeclaredConstructors()))
                        .filter(constructor -> Modifier.isPublic(constructor.getModifiers())
                                || Modifier.isProtected(constructor.getModifiers()))
                        .flatMap(constructor -> Arrays.stream(
                                constructor.getGenericParameterTypes()))
                        .noneMatch(P4C2AApiGateTest::exposesForbiddenPublicType)),
                () -> assertTrue(java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(service), nested.stream())
                        .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                        .filter(field -> Modifier.isPublic(field.getModifiers())
                                || Modifier.isProtected(field.getModifiers()))
                        .noneMatch(field -> exposesForbiddenPublicType(field.getGenericType()))));
    }

    @Test
    void productionAttachmentAccessAndGenerationArithmeticHaveExactOwners() throws Exception {
        var production = javaSources(MAIN_JAVA);
        var service = "com/yo1no/gramarye/magic/definition/player/"
                + "PlayerSkillAttachmentService.java";
        var gameTests = "com/yo1no/gramarye/magic/definition/player/"
                + "PlayerSkillAttachmentGameTests.java";
        var sourceObservation = "com/yo1no/gramarye/magic/definition/player/"
                + "PlayerSkillAttachmentSourceObservation.java";
        var reviewedAttachmentAccessors = Set.of(service, gameTests, sourceObservation);
        var getDataOwners = relativeFilesContaining(production, ".getData(");
        var setDataOwners = relativeFilesContaining(production, ".setData(");
        var playerSources = javaSources(PLAYER_ROOT);
        var successorOwners = playerSources.stream()
                .filter(path -> Pattern.compile(
                                "OptionalInt\\.of\\s*\\(\\s*current\\s*\\+\\s*1\\s*\\)")
                        .matcher(withoutCommentsAndLiterals(read(path))).find())
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());

        assertAll(
                () -> assertTrue(getDataOwners.contains(service)),
                () -> assertTrue(getDataOwners.contains(gameTests)),
                () -> assertTrue(reviewedAttachmentAccessors.containsAll(getDataOwners)),
                () -> assertEquals(Set.of(service), setDataOwners),
                () -> assertTrue(relativeFilesContaining(production, ".removeData(").isEmpty()),
                () -> assertEquals(Set.of("MutationGeneration.java"), successorOwners),
                () -> assertTrue(read(PLAYER_ROOT.resolve("MutationGeneration.java"))
                        .contains("current == Integer.MAX_VALUE")));
    }

    @Test
    void c2AHolderStaysAtTwoTestsWhileReviewedD3ARaisesRequiredTotalToTwelve()
            throws Exception {
        var holder = load(P4C2PhaseTypes.PLAYER_PACKAGE
                + "PlayerSkillAttachmentGameTests");
        var methods = Arrays.stream(holder.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GameTest.class))
                .toList();
        var allMain = javaSources(MAIN_JAVA).stream()
                .map(P4C2AApiGateTest::read)
                .collect(Collectors.joining("\n"));
        var holderAnnotation = holder.getAnnotation(GameTestHolder.class);

        assertAll(
                () -> assertTrue(Modifier.isPublic(holder.getModifiers())),
                () -> assertTrue(Modifier.isFinal(holder.getModifiers())),
                () -> assertEquals(2, methods.size()),
                () -> assertTrue(methods.stream().allMatch(method ->
                        Modifier.isPublic(method.getModifiers())
                                && Modifier.isStatic(method.getModifiers()))),
                () -> assertTrue(holderAnnotation != null
                        && holderAnnotation.value().equals(Gramarye.MOD_ID)),
                () -> assertEquals(12, occurrences(allMain, "@GameTest(")));
    }

    @Test
    void c2ASourcesKeepLaterCompositionAbsentWhileC2BStaysTestOnly()
            throws Exception {
        var c2Source = P4C2PhaseTypes.PLAYER_SOURCE_FILE_NAMES.stream()
                .map(PLAYER_ROOT::resolve)
                .map(P4C2AApiGateTest::read)
                .map(P4C2AApiGateTest::withoutCommentsAndLiterals)
                .collect(Collectors.joining("\n"));
        var allProduction = javaSources(MAIN_JAVA).stream()
                .map(P4C2AApiGateTest::read)
                .map(P4C2AApiGateTest::withoutCommentsAndLiterals)
                .collect(Collectors.joining("\n"));
        var productionWithoutGroupedStoreAudit = javaSources(MAIN_JAVA).stream()
                .filter(path -> !path.toAbsolutePath().normalize().equals(
                        MAIN_JAVA.resolve(
                                "com/yo1no/gramarye/magic/definition/store/"
                                        + "P4E1GroupedStoreAudit.java")
                                .toAbsolutePath().normalize()))
                .map(P4C2AApiGateTest::read)
                .map(P4C2AApiGateTest::withoutCommentsAndLiterals)
                .collect(Collectors.joining("\n"));
        var journalOwners = javaSources(MAIN_JAVA).stream()
                .filter(path -> withoutCommentsAndLiterals(read(path))
                        .contains("PendingAttachmentJournal"))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
        var reviewedD1JournalOwners = new java.util.HashSet<>(
                P4DPhaseTypes.NEW_STORE_SOURCE_FILE_NAMES);
        reviewedD1JournalOwners.addAll(P4DPhaseTypes.MODIFIED_STORE_SOURCE_FILE_NAMES);
        reviewedD1JournalOwners.add("SkillSubmissionRecoveryGameTests.java");
        reviewedD1JournalOwners.add("P4E1PendingJournalObservation.java");
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var workflow = read(PROJECT_ROOT.resolve(".github/workflows/build.yml"));

        for (var forbidden : List.of(
                "SkillDefinitionSubmissionService",
                "SkillDefinitionStore",
                "SkillRetentionRootSnapshot",
                "OfflineRoot",
                "RootCollector",
                "RootIndex",
                "Reconciliation",
                "CustomPacketPayload",
                "StreamCodec",
                "PayloadRegistrar",
                "PacketDistributor",
                "net.minecraft.client",
                "PlayerEvent.Clone")) {
            assertFalse(c2Source.contains(forbidden), () -> "C2-A source contains " + forbidden);
        }
        for (var forbidden : List.of(
                "OfflineRoot",
                "RootCollector",
                "RootIndex",
                "CustomPacketPayload",
                "PayloadRegistrar",
                "PacketDistributor")) {
            assertFalse(allProduction.contains(forbidden),
                    () -> "Later phase production surface appeared: " + forbidden);
        }
        assertFalse(productionWithoutGroupedStoreAudit.contains("Reconciliation"),
                "reconciliation escaped the exact B2-A grouped-audit owner");
        assertTrue(reviewedD1JournalOwners.containsAll(journalOwners),
                () -> "Pending journal escaped exact D1 allowlist: " + journalOwners);
        assertAll(
                () -> assertFalse(Pattern.compile("\\.\\s*commit\\s*\\(")
                        .matcher(c2Source).find()),
                () -> assertFalse(Pattern.compile("\\.\\s*reclaim\\s*\\(")
                        .matcher(c2Source).find()),
                () -> assertFalse(c2Source.contains(".sync(")),
                () -> assertFalse(allProduction.contains("p4C2Probe")),
                () -> assertFalse(allProduction.contains("p4C2GameTest")),
                () -> assertFalse(allProduction.contains("P4D3ProbeMain")),
                () -> assertFalse(allProduction.contains("P4D3MemoryGameTests")),
                () -> assertTrue(build.contains("sourceSets.create('p4C2Probe')")),
                () -> assertTrue(build.contains("sourceSets.create('p4C2GameTest')")),
                () -> assertTrue(build.contains("p4C2FixedHeapGate")),
                () -> assertTrue(workflow.contains("p4-c-memory-gates:")),
                () -> assertTrue(Files.isDirectory(
                        PROJECT_ROOT.resolve("src/p4C2Probe/java"))),
                () -> assertTrue(Files.isDirectory(
                        PROJECT_ROOT.resolve("src/p4C2GameTest/java"))));
    }

    @Test
    void compositionRootRegistersOnePrivatePlayerServiceWithoutGlobalGetter() {
        var root = read(MAIN_JAVA.resolve("com/yo1no/gramarye/Gramarye.java"));

        assertAll(
                () -> assertTrue(root.contains(
                        "private final PlayerSkillAttachmentService playerSkillAttachmentService;")),
                () -> assertTrue(root.contains(
                        "PlayerSkillAttachmentService.registerOn(modBus)")),
                () -> assertFalse(root.contains("public PlayerSkillAttachmentService")));
    }

    private static boolean exposesForbiddenPublicType(Type type) {
        if (type instanceof Class<?> raw) {
            var name = raw.getName();
            return Tag.class.isAssignableFrom(raw)
                    || AttachmentType.class.isAssignableFrom(raw)
                    || raw == IAttachmentHolder.class
                    || name.contains("PlayerSkillAttachmentState")
                    || name.contains("PlayerSkillAttachmentReady")
                    || name.contains("PlayerSkillAttachmentQuarantine")
                    || name.contains("EncodedPlayerSkillAttachment")
                    || name.contains("PlayerSkillAttachmentBuildResult")
                    || name.contains("ObservedPlayerSkillAttachment");
        }
        if (type instanceof ParameterizedType parameterized) {
            return exposesForbiddenPublicType(parameterized.getRawType())
                    || Arrays.stream(parameterized.getActualTypeArguments())
                            .anyMatch(P4C2AApiGateTest::exposesForbiddenPublicType);
        }
        if (type instanceof GenericArrayType array) {
            return exposesForbiddenPublicType(array.getGenericComponentType());
        }
        if (type instanceof WildcardType wildcard) {
            return Arrays.stream(wildcard.getUpperBounds())
                            .anyMatch(P4C2AApiGateTest::exposesForbiddenPublicType)
                    || Arrays.stream(wildcard.getLowerBounds())
                            .anyMatch(P4C2AApiGateTest::exposesForbiddenPublicType);
        }
        if (type instanceof TypeVariable<?> variable) {
            return Arrays.stream(variable.getBounds())
                    .anyMatch(P4C2AApiGateTest::exposesForbiddenPublicType);
        }
        return false;
    }

    private static void assertOrdered(String source, String... fragments) {
        var previous = -1;
        for (var fragment : fragments) {
            var current = source.indexOf(fragment, previous + 1);
            assertTrue(current >= 0, "Missing ordered fragment: " + fragment);
            assertTrue(current > previous, "Out-of-order fragment: " + fragment);
            previous = current;
        }
    }

    private static Set<String> topLevelDeclarations(List<Path> sources) {
        return sources.stream()
                .flatMap(path -> topLevelDeclarations(path).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> topLevelDeclarations(Path path) {
        var source = withoutCommentsAndLiterals(read(path));
        var declarations = new java.util.ArrayList<String>();
        var braceDepth = 0;
        for (var line : source.split("\\R", -1)) {
            if (braceDepth == 0) {
                var matcher = TOP_LEVEL_TYPE_DECLARATION.matcher(line);
                if (matcher.find()) {
                    declarations.add(matcher.group(1));
                }
            }
            for (var index = 0; index < line.length(); index++) {
                if (line.charAt(index) == '{') {
                    braceDepth++;
                } else if (line.charAt(index) == '}') {
                    braceDepth--;
                }
            }
        }
        return List.copyOf(declarations);
    }

    private static List<Path> javaSources(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static Set<String> relativeFilesContaining(List<Path> sources, String fragment) {
        return sources.stream()
                .filter(path -> read(path).contains(fragment))
                .map(P4C2AApiGateTest::relative)
                .collect(Collectors.toSet());
    }

    private static String relative(Path path) {
        return MAIN_JAVA.relativize(path).toString().replace(File.separatorChar, '/');
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        for (var index = source.indexOf(fragment); index >= 0;
                index = source.indexOf(fragment, index + fragment.length())) {
            count++;
        }
        return count;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, P4C2AApiGateTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Missing reviewed P4-C2-A class: " + name, exception);
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
        return source
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ")
                .replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"")
                .replaceAll("'(?:\\\\.|[^'\\\\])*'", "''");
    }
}
