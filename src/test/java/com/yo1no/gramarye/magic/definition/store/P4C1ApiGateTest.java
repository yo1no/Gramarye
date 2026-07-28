package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
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
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.junit.jupiter.api.Test;

/** Phase-local API, source-ownership, and later-domain gate for P4-C1. */
class P4C1ApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path PLAYER_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/player");
    private static final Path DOCUMENT_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/document");
    private static final Pattern TOP_LEVEL_TYPE_DECLARATION = Pattern.compile(
            "(?m)^(?:(?:public|protected|private|abstract|final|non-sealed|sealed|static)\\s+)*"
                    + "(?:class|record|interface|enum)\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final Pattern RAW_GENERIC_DECLARATION = Pattern.compile(
            "\\b(?:Class|Collection|DataResult|Dynamic|DynamicOps|Iterable|List|Map|Optional|"
                    + "RegistryOps|Set|Stream|Supplier)\\s+"
                    + "[A-Za-z_$][A-Za-z0-9_$]*\\s*(?:[=;,)\\[])");
    private static final Pattern UNCHECKED_STYLE_CAST = Pattern.compile(
            "\\(\\s*(?:Class|Collection|DataResult|Dynamic|DynamicOps|Iterable|List|Map|"
                    + "Optional|RegistryOps|Set|Stream|Supplier)\\s*\\)");

    @Test
    void exactReviewedTypesExistWithOnePublicTopLevelDraftFacade() throws Exception {
        // C2-A adds registration/service/GameTest files to the same package. Keep this C1 gate
        // pinned to the exact physical-model files that C1 reviewed; P4C2AApiGateTest owns the
        // union of the two phase allowlists.
        var playerSources = P4C1PhaseTypes.PLAYER_SOURCE_FILE_NAMES.stream()
                .map(PLAYER_ROOT::resolve)
                .sorted()
                .toList();
        var documentSources = P4C1PhaseTypes.DOCUMENT_SOURCE_FILE_NAMES.stream()
                .map(DOCUMENT_ROOT::resolve)
                .sorted()
                .toList();
        var playerDeclarations = topLevelDeclarations(playerSources);
        var documentDeclarations = topLevelDeclarations(documentSources);
        var loadedPlayer = P4C1PhaseTypes.PLAYER_TOP_LEVEL_TYPE_NAMES.stream()
                .map(name -> load(P4C1PhaseTypes.PLAYER_PACKAGE + name))
                .toList();
        var loadedDocument = P4C1PhaseTypes.DOCUMENT_TOP_LEVEL_TYPE_NAMES.stream()
                .map(name -> load(P4C1PhaseTypes.DOCUMENT_PACKAGE + name))
                .toList();
        var publicTopLevel = java.util.stream.Stream.concat(
                        loadedPlayer.stream(), loadedDocument.stream())
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(
                        P4C1PhaseTypes.PLAYER_SOURCE_FILE_NAMES,
                        playerSources.stream()
                                .map(path -> path.getFileName().toString())
                                .collect(Collectors.toSet())),
                () -> assertTrue(documentSources.stream().allMatch(Files::isRegularFile)),
                () -> assertEquals(
                        P4C1PhaseTypes.PLAYER_TOP_LEVEL_TYPE_NAMES, playerDeclarations),
                () -> assertEquals(
                        P4C1PhaseTypes.DOCUMENT_TOP_LEVEL_TYPE_NAMES, documentDeclarations),
                () -> assertEquals(P4C1PhaseTypes.PUBLIC_TOP_LEVEL_TYPE_NAMES, publicTopLevel),
                () -> assertTrue(loadedPlayer.stream().noneMatch(type ->
                        Modifier.isPublic(type.getModifiers())
                                || Modifier.isProtected(type.getModifiers())
                                || Modifier.isPrivate(type.getModifiers()))),
                () -> assertTrue(Modifier.isPublic(
                        SkillDraftPersistenceFacade.class.getModifiers())),
                () -> assertTrue(Modifier.isFinal(
                        SkillDraftPersistenceFacade.class.getModifiers())));
    }

    @Test
    void draftFacadeIsTheOnlyNewPublicSeamAndExposesOnlyOpaqueNestedResults() {
        var publicMethods = Arrays.stream(SkillDraftPersistenceFacade.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();
        var nested = Arrays.stream(SkillDraftPersistenceFacade.class.getDeclaredClasses())
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .toList();

        assertAll(
                () -> assertEquals(
                        Set.of("encodeCurrent", "loadAlwaysMigrating"),
                        publicMethods.stream().map(method -> method.getName())
                                .collect(Collectors.toSet())),
                () -> assertTrue(publicMethods.stream()
                        .allMatch(method -> Modifier.isStatic(method.getModifiers()))),
                () -> assertEquals(
                        P4C1PhaseTypes.FACADE_PUBLIC_NESTED_TYPE_NAMES,
                        nested.stream().map(Class::getSimpleName).collect(Collectors.toSet())),
                () -> assertTrue(java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(SkillDraftPersistenceFacade.class),
                                nested.stream())
                        .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .noneMatch(method -> exposesPhysicalTree(method.getGenericReturnType())
                                || Arrays.stream(method.getGenericParameterTypes())
                                        .anyMatch(P4C1ApiGateTest::exposesPhysicalTree))));
    }

    @Test
    void serializerUsesTagAsItsFirstGenericAndNeverReturnsNull() throws Exception {
        var serializer = load(P4C1PhaseTypes.PLAYER_PACKAGE
                + "PlayerSkillAttachmentSerializer");
        var state = load(P4C1PhaseTypes.PLAYER_PACKAGE
                + "PlayerSkillAttachmentState");
        var generic = Arrays.stream(serializer.getGenericInterfaces())
                .filter(ParameterizedType.class::isInstance)
                .map(ParameterizedType.class::cast)
                .filter(type -> type.getRawType() == IAttachmentSerializer.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing typed IAttachmentSerializer"));
        var read = Arrays.stream(serializer.getDeclaredMethods())
                .filter(method -> method.getName().equals("read"))
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .findFirst()
                .orElseThrow();
        var write = Arrays.stream(serializer.getDeclaredMethods())
                .filter(method -> method.getName().equals("write"))
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .findFirst()
                .orElseThrow();
        var source = read(PLAYER_ROOT.resolve("PlayerSkillAttachmentSerializer.java"));

        assertAll(
                () -> assertEquals(List.of(Tag.class, state),
                        Arrays.asList(generic.getActualTypeArguments())),
                () -> assertEquals(state, read.getReturnType()),
                () -> assertEquals(
                        List.of(IAttachmentHolder.class, Tag.class,
                                net.minecraft.core.HolderLookup.Provider.class),
                        Arrays.asList(read.getParameterTypes())),
                () -> assertEquals(Tag.class, write.getReturnType()),
                () -> assertEquals(
                        List.of(state, net.minecraft.core.HolderLookup.Provider.class),
                        Arrays.asList(write.getParameterTypes())),
                () -> assertTrue(Modifier.isPublic(read.getModifiers())),
                () -> assertTrue(Modifier.isPublic(write.getModifiers())),
                () -> assertFalse(withoutCommentsAndLiterals(source).contains("return null")),
                () -> assertFalse(source.contains("IAttachmentSerializer<CompoundTag")));
    }

    @Test
    void writeAnyTagCounterHasOneProductionCoordinateAndNoBufferingFallback()
            throws Exception {
        var production = javaSources(MAIN_JAVA);
        var counter = load(P4C1PhaseTypes.PLAYER_PACKAGE + "BoundedCountingDataOutput");
        var result = load(P4C1PhaseTypes.PLAYER_PACKAGE + "AttachmentTagSizeResult");
        var counterSource = read(PLAYER_ROOT.resolve("BoundedCountingDataOutput.java"));
        var sizeSource = read(PLAYER_ROOT.resolve("AttachmentTagSize.java"));

        assertAll(
                () -> assertEquals(
                        Set.of(
                                "com/yo1no/gramarye/magic/definition/document/StrictNbtTreeCodec.java",
                                "com/yo1no/gramarye/magic/definition/migration/"
                                        + "OpaqueSkillDocumentMigrationFacade.java",
                                "com/yo1no/gramarye/magic/definition/player/AttachmentTagSize.java"),
                        relativeFilesContaining(production, "NbtIo.writeAnyTag(")),
                () -> assertTrue(relativeFilesContaining(production, "writeUnnamedTag").isEmpty()),
                () -> assertFalse(counterSource.contains("ByteArrayOutputStream")),
                () -> assertFalse(sizeSource.contains("byte[]")),
                () -> assertTrue(Modifier.isFinal(counter.getModifiers())),
                () -> assertFalse(Modifier.isPublic(counter.getModifiers())),
                () -> assertEquals(
                        Set.of(long.class),
                        Arrays.stream(counter.getDeclaredFields())
                                .filter(field -> !field.isSynthetic())
                                .map(field -> field.getType())
                                .collect(Collectors.toSet())),
                () -> assertTrue(result.isSealed()),
                () -> assertEquals(
                        Set.of("WithinLimit", "Exceeded"),
                        Arrays.stream(result.getPermittedSubclasses())
                                .map(Class::getSimpleName)
                                .collect(Collectors.toSet())));
    }

    @Test
    void fiveAuthoritativeCeilingsAreExactAndHaveNoSynonymDefinitions() throws Exception {
        var ceilingSource = read(MAIN_JAVA.resolve(
                "com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java"));
        var production = javaSources(MAIN_JAVA);
        var ceilingPath = "com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java";
        var names = Set.of(
                "MAX_PLAYER_DRAFTS",
                "MAX_PLAYER_LATEST_STATES",
                "MAX_PLAYER_EQUIPPED_REFERENCES",
                "MAX_PLAYER_DRAFT_ENTRY_ENCODED_BYTES",
                "MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES");

        assertAll(
                () -> assertEquals(32, MagicSafetyCeilings.MAX_PLAYER_DRAFTS),
                () -> assertEquals(256, MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES),
                () -> assertEquals(64, MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES),
                () -> assertEquals(1_114_112,
                        MagicSafetyCeilings.MAX_PLAYER_DRAFT_ENTRY_ENCODED_BYTES),
                () -> assertEquals(16_777_216,
                        MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES),
                () -> assertTrue(names.stream().allMatch(name ->
                        occurrences(ceilingSource, "public static final int " + name + " =") == 1)),
                () -> assertEquals(
                        Set.of(
                                ceilingPath,
                                "com/yo1no/gramarye/magic/definition/player/"
                                        + "PlayerSkillAttachmentPersistenceBridge.java"),
                        relativeFilesContaining(production, "MAX_PLAYER_DRAFTS")),
                () -> assertEquals(
                        Set.of(
                                ceilingPath,
                                "com/yo1no/gramarye/magic/definition/player/"
                                        + "PlayerSkillAttachmentPersistenceBridge.java"),
                        relativeFilesContaining(production, "MAX_PLAYER_LATEST_STATES")),
                () -> assertEquals(
                        Set.of(
                                ceilingPath,
                                "com/yo1no/gramarye/magic/definition/player/"
                                        + "PlayerSkillAttachmentPersistenceBridge.java"),
                        relativeFilesContaining(production,
                                "MAX_PLAYER_EQUIPPED_REFERENCES")),
                () -> assertEquals(
                        Set.of(
                                ceilingPath,
                                "com/yo1no/gramarye/magic/definition/document/"
                                        + "SkillDraftPersistenceFacade.java"),
                        relativeFilesContaining(production,
                                "MAX_PLAYER_DRAFT_ENTRY_ENCODED_BYTES")),
                () -> assertEquals(
                        Set.of(
                                ceilingPath,
                                "com/yo1no/gramarye/magic/definition/player/AttachmentTagSize.java"),
                        relativeFilesContaining(production,
                                "MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES")),
                () -> assertFalse(ceilingSource.contains("MAX_PLAYER_ATTACHMENT_BYTES")),
                () -> assertFalse(ceilingSource.contains("MAX_PLAYER_ROOT_PROJECTION")),
                () -> assertFalse(ceilingSource.contains("MAX_EDITOR_SLOT")));
    }

    @Test
    void mutationGenerationHasOneCheckedSuccessorOwnerAndUsesOnlyIntPolicy()
            throws Exception {
        var c1Sources = reviewedSources();
        var arithmeticOwners = c1Sources.stream()
                .filter(path -> Pattern.compile(
                                "OptionalInt\\.of\\s*\\(\\s*current\\s*\\+\\s*1\\s*\\)")
                        .matcher(withoutCommentsAndLiterals(read(path)))
                        .find())
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
        var generationSources = List.of(
                PLAYER_ROOT.resolve("MutationGeneration.java"),
                PLAYER_ROOT.resolve("PlayerLatestState.java"),
                PLAYER_ROOT.resolve("PlayerSkillAttachmentCodecs.java"),
                PLAYER_ROOT.resolve("PlayerSkillAttachmentPersistenceBridge.java"),
                PLAYER_ROOT.resolve("PlayerSkillAttachmentSchema.java"));
        var source = generationSources.stream().map(P4C1ApiGateTest::read)
                .collect(Collectors.joining("\n"));

        assertAll(
                () -> assertEquals(Set.of("MutationGeneration.java"), arithmeticOwners),
                () -> assertFalse(source.contains("long mutationGeneration")),
                () -> assertFalse(source.contains("long expectedGeneration")),
                () -> assertFalse(source.contains("long targetGeneration")),
                () -> assertFalse(source.contains("Long.MAX_VALUE")),
                () -> assertTrue(read(PLAYER_ROOT.resolve("MutationGeneration.java"))
                        .contains("current == Integer.MAX_VALUE")));
    }

    @Test
    void c1SourcesHaveNoRawUncheckedFixtureOrC2CompositionSurface() throws Exception {
        var raw = new java.util.ArrayList<String>();
        var casts = new java.util.ArrayList<String>();
        var fixtures = new java.util.ArrayList<Path>();
        var sources = reviewedSources();
        var joined = sources.stream()
                .map(P4C1ApiGateTest::read)
                .map(P4C1ApiGateTest::withoutCommentsAndLiterals)
                .collect(Collectors.joining("\n"));

        for (var source : sources) {
            var contents = read(source);
            var code = withoutCommentsAndLiterals(contents);
            collectMatches(source, RAW_GENERIC_DECLARATION, code, raw);
            collectMatches(source, UNCHECKED_STYLE_CAST, code, casts);
            if (contents.contains("@SuppressWarnings")
                    || contents.contains("org.junit")
                    || Pattern.compile(
                                    "\\b(?:class|record|interface|enum)\\s+"
                                            + "[A-Za-z0-9_]*(?:Test|Fixture|Fake|Dummy|Noop|Stub)\\b")
                            .matcher(code).find()) {
                fixtures.add(source);
            }
        }

        // Keep the exact C1 files isolated from registration, Player mutation, lifecycle, P4-D/E,
        // and network even after those reviewed C2-A files join the same package.
        for (var forbidden : List.of(
                "AttachmentType",
                "copyOnDeath",
                "ServerPlayer",
                "PlayerEvent",
                ".getData(",
                ".setData(",
                "ServerStartingEvent",
                "ServerStoppedEvent",
                "PendingAttachmentJournal",
                "PreparedPlayerSkillTransition",
                "SkillDefinitionSubmissionService",
                "SkillDefinitionStore",
                "SkillRetentionRootSnapshot",
                "RootProjection",
                "RootCollector",
                "RootIndex",
                "OfflineRoot",
                "Reconciliation",
                "CustomPacketPayload",
                "StreamCodec",
                "PayloadRegistrar",
                "PacketDistributor",
                "net.minecraft.client")) {
            assertFalse(joined.contains(forbidden), () -> "P4-C1 source contains " + forbidden);
        }
        assertAll(
                () -> assertFalse(Pattern.compile("\\.\\s*commit\\s*\\(").matcher(joined).find()),
                () -> assertTrue(raw.isEmpty(), () -> "Raw declarations: " + raw),
                () -> assertTrue(casts.isEmpty(), () -> "Unchecked-style casts: " + casts),
                () -> assertTrue(fixtures.isEmpty(), () -> "Production fixtures: " + fixtures),
                () -> assertFalse(read(PROJECT_ROOT.resolve("build.gradle"))
                        .contains("p4C1")),
                () -> assertFalse(read(PROJECT_ROOT.resolve(".github/workflows/build.yml"))
                        .contains("p4-c-memory")));
    }

    private static List<Path> reviewedSources() throws Exception {
        var result = new java.util.ArrayList<Path>();
        P4C1PhaseTypes.PLAYER_SOURCE_FILE_NAMES.stream()
                .map(PLAYER_ROOT::resolve)
                .sorted()
                .forEach(result::add);
        P4C1PhaseTypes.DOCUMENT_SOURCE_FILE_NAMES.stream()
                .map(DOCUMENT_ROOT::resolve)
                .sorted()
                .forEach(result::add);
        return List.copyOf(result);
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
            if (braceDepth < 0) {
                throw new AssertionError("Unbalanced braces in " + path);
            }
        }
        if (braceDepth != 0) {
            throw new AssertionError("Unbalanced braces in " + path);
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
                .map(MAIN_JAVA::relativize)
                .map(Path::toString)
                .map(name -> name.replace(File.separatorChar, '/'))
                .collect(Collectors.toSet());
    }

    private static boolean exposesPhysicalTree(Type type) {
        if (type instanceof Class<?> raw) {
            var name = raw.getName();
            return Tag.class.isAssignableFrom(raw)
                    || name.contains("RawTreeEnvelope")
                    || name.contains("PhysicalSkillDraft")
                    || name.contains("Dynamic");
        }
        if (type instanceof ParameterizedType parameterized) {
            return exposesPhysicalTree(parameterized.getRawType())
                    || Arrays.stream(parameterized.getActualTypeArguments())
                            .anyMatch(P4C1ApiGateTest::exposesPhysicalTree);
        }
        if (type instanceof GenericArrayType array) {
            return exposesPhysicalTree(array.getGenericComponentType());
        }
        if (type instanceof WildcardType wildcard) {
            return Arrays.stream(wildcard.getUpperBounds())
                            .anyMatch(P4C1ApiGateTest::exposesPhysicalTree)
                    || Arrays.stream(wildcard.getLowerBounds())
                            .anyMatch(P4C1ApiGateTest::exposesPhysicalTree);
        }
        if (type instanceof TypeVariable<?> variable) {
            return Arrays.stream(variable.getBounds())
                    .anyMatch(P4C1ApiGateTest::exposesPhysicalTree);
        }
        return false;
    }

    private static void collectMatches(
            Path path, Pattern pattern, String source, List<String> findings) {
        pattern.matcher(source).results()
                .map(match -> path.getFileName() + ":" + match.group())
                .forEach(findings::add);
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
            return Class.forName(name, false, P4C1ApiGateTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Missing reviewed P4-C1 class: " + name, exception);
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
