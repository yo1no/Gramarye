package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.validation.ValidatedSkillDefinition;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Exact S1 Java surface, bootstrap, isolation, and source-family guard. */
final class P9S1BoundaryTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path ROOT_MAIN = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye");
    private static final Path MAIN_RESOURCES = PROJECT_ROOT.resolve("src/main/resources");
    private static final Path ROOT_CLASSES = PROJECT_ROOT.resolve(
            "build/classes/java/main/com/yo1no/gramarye");
    private static final Path CONTENT_SOURCE = ROOT_MAIN.resolve("P9StarterSkillContent.java");
    private static final Path GRAMARYE_SOURCE = ROOT_MAIN.resolve("Gramarye.java");

    private static final Set<String> P9_S1_SOURCE_FILES = Set.of(
            "P9ActiveCastTriggerPayloadV0.java",
            "P9ActiveCastTriggerType.java",
            "P9DamageActionPayloadV0.java",
            "P9DamageActionType.java",
            "P9EffectHitTriggerPayloadV0.java",
            "P9EffectHitTriggerType.java",
            "P9SpawnProjectileActionPayloadV0.java",
            "P9SpawnProjectileActionType.java",
            "P9StarterSkillContent.java");
    private static final Map<String, Set<String>> TOP_LEVEL_TYPES = Map.ofEntries(
            Map.entry(
                    "P9ActiveCastTriggerPayloadV0.java",
                    Set.of("P9ActiveCastTriggerPayloadV0")),
            Map.entry("P9ActiveCastTriggerType.java", Set.of("P9ActiveCastTriggerType")),
            Map.entry("P9DamageActionPayloadV0.java", Set.of("P9DamageActionPayloadV0")),
            Map.entry("P9DamageActionType.java", Set.of("P9DamageActionType")),
            Map.entry(
                    "P9EffectHitTriggerPayloadV0.java",
                    Set.of("P9EffectHitTriggerPayloadV0")),
            Map.entry("P9EffectHitTriggerType.java", Set.of("P9EffectHitTriggerType")),
            Map.entry(
                    "P9SpawnProjectileActionPayloadV0.java",
                    Set.of("P9SpawnProjectileActionPayloadV0")),
            Map.entry(
                    "P9SpawnProjectileActionType.java",
                    Set.of("P9SpawnProjectileActionType")),
            Map.entry(
                    "P9StarterSkillContent.java",
                    Set.of("P9StarterSkillContent", "StarterGameplayFingerprintV0")));
    private static final Pattern TOP_LEVEL_TYPE = Pattern.compile(
            "(?m)^(?:public\\s+)?(?:(?:final|sealed|non-sealed|abstract)\\s+)*"
                    + "(?:class|interface|record|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final Pattern PUBLIC_P9_TOP_LEVEL = Pattern.compile(
            "(?m)^public\\s+(?:(?:final|sealed|non-sealed|abstract)\\s+)*"
                    + "(?:class|interface|record|enum)\\s+P9[A-Za-z0-9_$]*\\b");
    private static final Pattern REGISTRATION_CALL = Pattern.compile(
            "\\bP9StarterSkillContent\\s*\\.\\s*registerDefinitionTypes\\s*"
                    + "\\(\\s*\\)\\s*;");
    private static final Set<String> P9_S1_CLASS_AFTER_SET = Set.of(
            "P9ActiveCastTriggerPayloadV0.class",
            "P9ActiveCastTriggerType.class",
            "P9DamageActionPayloadV0.class",
            "P9DamageActionType.class",
            "P9EffectHitTriggerPayloadV0.class",
            "P9EffectHitTriggerType.class",
            "P9SpawnProjectileActionPayloadV0.class",
            "P9SpawnProjectileActionType.class",
            "P9StarterSkillContent.class",
            "P9StarterSkillContent$1.class",
            "P9StarterSkillContent$2.class",
            "P9StarterSkillContent$3.class",
            "StarterGameplayFingerprintV0.class");

    @Test
    void exactS1SourceFamiliesExistWithNoPublicP9TopLevelType() throws IOException {
        Set<String> actualFiles;
        try (var paths = Files.list(ROOT_MAIN)) {
            actualFiles = paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("P9") && name.endsWith(".java"))
                    .collect(Collectors.toUnmodifiableSet());
        }

        var actualTypes = new LinkedHashMap<String, Set<String>>();
        for (var entry : TOP_LEVEL_TYPES.entrySet()) {
            var source = read(ROOT_MAIN.resolve(entry.getKey()));
            var matcher = TOP_LEVEL_TYPE.matcher(source);
            var names = new java.util.LinkedHashSet<String>();
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
            actualTypes.put(entry.getKey(), Set.copyOf(names));
        }

        var publicP9Types = javaSources(PROJECT_ROOT.resolve("src/main/java")).stream()
                .filter(path -> PUBLIC_P9_TOP_LEVEL.matcher(read(path)).find())
                .map(path -> PROJECT_ROOT.relativize(path).toString())
                .toList();

        assertAll(
                () -> assertEquals(P9_S1_SOURCE_FILES, actualFiles),
                () -> assertEquals(TOP_LEVEL_TYPES, actualTypes),
                () -> assertEquals(List.of(), publicP9Types));
    }

    @Test
    void compiledS1ClassFamiliesMatchTheExactCurrentAfterSet() throws IOException {
        Set<String> actualClasses;
        try (var paths = Files.list(ROOT_CLASSES)) {
            actualClasses = paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("P9")
                            || name.startsWith("StarterGameplayFingerprintV0"))
                    .collect(Collectors.toUnmodifiableSet());
        }
        assertEquals(P9_S1_CLASS_AFTER_SET, actualClasses);
    }

    @Test
    void fourDescriptorsArePackagePrivateFinalStatelessSingletonsWithFiveOverrides()
            throws ReflectiveOperationException {
        assertDescriptor(
                P9ActiveCastTriggerType.class,
                P9ActiveCastTriggerType.INSTANCE,
                TriggerType.class,
                P9ActiveCastTriggerPayloadV0.class);
        assertDescriptor(
                P9EffectHitTriggerType.class,
                P9EffectHitTriggerType.INSTANCE,
                TriggerType.class,
                P9EffectHitTriggerPayloadV0.class);
        assertDescriptor(
                P9SpawnProjectileActionType.class,
                P9SpawnProjectileActionType.INSTANCE,
                ActionType.class,
                P9SpawnProjectileActionPayloadV0.class);
        assertDescriptor(
                P9DamageActionType.class,
                P9DamageActionType.INSTANCE,
                ActionType.class,
                P9DamageActionPayloadV0.class);
    }

    @Test
    void payloadAndFingerprintAfterSetsHaveTheExactClosedPackagePrivateShape() {
        assertAll(
                () -> assertFalse(Modifier.isPublic(
                        P9ActiveCastTriggerPayloadV0.class.getModifiers())),
                () -> assertTrue(P9ActiveCastTriggerPayloadV0.class.isEnum()),
                () -> assertEquals(
                        List.of("INSTANCE"),
                        Arrays.stream(P9ActiveCastTriggerPayloadV0.values())
                                .map(Enum::name)
                                .toList()),
                () -> assertTrue(TriggerPayload.class.isAssignableFrom(
                        P9ActiveCastTriggerPayloadV0.class)),
                () -> assertRecord(
                        P9SpawnProjectileActionPayloadV0.class,
                        ActionPayload.class,
                        List.of("profileCode:int", "manaCost:long")),
                () -> assertRecord(
                        P9EffectHitTriggerPayloadV0.class,
                        TriggerPayload.class,
                        List.of(
                                "sourceNodeIndex:int",
                                "sourceOutputOrdinal:int",
                                "includeDerived:boolean")),
                () -> assertRecord(
                        P9DamageActionPayloadV0.class,
                        ActionPayload.class,
                        List.of("magnitude:long", "manaCost:long")),
                () -> assertRecord(
                        StarterGameplayFingerprintV0.class,
                        Record.class,
                        List.of(
                                "firstNodeIndex:int",
                                "activeCastTypeId:" + ResourceLocation.class.getName(),
                                "spawnProjectileTypeId:" + ResourceLocation.class.getName(),
                                "profileCode:int",
                                "spawnManaCost:long",
                                "firstOutputOrdinal:int",
                                "firstOutputKind:com.yo1no.gramarye.magic.capability.ActionOutputKind",
                                "secondNodeIndex:int",
                                "effectHitTypeId:" + ResourceLocation.class.getName(),
                                "sourceSelection:com.yo1no.gramarye.magic.definition.inspection.SourceSelection",
                                "sourceNodeIndex:int",
                                "sourceOutputOrdinal:int",
                                "includeDerived:boolean",
                                "damageTypeId:" + ResourceLocation.class.getName(),
                                "magnitude:long",
                                "damageManaCost:long")));
    }

    @Test
    void registrationOwnerHasNoPublicDeltaAndBootstrapIsExactAndAdjacent()
            throws IOException, ReflectiveOperationException {
        var owner = P9StarterSkillContent.class;
        var registration = owner.getDeclaredMethod("registerDefinitionTypes");
        var builder = owner.getDeclaredMethod("canonicalDraft", SkillId.class);
        var fingerprint = owner.getDeclaredMethod(
                "fingerprintOf", ValidatedSkillDefinition.class);
        var predicate = owner.getDeclaredMethod(
                "hasCanonicalGameplayFingerprint", ValidatedSkillDefinition.class);
        var source = read(GRAMARYE_SOURCE);
        var registrationCallers = javaSources(PROJECT_ROOT.resolve("src/main/java")).stream()
                .filter(path -> patternOccurrences(REGISTRATION_CALL, read(path)) > 0)
                .collect(Collectors.toUnmodifiableMap(
                        path -> PROJECT_ROOT.relativize(path).toString(),
                        path -> patternOccurrences(REGISTRATION_CALL, read(path))));

        assertAll(
                () -> assertFalse(Modifier.isPublic(owner.getModifiers())),
                () -> assertTrue(Modifier.isFinal(owner.getModifiers())),
                () -> assertEquals(1, owner.getDeclaredConstructors().length),
                () -> assertTrue(Modifier.isPrivate(
                        owner.getDeclaredConstructors()[0].getModifiers())),
                () -> assertEquals(
                        0, owner.getDeclaredConstructors()[0].getParameterCount()),
                () -> assertEquals(0, publicOrProtectedDeclaredMembers(owner)),
                () -> assertPackageStatic(registration, void.class),
                () -> assertPackageStatic(builder, SkillDraft.class, SkillId.class),
                () -> assertPackageStatic(
                        fingerprint, Optional.class, ValidatedSkillDefinition.class),
                () -> assertPackageStatic(
                        predicate, boolean.class, ValidatedSkillDefinition.class),
                () -> assertEquals(1, patternOccurrences(REGISTRATION_CALL, source)),
                () -> assertEquals(
                        Map.of(
                                "src/main/java/com/yo1no/gramarye/Gramarye.java",
                                1),
                        registrationCallers),
                () -> assertEquals(1, occurrences(source, "MagicRegistries.register(modBus);")),
                () -> assertTrue(Pattern.compile(
                                REGISTRATION_CALL.pattern()
                                        + "\\s*MagicRegistries\\s*\\.\\s*register\\s*"
                                        + "\\(\\s*modBus\\s*\\)\\s*;")
                        .matcher(source)
                        .find()),
                () -> assertEquals(
                        2,
                        occurrences(
                                read(CONTENT_SOURCE),
                                "MagicRegistries.TRIGGER_TYPES.register(")),
                () -> assertEquals(
                        2,
                        occurrences(
                                read(CONTENT_SOURCE),
                                "MagicRegistries.ACTION_TYPES.register(")));
    }

    @Test
    void contentRemainsOutsideLaterSliceRuntimeAndResourceOwnership() throws IOException {
        var contentSources = P9_S1_SOURCE_FILES.stream()
                .sorted()
                .map(name -> read(ROOT_MAIN.resolve(name)))
                .collect(Collectors.joining("\n"));
        for (var forbidden : List.of(
                "magic.definition.player",
                "magic.definition.store",
                "magic.runtime",
                "magic.network",
                "magic.presentation",
                "net.minecraft.server",
                "net.minecraft.world",
                "net.minecraft.client",
                "net.neoforged",
                "java.util.concurrent",
                "java.lang.reflect",
                "java.lang.invoke",
                "Class.forName",
                "ServiceLoader",
                "SkillRuntimeService",
                "ManaTransactionService",
                "PlayerSkillAttachmentService",
                "SkillDefinitionStoreService",
                "CustomPacketPayload",
                "addFreshEntity(",
                ".hurt(",
                "setEquipped(",
                "putDraft(",
                ".submit(")) {
            assertFalse(contentSources.contains(forbidden), forbidden);
        }
        assertFalse(Pattern.compile(
                        "\\bP[5-8][A-Za-z0-9_$]*\\b|"
                                + "\\b(?:P9StarterProjectile|Entity|EntityType|Projectile|"
                                + "ServerPlayer|MinecraftServer|Executor|Thread|ThreadLocal|"
                                + "Future|CompletableFuture|Object)\\b")
                .matcher(contentSources)
                .find());

        var sourceJsonResources = Files.isDirectory(MAIN_RESOURCES)
                ? jsonResources(MAIN_RESOURCES)
                : List.<Path>of();
        assertEquals(List.of(), sourceJsonResources);
    }

    private static void assertDescriptor(
            Class<?> type, Object instance, Class<?> contract, Class<?> payloadType)
            throws ReflectiveOperationException {
        var instanceField = type.getDeclaredField("INSTANCE");
        var genericInterfaces = type.getGenericInterfaces();
        var descriptorInterface = genericInterfaces.length == 1
                && genericInterfaces[0] instanceof ParameterizedType parameterized
                        ? parameterized
                        : null;
        var exposedOperations = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> !method.isSynthetic() && !method.isBridge())
                .filter(method -> Modifier.isPublic(method.getModifiers())
                        || Modifier.isProtected(method.getModifiers()))
                .toList();
        var publicOperationNames = exposedOperations.stream()
                .map(method -> method.getName())
                .collect(Collectors.toUnmodifiableSet());
        assertAll(
                () -> assertFalse(Modifier.isPublic(type.getModifiers())),
                () -> assertTrue(Modifier.isFinal(type.getModifiers())),
                () -> assertTrue(contract.isAssignableFrom(type)),
                () -> assertEquals(1, genericInterfaces.length),
                () -> assertTrue(descriptorInterface != null),
                () -> assertEquals(contract, descriptorInterface.getRawType()),
                () -> assertEquals(
                        List.of(payloadType),
                        List.of(descriptorInterface.getActualTypeArguments())),
                () -> assertEquals(1, type.getDeclaredConstructors().length),
                () -> assertTrue(Modifier.isPrivate(
                        type.getDeclaredConstructors()[0].getModifiers())),
                () -> assertEquals(0, type.getDeclaredConstructors()[0].getParameterCount()),
                () -> assertTrue(Arrays.stream(type.getDeclaredFields())
                        .filter(field -> !field.isSynthetic())
                        .allMatch(field -> Modifier.isStatic(field.getModifiers())
                                && Modifier.isFinal(field.getModifiers()))),
                () -> assertEquals(
                        1,
                        Arrays.stream(type.getDeclaredFields())
                                .filter(field -> field.getType() == type)
                                .count()),
                () -> assertEquals(
                        0,
                        Arrays.stream(type.getDeclaredFields())
                                .filter(field -> Modifier.isPublic(field.getModifiers())
                                        || Modifier.isProtected(field.getModifiers()))
                                .count()),
                () -> assertTrue(Modifier.isStatic(instanceField.getModifiers())),
                () -> assertTrue(Modifier.isFinal(instanceField.getModifiers())),
                () -> assertFalse(Modifier.isPublic(instanceField.getModifiers())),
                () -> assertSame(instance, instanceField.get(null)),
                () -> assertEquals(5, exposedOperations.size()),
                () -> assertTrue(exposedOperations.stream()
                        .allMatch(method -> Modifier.isPublic(method.getModifiers())
                                && !Modifier.isStatic(method.getModifiers()))),
                () -> assertEquals(
                        Set.of(
                                "currentPayloadSchemaVersion",
                                "payloadInspector",
                                "payloadCodec",
                                "capabilities",
                                "validate"),
                        publicOperationNames),
                () -> assertEquals(
                        0,
                        Arrays.stream(type.getDeclaredMethods())
                                .filter(method -> method.getName().equals("payloadMigrationPlan"))
                                .count()));
    }

    private static void assertRecord(
            Class<?> type, Class<?> contract, List<String> expectedComponents) {
        assertAll(
                () -> assertFalse(Modifier.isPublic(type.getModifiers())),
                () -> assertTrue(Modifier.isFinal(type.getModifiers())),
                () -> assertTrue(type.isRecord()),
                () -> assertTrue(contract == Record.class || contract.isAssignableFrom(type)),
                () -> assertEquals(
                        expectedComponents,
                        Arrays.stream(type.getRecordComponents())
                                .map(component -> component.getName()
                                        + ":" + component.getType().getName())
                                .toList()),
                () -> assertTrue(Arrays.stream(type.getDeclaredConstructors())
                        .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                                || Modifier.isProtected(constructor.getModifiers()))));
    }

    private static void assertPackageStatic(
            java.lang.reflect.Method method,
            Class<?> returnType,
            Class<?>... parameterTypes) {
        assertAll(
                () -> assertTrue(Modifier.isStatic(method.getModifiers())),
                () -> assertFalse(Modifier.isPublic(method.getModifiers())),
                () -> assertFalse(Modifier.isProtected(method.getModifiers())),
                () -> assertFalse(Modifier.isPrivate(method.getModifiers())),
                () -> assertEquals(returnType, method.getReturnType()),
                () -> assertEquals(List.of(parameterTypes), List.of(method.getParameterTypes())),
                () -> assertEquals(List.of(), List.of(method.getExceptionTypes())));
    }

    private static long publicOrProtectedDeclaredMembers(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))
                        .count()
                + Arrays.stream(type.getDeclaredFields())
                        .filter(field -> Modifier.isPublic(field.getModifiers())
                                || Modifier.isProtected(field.getModifiers()))
                        .count()
                + Arrays.stream(type.getDeclaredConstructors())
                        .filter(constructor -> Modifier.isPublic(constructor.getModifiers())
                                || Modifier.isProtected(constructor.getModifiers()))
                        .count();
    }

    private static List<Path> jsonResources(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(PROJECT_ROOT::relativize)
                    .sorted()
                    .toList();
        }
    }

    private static List<Path> javaSources(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static int occurrences(String source, String needle) {
        var count = 0;
        for (var index = source.indexOf(needle); index >= 0;
                index = source.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }

    private static int patternOccurrences(Pattern pattern, String source) {
        return Math.toIntExact(pattern.matcher(source).results().count());
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new AssertionError("unable to inspect " + path, failure);
        }
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root unavailable");
    }
}
