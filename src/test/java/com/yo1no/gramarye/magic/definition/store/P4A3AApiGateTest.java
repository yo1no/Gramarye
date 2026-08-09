package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Phase-local API and architecture gate for pure P4-A3-A carrier primitives. */
class P4A3AApiGateTest {
    private static final String STORE_PACKAGE =
            "com.yo1no.gramarye.magic.definition.store.";
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path STORE_ROOT = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye/magic/definition/store");
    private static final Set<String> A3_A_TYPES = Set.of(
            "StoreEncodingLayout",
            "StoreLayoutEncodeResult",
            "EncodedSkillStoreCarrier",
            "EncodedHistoryIndex",
            "EncodedRevisionIndex",
            "PreparedCarrierUpdate",
            "CarrierUpdateKind",
            "SkillStoreCarrierBuilder",
            "CarrierBuildResult",
            "CarrierUpdateResult",
            "CarrierInvariantException",
            "HistoryBlobSource",
            "RevisionBlobSource",
            "StoreHistoryBlobSlice",
            "StoreRevisionBlobSlice");
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "\\b(?:class|record|interface|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final Pattern TOP_LEVEL_TYPE_DECLARATION = Pattern.compile(
            "(?m)^(?:(?:abstract|final|non-sealed|sealed)\\s+)*"
                    + "(?:class|record|interface|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");

    @Test
    void reviewedA3ATypesExistAndRemainPackagePrivate() {
        var loaded = A3_A_TYPES.stream()
                .map(name -> loadWithoutInitialization(STORE_PACKAGE + name))
                .toList();

        assertAll(
                () -> assertEquals(A3_A_TYPES,
                        loaded.stream().map(Class::getSimpleName).collect(Collectors.toSet())),
                () -> assertTrue(loaded.stream().allMatch(type -> {
                    var modifiers = type.getModifiers();
                    return !Modifier.isPublic(modifiers)
                            && !Modifier.isProtected(modifiers)
                            && !Modifier.isPrivate(modifiers);
                })));
    }

    @Test
    void p3dStorePublicSurfaceRemainsTheOnlyDomainSurface() {
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
    void carrierKeepsOneRootBlobAndIndexesKeepNoNestedByteArrays() {
        var carrier = loadWithoutInitialization(STORE_PACKAGE + "EncodedSkillStoreCarrier");
        var reviewedTypes = A3_A_TYPES.stream()
                .map(name -> loadWithoutInitialization(STORE_PACKAGE + name))
                .toList();
        var carrierBlobFields = Arrays.stream(carrier.getDeclaredFields())
                .filter(field -> field.getType().getSimpleName().equals("ImmutableStoreBlob"))
                .count();
        var forbiddenRetainedTypes = Set.of(
                "SkillDocument",
                "ValidatedSkillDefinition",
                "SkillDefinitionStoreSnapshot",
                "PipelineFactReport",
                "ValidationResult",
                "EncodedSkillDocument",
                "ImmutableHistoryBlob",
                "ImmutableRevisionBlob",
                "net.minecraft.core.HolderLookup$Provider",
                "DynamicOps",
                "Dynamic",
                "Tag");

        assertAll(
                () -> assertEquals(1, carrierBlobFields),
                () -> assertTrue(reviewedTypes.stream()
                        .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                        .noneMatch(field -> field.getType() == byte[].class)),
                () -> assertTrue(reviewedTypes.stream()
                        .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                        .noneMatch(field -> forbiddenRetainedTypes.contains(
                                        field.getType().getSimpleName())
                                || forbiddenRetainedTypes.contains(field.getType().getName()))),
                () -> assertTrue(Arrays.stream(carrier.getDeclaredMethods())
                        .noneMatch(method -> method.getName().equals("equals")
                                || method.getName().equals("hashCode"))));
    }

    @Test
    void a3aSourcesStayInsideThePureCarrierBoundary() throws Exception {
        var sources = reviewedSources();
        var code = sources.stream()
                .map(P4A3AApiGateTest::read)
                .map(P4A3AApiGateTest::withoutCommentsAndLiterals)
                .collect(Collectors.joining("\n"));
        var forbiddenTokens = List.of(
                "SavedData",
                "DimensionDataStorage",
                "Attachment",
                "Journal",
                "setDirty",
                "ServerPlayer",
                "ServerLevel",
                "StreamCodec",
                "RootProvider",
                "RootCollector",
                "SkillStoreCommitResult",
                "SkillStoreCommitConflict",
                "SkillQuota",
                "java.nio.file",
                "FileInputStream",
                "FileOutputStream",
                "java.util.concurrent",
                "MemoryMXBean");

        assertAll(
                () -> assertTrue(forbiddenTokens.stream().noneMatch(code::contains)),
                () -> assertFalse(Pattern.compile("\\.\\s*commit\\s*\\(")
                        .matcher(code).find()),
                () -> assertFalse(Pattern.compile("\\.\\s*reclaim\\s*\\(")
                        .matcher(code).find()));
    }

    @Test
    void a3aDoesNotDuplicateA2PhysicalSchemaOrDocumentEncoding() throws Exception {
        var source = reviewedSources().stream()
                .map(P4A3AApiGateTest::read)
                .collect(Collectors.joining("\n"));
        var forbiddenSchemaLiterals = List.of(
                "\"store_schema_version\"",
                "\"history_entries\"",
                "\"revision_entries\"",
                "\"document_encoding\"",
                "\"document_bytes\"");
        var forbiddenImplementationTypes = List.of(
                "NbtIo",
                "CompoundTag",
                "Codec.PASSTHROUGH",
                "RawTreeEnvelope",
                "SkillDocumentWriter",
                "SkillDocumentPersistenceBridge");

        assertAll(
                () -> assertTrue(forbiddenSchemaLiterals.stream().noneMatch(source::contains)),
                () -> assertTrue(forbiddenImplementationTypes.stream().noneMatch(source::contains)));
    }

    @Test
    void carrierConstructionRemainsBoundToReviewedWriterCompositionCallSites()
            throws Exception {
        var production = productionSources(STORE_ROOT);

        assertAll(
                () -> assertEquals(
                        Set.of(
                                "SkillDefinitionStorePersistenceBridge.java",
                                "SkillStoreCarrierBuilder.java"),
                        filesContaining(production, "StoreEncodingLayout.fromWriterFrame(")),
                () -> assertEquals(
                        Set.of("SkillStoreCarrierBuilder.java"),
                        filesContaining(production, "EncodedSkillStoreCarrier.fromLayout(")),
                () -> assertEquals(
                        Set.of("StoreNbtFraming.java"),
                        filesContaining(production, "new EncodedHistoryIndex(")),
                () -> assertEquals(
                        Set.of("StoreNbtFraming.java"),
                        filesContaining(production, "new EncodedRevisionIndex(")),
                () -> assertEquals(
                        Set.of("SkillDefinitionStorePersistenceBridge.java"),
                        filesContaining(
                                production, "StoreNbtFraming.encodeRevisionWithRoute(")),
                () -> assertEquals(
                        Set.of(
                                "SkillDefinitionStorePersistenceBridge.java",
                                "SkillStoreCarrierBuilder.java"),
                        filesContaining(
                                production, "StoreNbtFraming.encodeHistoryWithLayout(")),
                () -> assertEquals(
                        Set.of(
                                "SkillDefinitionStorePersistenceBridge.java",
                                "SkillStoreCarrierBuilder.java"),
                        filesContaining(
                                production, "StoreNbtFraming.encodeStoreWithLayout(")),
                () -> assertEquals(
                        Set.of("SkillStoreCarrierBuilder.java"),
                        filesContaining(production, ".routedHistorySource(")),
                () -> assertEquals(
                        Set.of("SkillStoreCarrierBuilder.java"),
                        filesContaining(production, ".routedRevisionSource(")),
                () -> assertEquals(
                        Set.of("EncodedSkillStoreCarrier.java"),
                        filesContaining(
                                production, "StoreNbtFraming.bindVerifiedHistorySource(")),
                () -> assertEquals(
                        Set.of("EncodedSkillStoreCarrier.java"),
                        filesContaining(
                                production, "StoreNbtFraming.bindVerifiedRevisionSource(")),
                () -> assertTrue(production.stream()
                        .map(P4A3AApiGateTest::read)
                        .noneMatch(source -> source.contains("EncodedSkillStoreCarrier.of("))));
    }

    @Test
    void probesRemainTestOnlyAndP4C2AOpensOnlyReviewedPlayerLifecycle() throws Exception {
        var production = productionSources(PROJECT_ROOT.resolve("src/main/java")).stream()
                .map(P4A3AApiGateTest::read)
                .collect(Collectors.joining("\n"));
        var journalOwners = filesContaining(
                productionSources(STORE_ROOT), "PendingAttachmentJournal");
        var reviewedD1JournalOwners = new java.util.HashSet<>(
                P4DPhaseTypes.NEW_STORE_SOURCE_FILE_NAMES);
        reviewedD1JournalOwners.addAll(P4DPhaseTypes.MODIFIED_STORE_SOURCE_FILE_NAMES);
        reviewedD1JournalOwners.add("SkillSubmissionRecoveryGameTests.java");
        reviewedD1JournalOwners.add("P4E1PendingJournalObservation.java");

        assertAll(
                () -> assertFalse(production.contains("P4A3HeapProbe")),
                () -> assertFalse(production.contains("P4A3CarrierGameTests")),
                () -> assertFalse(production.contains("P4B2ProbeMain")),
                () -> assertFalse(production.contains("P4B2MemoryGameTests")),
                () -> assertFalse(production.contains("gramarye_p4_b2")),
                () -> assertFalse(production.contains("P4C2ProbeMain")),
                () -> assertFalse(production.contains("P4C2MemoryGameTests")),
                () -> assertFalse(production.contains(
                        "@GameTestHolder(\"gramarye_p4_c2\")")),
                () -> assertFalse(production.contains("P4D3ProbeMain")),
                () -> assertFalse(production.contains("P4D3MemoryGameTests")),
                () -> assertFalse(production.contains(
                        "@GameTestHolder(\"gramarye_p4_d3\")")),
                // P4-C2-A phase-local: exact registration, controlled mutation, token, and
                // per-player root projection are now reviewed production.
                () -> assertTrue(production.contains("PlayerSkillAttachmentState")),
                () -> assertTrue(production.contains("PlayerSkillAttachmentSerializer")),
                () -> assertTrue(production.contains("SkillDraftPersistenceFacade")),
                () -> assertTrue(production.contains("PlayerSkillAttachments")),
                () -> assertTrue(production.contains("PlayerSkillAttachmentService")),
                () -> assertTrue(production.contains("AttachmentType")),
                () -> assertTrue(production.contains(".copyOnDeath()")),
                () -> assertTrue(production.contains("ServerPlayer")),
                () -> assertTrue(production.contains(".setData(")),
                () -> assertTrue(production.contains("PreparedPlayerSkillTransition")),
                () -> assertTrue(reviewedD1JournalOwners.containsAll(journalOwners),
                        () -> "Pending journal escaped exact D1 allowlist: " + journalOwners),
                () -> assertTrue(production.contains("PlayerSkillRootProjection")));
    }

    private static List<Path> reviewedSources() throws Exception {
        var sources = productionSources(STORE_ROOT);
        var declarations = new LinkedHashSet<String>();
        var reviewed = sources.stream().filter(path -> {
            var matcher = TYPE_DECLARATION.matcher(withoutCommentsAndLiterals(read(path)));
            var matched = false;
            while (matcher.find()) {
                if (A3_A_TYPES.contains(matcher.group(1))) {
                    declarations.add(matcher.group(1));
                    matched = true;
                }
            }
            return matched;
        }).toList();
        assertEquals(A3_A_TYPES, declarations);
        var reviewedTopLevelTypes = reviewed.stream()
                .flatMap(path -> TOP_LEVEL_TYPE_DECLARATION
                        .matcher(withoutCommentsAndLiterals(read(path)))
                        .results()
                        .map(result -> result.group(1)))
                .collect(Collectors.toSet());
        var allowedTopLevelTypes = new LinkedHashSet<>(A3_A_TYPES);
        allowedTopLevelTypes.addAll(Set.of(
                "ImmutableStoreBlob",
                "ImmutableHistoryBlob",
                "ImmutableRevisionBlob"));
        assertEquals(allowedTopLevelTypes, reviewedTopLevelTypes);
        return reviewed;
    }

    private static List<Path> productionSources(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static Set<String> filesContaining(List<Path> sources, String fragment) {
        return sources.stream()
                .filter(path -> read(path).contains(fragment))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
    }

    private static Class<?> loadWithoutInitialization(String className) {
        try {
            return Class.forName(className, false, P4A3AApiGateTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Missing reviewed P4-A3-A class: " + className, exception);
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
