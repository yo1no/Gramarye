package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import com.yo1no.gramarye.magic.definition.tree.SupportedDynamicTrees;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class P4A1ApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path PACKAGE_ROOT = MAIN_JAVA.resolve("com/yo1no/gramarye");
    private static final Path TREE_PACKAGE = PACKAGE_ROOT.resolve("magic/definition/tree");
    private static final Path DOCUMENT_PACKAGE = PACKAGE_ROOT.resolve("magic/definition/document");

    private static final List<Path> A1_SOURCES = List.of(
            PACKAGE_ROOT.resolve("magic/api/id/SkillOwnerId.java"),
            PACKAGE_ROOT.resolve("magic/definition/tree/SerializedTreeFamily.java"),
            PACKAGE_ROOT.resolve("magic/definition/tree/SerializedTreeContext.java"),
            PACKAGE_ROOT.resolve("magic/definition/tree/SupportedDynamicTrees.java"),
            PACKAGE_ROOT.resolve("magic/definition/tree/DynamicTreeBounds.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/AppearanceDocument.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/AppearanceOverrideDocument.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/AppearanceRawSnapshot.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/AppearanceStorageCodec.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/DynamicTreeSupport.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/BoundedByteEncoding.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/ImmutableEncodedBytes.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/MalformedTreeException.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/PhysicalSkillDocument.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/PhysicalSkillDocumentNbt.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/RawTreeEnvelope.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/SkillDocumentPersistenceBridge.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/SkillDocumentPersistenceFailure.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/SkillDocumentPersistenceLocation.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/SkillDocumentPersistenceResult.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/StrictJsonTreeCodec.java"),
            PACKAGE_ROOT.resolve("magic/definition/document/StrictNbtTreeCodec.java"),
            PACKAGE_ROOT.resolve("magic/definition/envelope/RawPayloadSnapshot.java"),
            PACKAGE_ROOT.resolve("magic/definition/migration/RawSkillDocumentSnapshot.java"),
            PACKAGE_ROOT.resolve("magic/limits/MagicSafetyCeilings.java"));

    private static final List<Class<?>> A1_INTERNAL_TYPES = List.of(
            BoundedByteEncoding.class,
            ImmutableEncodedBytes.class,
            MalformedTreeException.class,
            PhysicalSkillDocument.class,
            PhysicalNodeDocument.class,
            PhysicalDefinitionEnvelope.class,
            PhysicalTopAppearance.class,
            PhysicalAppearanceOverride.class,
            PhysicalSkillDocumentNbt.class,
            RawTreeEnvelope.class,
            SkillDocumentPersistenceBridge.class,
            SkillDocumentPersistenceFailure.class,
            SkillDocumentPersistenceLocation.class,
            SkillDocumentPersistenceResult.class,
            StrictJsonTreeCodec.class,
            StrictNbtTreeCodec.class);

    private static final Pattern FAMILY_DECLARATION = Pattern.compile(
            "\\benum\\s+SerializedTreeFamily\\b");
    private static final Pattern CLASSIFIER_DECLARATION = Pattern.compile(
            "\\bclass\\s+SupportedDynamicTrees\\b");
    private static final Pattern OPS_CLASSIFICATION = Pattern.compile(
            "instanceof\\s+(?:JsonOps|NbtOps|RegistryOps)");
    private static final Pattern SINGLETON_IDENTITY = Pattern.compile(
            "(?:==|!=)\\s*(?:JsonOps|NbtOps)\\.(?:INSTANCE|COMPRESSED)"
                    + "|(?:JsonOps|NbtOps)\\.(?:INSTANCE|COMPRESSED)\\s*(?:==|!=)");
    private static final Pattern RAW_GENERIC_DECLARATION = Pattern.compile(
            "\\b(?:Class|Codec|Collection|DataResult|Dynamic|DynamicOps|Iterable|List|Map|Optional|"
                    + "RegistryOps|Set|Stream)\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*(?:[=;,)\\[])");
    private static final Pattern UNCHECKED_STYLE_CAST = Pattern.compile(
            "\\(\\s*(?:Class|Codec|Collection|DataResult|Dynamic|DynamicOps|Iterable|List|Map|"
                    + "Optional|RegistryOps|Set|Stream)\\s*\\)");
    /** P4-C1 phase-local: physical persistence is allowed; C2/composition remain absent. */
    private static final Pattern FORBIDDEN_POST_C1_TYPE = Pattern.compile(
            "\\b(?:class|record|interface|enum)\\s+"
                    + "(?:[A-Za-z0-9_]*CarrierDelta[A-Za-z0-9_]*|"
                    + "PlayerSkillAttachment(?:Registration|Service|Lifecycle)[A-Za-z0-9_]*|"
                    + "PreparedPlayerSkillTransition[A-Za-z0-9_]*|"
                    + "PlayerSkillRootProjection[A-Za-z0-9_]*|"
                    + "PendingAttachmentJournal[A-Za-z0-9_]*)\\b");
    private static final Pattern PRODUCTION_FIXTURE_TYPE = Pattern.compile(
            "\\b(?:class|record|interface|enum)\\s+[A-Za-z0-9_]*(?:Test|Fixture|Fake|Dummy|Noop|Stub)\\b");

    @Test
    void serializedTreeFamilyAndDynamicClassifierHaveOneProductionTruth() throws Exception {
        var sources = productionSources();
        var familyDefinitions = sources.stream()
                .filter(source -> FAMILY_DECLARATION.matcher(source.contents()).find())
                .map(SourceFile::path)
                .toList();
        var classifierDefinitions = sources.stream()
                .filter(source -> CLASSIFIER_DECLARATION.matcher(source.contents()).find())
                .map(SourceFile::path)
                .toList();
        var classifierCallSites = sources.stream()
                .filter(source -> OPS_CLASSIFICATION.matcher(source.contents()).find())
                .map(SourceFile::path)
                .distinct()
                .toList();
        var utility = TREE_PACKAGE.resolve("SupportedDynamicTrees.java");

        assertAll(
                () -> assertEquals(
                        List.of(TREE_PACKAGE.resolve("SerializedTreeFamily.java")),
                        familyDefinitions),
                () -> assertEquals(List.of(utility), classifierDefinitions),
                () -> assertEquals(List.of(utility), classifierCallSites),
                () -> assertFalse(Files.exists(DOCUMENT_PACKAGE.resolve("SerializedTreeFamily.java"))),
                () -> assertEquals(Set.of(SerializedTreeFamily.JSON, SerializedTreeFamily.NBT),
                        Set.of(SerializedTreeFamily.values())));
    }

    @Test
    void legacySnapshotsDelegateFamilyAndCopyWorkToTheSingleUtility() throws Exception {
        var refactoredSnapshots = List.of(
                DOCUMENT_PACKAGE.resolve("AppearanceRawSnapshot.java"),
                PACKAGE_ROOT.resolve("magic/definition/envelope/RawPayloadSnapshot.java"),
                PACKAGE_ROOT.resolve("magic/definition/migration/RawSkillDocumentSnapshot.java"));

        for (var source : refactoredSnapshots) {
            var contents = Files.readString(source);
            assertAll(
                    source.toString(),
                    () -> assertTrue(contents.contains("SupportedDynamicTrees.defensiveCopy(")),
                    () -> assertTrue(contents.contains("SupportedDynamicTrees.contextOf(")),
                    () -> assertFalse(OPS_CLASSIFICATION.matcher(contents).find()),
                    () -> assertFalse(contents.contains(".compressMaps()")),
                    () -> assertFalse(contents.contains(".withParent(")));
        }

        var compatibilitySupport = Files.readString(
                DOCUMENT_PACKAGE.resolve("DynamicTreeSupport.java"));
        assertAll(
                () -> assertFalse(compatibilitySupport.contains("SerializedTreeFamily")),
                () -> assertFalse(compatibilitySupport.contains("SerializedTreeContext")),
                () -> assertFalse(compatibilitySupport.contains("JsonOps")),
                () -> assertFalse(compatibilitySupport.contains("NbtOps")),
                () -> assertFalse(compatibilitySupport.contains("RegistryOps")));
    }

    @Test
    void productionUsesNeitherOpsSingletonIdentityNorDynamicConversion() throws Exception {
        for (var source : productionSources()) {
            var code = withoutCommentsAndLiterals(source.contents());
            assertAll(
                    source.path().toString(),
                    () -> assertFalse(SINGLETON_IDENTITY.matcher(code).find()),
                    () -> assertFalse(code.contains(".convert(")),
                    () -> assertFalse(code.contains(".convertTo(")));
        }
    }

    @Test
    void onlyReviewedFamilyContextSurfaceIsPublic() {
        assertAll(
                () -> assertTrue(Modifier.isPublic(SerializedTreeFamily.class.getModifiers())),
                () -> assertTrue(Modifier.isPublic(SerializedTreeContext.class.getModifiers())),
                () -> assertTrue(Modifier.isPublic(SupportedDynamicTrees.class.getModifiers())),
                () -> assertTrue(Modifier.isFinal(SupportedDynamicTrees.class.getModifiers())),
                () -> assertEquals(
                        List.of("compressedMaps", "family", "registryContext"),
                        Arrays.stream(SerializedTreeContext.class.getRecordComponents())
                                .map(component -> component.getName())
                                .sorted()
                                .toList()),
                () -> assertTrue(Arrays.stream(SerializedTreeContext.class.getDeclaredFields())
                        .noneMatch(field -> isMutableTreeOrContext(field.getType()))));

        var publicUtilityMethods = Arrays.stream(SupportedDynamicTrees.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();
        assertAll(
                () -> assertEquals(3, publicUtilityMethods.size()),
                () -> assertEquals(
                        List.of("contextOf", "contextOf", "defensiveCopy"),
                        publicUtilityMethods.stream().map(Method::getName).sorted().toList()),
                () -> assertTrue(publicUtilityMethods.stream()
                        .noneMatch(method -> isMutableTreeOrContext(method.getReturnType()))));
    }

    @Test
    void rawEnvelopePhysicalDtosAndCurrentHydrationRemainInternal() throws Exception {
        assertAll(
                () -> assertFalse(Modifier.isPublic(RawTreeEnvelope.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(PhysicalSkillDocument.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(PhysicalNodeDocument.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(PhysicalDefinitionEnvelope.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(PhysicalTopAppearance.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(PhysicalAppearanceOverride.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(PhysicalSkillDocumentNbt.class.getModifiers())));

        var hydration = SkillDocumentPersistenceBridge.class.getDeclaredMethod(
                "hydrateCurrentForInternalUse", ImmutableEncodedBytes.class, java.util.Optional.class);
        assertAll(
                () -> assertFalse(Modifier.isPublic(hydration.getModifiers())),
                () -> assertTrue(Modifier.isStatic(hydration.getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        SkillDocumentPersistenceBridge.class.getModifiers())),
                () -> assertTrue(Arrays.stream(SkillDocumentPersistenceBridge.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .noneMatch(method -> method.getName().toLowerCase().contains("hydrate")
                                || method.getName().toLowerCase().contains("decode")
                                || method.getName().toLowerCase().contains("load"))));
    }

    @Test
    void rawBytesHaveNoPublicReferenceAccessor() throws Exception {
        var rawCopy = RawTreeEnvelope.class.getDeclaredMethod("copyData");
        var immutableCopy = ImmutableEncodedBytes.class.getDeclaredMethod("copyBytes");

        assertAll(
                () -> assertEquals(byte[].class, rawCopy.getReturnType()),
                () -> assertFalse(Modifier.isPublic(rawCopy.getModifiers())),
                () -> assertEquals(byte[].class, immutableCopy.getReturnType()),
                () -> assertFalse(Modifier.isPublic(immutableCopy.getModifiers())),
                () -> assertTrue(A1_INTERNAL_TYPES.stream()
                        .filter(type -> Modifier.isPublic(type.getModifiers()))
                        .flatMap(type -> Arrays.stream(type.getMethods()))
                        .noneMatch(method -> method.getReturnType() == byte[].class)),
                () -> assertTrue(Arrays.stream(RawTreeEnvelope.class.getDeclaredFields())
                        .noneMatch(field -> field.getType() == byte[].class)));
    }

    @Test
    void skillOwnerIdAddsOnlyTheCanonicalMojangCodecAndNoStreamCodec() throws Exception {
        var codec = SkillOwnerId.class.getDeclaredField("CODEC");
        var ownerSource = Files.readString(
                PACKAGE_ROOT.resolve("magic/api/id/SkillOwnerId.java"));
        var skillIdSource = Files.readString(
                PACKAGE_ROOT.resolve("magic/api/id/SkillId.java"));

        assertAll(
                () -> assertEquals(Codec.class, codec.getType()),
                () -> assertTrue(Modifier.isPublic(codec.getModifiers())),
                () -> assertTrue(Modifier.isStatic(codec.getModifiers())),
                () -> assertTrue(Modifier.isFinal(codec.getModifiers())),
                () -> assertTrue(ownerSource.contains("UUIDUtil.CODEC.xmap")),
                () -> assertTrue(skillIdSource.contains("UUIDUtil.CODEC.xmap")),
                () -> assertFalse(ownerSource.contains("StreamCodec")),
                () -> assertTrue(Arrays.stream(SkillOwnerId.class.getDeclaredFields())
                        .noneMatch(field -> field.getType().getSimpleName().contains("StreamCodec"))),
                () -> assertTrue(Arrays.stream(SkillOwnerId.class.getDeclaredMethods())
                        .noneMatch(method -> method.getReturnType().getSimpleName().contains("StreamCodec"))));
    }

    @Test
    void p4C1PhysicalTypesAreAllowedWhileC2AndLaterCompositionRemainAbsent() throws Exception {
        var sources = productionSources();
        var laterPhaseDeclarations = sources.stream()
                .filter(source -> FORBIDDEN_POST_C1_TYPE.matcher(source.contents()).find())
                .map(SourceFile::path)
                .toList();
        var forbiddenA1References = Pattern.compile(
                "net\\.minecraft\\.world\\.level\\.saveddata|DimensionDataStorage|"
                        + "net\\.neoforged\\.neoforge\\.attachment|AttachmentType|ServerPlayer|"
                        + "ServerLevel|\\bPlayer\\b|\\bLevel\\b|setDirty\\s*\\(|"
                        + "java\\.nio\\.file|FileInputStream|FileOutputStream|StreamCodec");

        assertAll(
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_RAW_PAYLOAD_BYTES,
                        MagicSafetyCeilings.MAX_UNPARSED_APPEARANCE_BYTES),
                () -> assertTrue(laterPhaseDeclarations.isEmpty(),
                        () -> "Later-phase declarations found in " + laterPhaseDeclarations),
                () -> assertTrue(A1_SOURCES.stream().allMatch(path -> {
                    try {
                        return !forbiddenA1References.matcher(Files.readString(path)).find();
                    } catch (IOException exception) {
                        throw new AssertionError("Unable to inspect " + path, exception);
                    }
                })));
    }

    @Test
    void a1SourcesContainNoRawUncheckedSuppressedOrProductionFixtureCode() throws Exception {
        var rawFindings = new ArrayList<String>();
        var castFindings = new ArrayList<String>();
        var suppressionFindings = new ArrayList<Path>();
        var fixtureFindings = new ArrayList<Path>();

        for (var path : A1_SOURCES) {
            assertTrue(Files.isRegularFile(path), () -> "Missing reviewed A1 source: " + path);
            var contents = Files.readString(path);
            var code = withoutCommentsAndLiterals(contents);
            collectMatches(path, RAW_GENERIC_DECLARATION, code, rawFindings);
            collectMatches(path, UNCHECKED_STYLE_CAST, code, castFindings);
            if (contents.contains("@SuppressWarnings")) {
                suppressionFindings.add(path);
            }
            if (PRODUCTION_FIXTURE_TYPE.matcher(code).find()
                    || contents.contains("org.junit")) {
                fixtureFindings.add(path);
            }
        }

        for (var type : A1_INTERNAL_TYPES) {
            assertNoRawMemberSignatures(type);
        }
        assertNoRawMemberSignatures(SerializedTreeContext.class);
        assertNoRawMemberSignatures(SupportedDynamicTrees.class);
        assertNoRawMemberSignatures(SkillOwnerId.class);

        assertAll(
                () -> assertTrue(rawFindings.isEmpty(), () -> "Raw declarations: " + rawFindings),
                () -> assertTrue(castFindings.isEmpty(), () -> "Unchecked-style casts: " + castFindings),
                () -> assertTrue(suppressionFindings.isEmpty(),
                        () -> "Suppressions in A1 sources: " + suppressionFindings),
                () -> assertTrue(fixtureFindings.isEmpty(),
                        () -> "Production fixtures in A1 sources: " + fixtureFindings));
    }

    private static void assertNoRawMemberSignatures(Class<?> type) {
        for (var field : type.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                assertNotRaw(field.getType(), field.getGenericType(), type.getName() + " field " + field.getName());
            }
        }
        for (var constructor : type.getDeclaredConstructors()) {
            if (!constructor.isSynthetic()) {
                assertExecutableNotRaw(constructor);
            }
        }
        for (var method : type.getDeclaredMethods()) {
            if (!method.isSynthetic() && !method.isBridge()) {
                assertNotRaw(method.getReturnType(), method.getGenericReturnType(),
                        type.getName() + " return " + method.getName());
                assertExecutableNotRaw(method);
            }
        }
        for (var nested : type.getDeclaredClasses()) {
            assertNoRawMemberSignatures(nested);
        }
    }

    private static void assertExecutableNotRaw(Executable executable) {
        var erased = executable.getParameterTypes();
        var generic = executable.getGenericParameterTypes();
        var syntheticPrefix = erased.length - generic.length;
        assertTrue(syntheticPrefix >= 0, () -> "Unexpected reflective signature for " + executable);
        for (var index = syntheticPrefix; index < erased.length; index++) {
            assertNotRaw(
                    erased[index],
                    generic[index - syntheticPrefix],
                    executable + " parameter " + index);
        }
    }

    private static void assertNotRaw(Class<?> erased, Type generic, String location) {
        if (erased.getTypeParameters().length > 0) {
            assertTrue(isParameterized(generic), () -> "Raw generic signature at " + location);
        }
    }

    private static boolean isParameterized(Type type) {
        return type instanceof ParameterizedType
                || type instanceof TypeVariable<?>
                || type instanceof WildcardType
                || type instanceof GenericArrayType;
    }

    private static boolean isMutableTreeOrContext(Class<?> type) {
        var name = type.getName();
        return name.equals("com.google.gson.JsonElement")
                || name.equals("net.minecraft.nbt.Tag")
                || name.equals("com.mojang.serialization.DynamicOps")
                || name.equals("net.minecraft.resources.RegistryOps")
                || name.equals("net.minecraft.core.HolderLookup$Provider")
                || type == byte[].class;
    }

    private static void collectMatches(
            Path path,
            Pattern pattern,
            String contents,
            List<String> findings) {
        var matcher = pattern.matcher(contents);
        while (matcher.find()) {
            findings.add(path.getFileName() + ":" + matcher.group());
        }
    }

    private static List<SourceFile> productionSources() throws IOException {
        try (var paths = Files.walk(MAIN_JAVA)) {
            var files = paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
            var sources = new ArrayList<SourceFile>(files.size());
            for (var path : files) {
                sources.add(new SourceFile(path, Files.readString(path)));
            }
            return List.copyOf(sources);
        }
    }

    private static String withoutCommentsAndLiterals(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ")
                .replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"")
                .replaceAll("'(?:\\\\.|[^'\\\\])*'", "''");
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
        throw new AssertionError("Unable to locate the Gradle project root");
    }

    private record SourceFile(Path path, String contents) {
        private SourceFile {
            assertNotNull(path);
            assertNotNull(contents);
        }
    }
}
