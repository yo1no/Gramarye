package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Phase-local API, source-set, task, CI, and later-domain gate for P4-B2-B. */
class P4B2BApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path PROBE_ROOT = PROJECT_ROOT.resolve("src/p4B2Probe/java");
    private static final Path GAME_TEST_ROOT = PROJECT_ROOT.resolve("src/p4B2GameTest/java");
    private static final Set<String> PROBE_FILES = Set.of(
            "P4B2ProbeCase.java",
            "P4B2Hashing.java",
            "P4B2FixtureManifest.java",
            "P4B2FixtureBuilder.java",
            "P4B2FileVerifier.java",
            "P4B2RuntimePackagingVerifier.java",
            "P4B2ProbeMain.java",
            "P4B2ProbeSummary.java");
    private static final Set<String> GAME_TEST_FILES = Set.of(
            "P4B2ProbeServerLifecycle.java",
            "P4B2MemoryGameTests.java");

    @Test
    void exactProbeFilesStayInTheirTwoIsolatedSourceSets() throws Exception {
        assertAll(
                () -> assertEquals(PROBE_FILES, javaFiles(PROBE_ROOT)),
                () -> assertEquals(GAME_TEST_FILES, javaFiles(GAME_TEST_ROOT)),
                () -> assertTrue(Modifier.isPublic(P4B2ProbeMain.class.getModifiers())),
                () -> assertEquals(
                        Set.of("main"),
                        Arrays.stream(P4B2ProbeMain.class.getDeclaredMethods())
                                .filter(method -> Modifier.isPublic(method.getModifiers()))
                                .map(method -> method.getName())
                                .collect(Collectors.toSet())),
                () -> assertThrows(ClassNotFoundException.class, () -> Class.forName(
                        "com.yo1no.gramarye.magic.definition.store.P4B2MemoryGameTests")));
    }

    @Test
    void fixedHeapTaskGraphAndSingleGameTestDispatcherAreExact() throws Exception {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var gameTest = sources(GAME_TEST_ROOT);
        for (var required : List.of(
                "sourceSets.create('p4B2Probe')",
                "sourceSets.create('p4B2GameTest')",
                "prepareP4B2HeapWorld",
                "runP4B2HeapLoadSaveServer",
                "runP4B2HeapRestartServer",
                "prepareP4B2HostileFnameWorld",
                "runP4B2HostileFnameServer",
                "runP4B2HostileFnameRestartServer",
                "verifyP4B2HostileFnameOutput",
                "verifyP4B2HostileFnameRestartOutput",
                "prepareP4B2InvalidWorlds",
                "runP4B2MalformedServer",
                "runP4B2MalformedRestartServer",
                "runP4B2TrailingServer",
                "runP4B2TrailingRestartServer",
                "runP4B2SecondMemberServer",
                "runP4B2SecondMemberRestartServer",
                "p4B2InvalidRestartGate",
                "verifyP4B2Configuration",
                "p4B2FixedHeapGate",
                "'-Xms512m'",
                "'-Xmx1024m'",
                "'-XX:+ExitOnOutOfMemoryError'",
                "Duration.ofSeconds(600)",
                "Duration.ofSeconds(300)")) {
            assertTrue(build.contains(required), () -> "missing B2-B build contract: " + required);
        }
        assertAll(
                () -> assertEquals(1, occurrences(gameTest, "@GameTest(")),
                () -> assertTrue(gameTest.contains("@GameTestHolder(\"gramarye_p4_b2\")")),
                () -> assertTrue(gameTest.contains("templateNamespace = \"gramarye_p4_b2\"")),
                () -> assertEquals(
                        Set.of(
                                "FULL_FIRST",
                                "FULL_RESTART",
                                "HOSTILE_FNAME_FIRST",
                                "HOSTILE_FNAME_RESTART",
                                "MALFORMED_FIRST",
                                "MALFORMED_RESTART",
                                "TRAILING_FIRST",
                                "TRAILING_RESTART",
                                "SECOND_MEMBER_FIRST",
                                "SECOND_MEMBER_RESTART"),
                        Arrays.stream(P4B2RunMode.values())
                                .map(Enum::name)
                                .collect(Collectors.toSet())),
                () -> assertEquals(
                        Set.of(
                                "full-first-load-save",
                                "full-restart",
                                "hostile-fname-first",
                                "hostile-fname-restart",
                                "malformed-first",
                                "malformed-restart",
                                "trailing-first",
                                "trailing-restart",
                                "second-member-first",
                                "second-member-restart"),
                        Arrays.stream(P4B2RunMode.values())
                                .map(P4B2RunMode::token)
                                .collect(Collectors.toSet())),
                () -> assertEquals(10, P4B2RunMode.values().length),
                () -> assertTrue(P4B2RunMode.HOSTILE_FNAME_FIRST.fullSize()),
                () -> assertEquals(
                        P4B2RunMode.HOSTILE_FNAME_RESTART,
                        P4B2RunMode.HOSTILE_FNAME_FIRST.restartMode()),
                () -> assertTrue(build.contains(
                        "dependsOn(runP4B2HeapLoadSaveServer, verifyP4B2HeapLoadSaveOutput)")),
                () -> assertTrue(build.contains(
                        "dependsOn(runP4B2HostileFnameServer, "
                                + "verifyP4B2HostileFnameOutput)")),
                () -> assertTrue(build.contains(
                        "dependsOn(runP4B2MalformedServer, verifyP4B2MalformedOutput)")),
                () -> assertTrue(build.contains(
                        "dependsOn(runP4B2TrailingServer, verifyP4B2TrailingOutput)")),
                () -> assertTrue(build.contains(
                        "dependsOn(runP4B2SecondMemberServer, verifyP4B2SecondMemberOutput)")));
    }

    @Test
    void hostileFnameGateIsExactMaximumLegalAndPartOfTheFixedHeapCiPath()
            throws Exception {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var workflow = read(PROJECT_ROOT.resolve(".github/workflows/build.yml"));
        var builder = read(PROBE_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/store/P4B2FixtureBuilder.java"));
        var manifest = read(PROBE_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/store/P4B2FixtureManifest.java"));
        var verifier = read(PROBE_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/store/P4B2FileVerifier.java"));
        var gameTest = read(GAME_TEST_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/store/P4B2MemoryGameTests.java"));
        var configurationGate = read(PROJECT_ROOT.resolve(
                "scripts/verify-p4-b2-b-configuration.sh"));

        assertAll(
                () -> assertEquals(
                        73_400_320,
                        MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES),
                () -> assertEquals(
                        Set.of("FULL", "HOSTILE_FNAME", "MALFORMED_GZIP",
                                "COMPRESSED_TRAILING", "SECOND_MEMBER"),
                        Arrays.stream(P4B2ProbeCase.values())
                                .map(Enum::name)
                                .collect(Collectors.toSet())),
                () -> assertTrue(builder.contains("prepareHostileFname(Path")),
                () -> assertTrue(builder.contains("requireExactMaximumHostileFname(")),
                () -> assertTrue(builder.contains("requireCanonicalGzipWithoutFname(")),
                () -> assertTrue(builder.contains(
                        "MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES")),
                () -> assertTrue(builder.contains(
                        "private static final int GZIP_FNAME_FLAG = 0x08")),
                () -> assertTrue(builder.contains(
                        "getBytes(StandardCharsets.ISO_8859_1)[0]")),
                () -> assertTrue(builder.contains(
                        "Arrays.fill(buffer, HOSTILE_FNAME_BYTE)")),
                () -> assertTrue(builder.contains("output.write(0);")),
                () -> assertTrue(builder.contains("buffer[index] == 0")),
                () -> assertTrue(builder.contains("Files.size(basePrimary)")),
                () -> assertTrue(builder.contains("copyRemaining(input, output)")),
                () -> assertTrue(manifest.contains("long sourceFnameBytes")),
                () -> assertTrue(manifest.contains("source_fname_bytes=")),
                () -> assertTrue(verifier.contains("requireCanonicalGzipWithoutFname(")),
                () -> assertTrue(gameTest.contains("requireExactMaximumHostileFname(")),
                () -> assertTrue(gameTest.contains("saveWithHeldPlatformCopy(")),
                () -> assertTrue(gameTest.contains("requireCanonicalGzipWithoutFname(")),
                () -> assertTrue(gameTest.contains(
                        "if (!ready.rewriteRequired() || !adapter.isDirty())")),
                () -> assertTrue(gameTest.contains(
                        "if (ready.rewriteRequired() || adapter.isDirty())")),
                () -> assertTrue(build.contains("'prepare-hostile-fname'")),
                () -> assertTrue(build.contains(
                        "p4B2HostileFnameGameDirectory.get().asFile.absolutePath")),
                () -> assertTrue(build.contains(
                        "'verify-hostile-fname-first'")),
                () -> assertTrue(build.contains(
                        "'verify-hostile-fname-restart'")),
                () -> assertEquals(
                        10,
                        occurrences(build,
                                "jvmArguments.addAll(p4B2FixedHeapJvmArgs)")),
                () -> assertTrue(Pattern.compile(
                                "def\\s+p4B2FixedHeapJvmArgs\\s*=\\s*\\[\\s*"
                                        + "'-Xms512m',\\s*'-Xmx1024m',\\s*"
                                        + "'-XX:\\+ExitOnOutOfMemoryError',?\\s*]",
                                Pattern.DOTALL)
                        .matcher(build)
                        .find()),
                () -> assertTrue(fixedHeapGateBlock(build).contains(
                        "runP4B2HostileFnameRestartServer")),
                () -> assertTrue(fixedHeapGateBlock(build).contains(
                        "verifyP4B2HostileFnameRestartOutput")),
                () -> assertTrue(workflow.contains(
                        "Run P4-B2-B fixed-heap valid, hostile-FNAME, "
                                + "and quarantine restart gates")),
                () -> assertTrue(workflow.contains(
                        "./gradlew --no-daemon --console=plain p4B2FixedHeapGate")),
                () -> assertTrue(configurationGate.contains("HOSTILE_FNAME_FIRST")),
                () -> assertTrue(configurationGate.contains("source_fname_bytes")),
                () -> assertTrue(configurationGate.contains(
                        "MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES")),
                () -> assertTrue(configurationGate.contains("HOSTILE_FNAME_BYTE")),
                () -> assertTrue(configurationGate.contains(
                        "requireExactMaximumHostileFname")),
                () -> assertTrue(configurationGate.contains(
                        "requireCanonicalGzipWithoutFname")));
    }

    @Test
    void probeSourcesContainNoLaterPhaseOrUnsafeCompositionSurface() throws Exception {
        var code = sources(PROBE_ROOT) + "\n" + sources(GAME_TEST_ROOT);
        // P4-C2-A production registration/lifecycle does not authorize P4-B2-B fixtures to depend
        // on Attachment persistence or bypass the production ingress boundary.
        for (var forbidden : List.of(
                "PlayerSkillAttachment",
                "IAttachmentSerializer",
                "PendingAttachmentJournal",
                "SkillDefinitionSubmissionService",
                "RootCollector",
                "RootIndex",
                "OfflineRoot",
                "Reconciliation",
                "CustomPacketPayload",
                "StreamCodec",
                "java.lang.reflect",
                "setAccessible",
                "sun.misc.Unsafe",
                "@SuppressWarnings",
                ".commit(")) {
            assertFalse(code.contains(forbidden), () -> "later/unsafe B2-B surface: " + forbidden);
        }
        assertAll(
                () -> assertTrue(code.contains("FULL_SIZE_MINIMUM_BYTES = 63 * 1_024 * 1_024")),
                () -> assertTrue(code.contains("IOUtilities.waitUntilIOWorkerComplete()")),
                () -> assertTrue(code.contains("DimensionDataStorage")),
                () -> assertTrue(code.contains("CompoundTag")),
                () -> assertTrue(code.contains("CountDownLatch")),
                () -> assertFalse(code.contains("System.gc(")),
                () -> assertFalse(code.contains("Runtime.getRuntime().freeMemory")));
    }

    @Test
    void productionAndNormalGameTestsRemainUnchangedByProbeTypes() throws Exception {
        var main = sources(PROJECT_ROOT.resolve("src/main/java"));
        assertAll(
                () -> assertFalse(main.contains("P4B2ProbeMain")),
                () -> assertFalse(main.contains("P4B2MemoryGameTests")),
                () -> assertFalse(main.contains("gramarye_p4_b2")),
                () -> assertEquals(12, occurrences(main, "@GameTest(")));
    }

    @Test
    void workflowDeclaresIndependentRequiredMemoryJob() throws Exception {
        var workflow = read(PROJECT_ROOT.resolve(".github/workflows/build.yml"));
        for (var required : List.of(
                "p4-b-memory-gates:",
                "name: P4-B memory gates",
                "- build",
                "- p4-a3-memory-gates",
                "timeout-minutes: 30",
                "./gradlew --no-daemon --console=plain verifyP4B2Configuration",
                "./gradlew --no-daemon --console=plain p4B2FixedHeapGate")) {
            assertTrue(workflow.contains(required), () -> "missing P4-B CI contract: " + required);
        }
        assertFalse(workflow.contains("continue-on-error"));
        assertFalse(workflow.contains("allow-failure"));
    }

    @Test
    void commonsCompressHasOneVersionTruthAndOfficialRuntimePackagingSeams()
            throws Exception {
        var properties = read(PROJECT_ROOT.resolve("gradle.properties"));
        var build = read(PROJECT_ROOT.resolve("build.gradle"));

        assertAll(
                () -> assertEquals(
                        1, occurrences(properties, "commons_compress_version=")),
                () -> assertTrue(properties.contains(
                        "commons_compress_version=1.26.0")),
                () -> assertEquals(
                        1,
                        occurrences(
                                build,
                                "providers.gradleProperty('commons_compress_version')")),
                () -> assertFalse(build.contains("1.26.0")),
                () -> assertTrue(build.contains(
                        "jarJar(implementation(commonsCompressCoordinate))")),
                () -> assertEquals(
                        1,
                        occurrences(build, "implementation(commonsCompressCoordinate)")),
                () -> assertTrue(build.contains(
                        "strictly \"[${commonsCompressVersion}]\"")),
                () -> assertTrue(build.contains("prefer commonsCompressVersion")),
                () -> assertTrue(build.contains(
                        "additionalRuntimeClasspath commonsCompressCoordinate")),
                () -> assertFalse(build.contains(
                        "implementation 'org.apache.commons:commons-compress")),
                () -> assertFalse(build.contains(
                        "implementation \"org.apache.commons:commons-compress")));
    }

    @Test
    void packagedRuntimeUsesDeployableJarAndRetainsDependencyErrorPolicy()
            throws Exception {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var workflow = read(PROJECT_ROOT.resolve(".github/workflows/build.yml"));
        var scriptPath = PROJECT_ROOT.resolve(
                "scripts/run-p4-b2-packaged-runtime-smoke.sh");
        var script = read(scriptPath);
        var configurationGate = read(PROJECT_ROOT.resolve(
                "scripts/verify-p4-b2-b-configuration.sh"));
        var production = withoutCommentsAndLiterals(
                sources(PROJECT_ROOT.resolve("src/main/java")));
        var storeService = withoutCommentsAndLiterals(read(PROJECT_ROOT.resolve(
                "src/main/java/com/yo1no/gramarye/magic/definition/store/"
                        + "SkillDefinitionStoreService.java")));
        var startup = methodBody(storeService, "runP4E3StartupReclaim");
        var lexicalFixture = String.join("",
                "// catch (Error hidden)\r",
                "var text = \"\"\"\ncatch (LinkageError hidden)\n\"\"\";\n",
                "catch (RuntimeException | Error failure) { throw failure; }");
        var maskedLexicalFixture = withoutCommentsAndLiterals(lexicalFixture);
        var packagingVerifier = read(PROBE_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/store/"
                        + "P4B2RuntimePackagingVerifier.java"));

        for (var required : List.of(
                "p4B2PackagedServerInstaller",
                "transitive = false",
                "net.neoforged:neoforge:${neo_version}:installer",
                "prepareP4B2PackagedRuntimeFixture",
                "verifyP4B2PackagedArtifact",
                "stageP4B2PackagedRuntime",
                "runP4B2PackagedRuntimeSmoke",
                "verifyP4B2PackagedRuntimeOutput",
                "p4B2RuntimePackagingGate",
                "tasks.named('jar', Jar).flatMap { it.archiveFile }",
                "scripts/run-p4-b2-packaged-runtime-smoke.sh")) {
            assertTrue(build.contains(required),
                    () -> "missing packaged-runtime build contract: " + required);
        }
        for (var required : List.of(
                "--installServer",
                "@\"$UNIX_ARGS\"",
                "$SERVER_ROOT/mods/gramarye.jar",
                "world/data/gramarye_skill_definitions.dat",
                "-Xlog:class+load=info",
                "GzipCompressorInputStream",
                "ClassNotFoundException|NoClassDefFoundError",
                "mod_count")) {
            assertTrue(script.contains(required),
                    () -> "missing packaged-runtime script contract: " + required);
        }
        assertAll(
                () -> assertTrue(Files.isExecutable(scriptPath)),
                () -> assertFalse(script.contains("./gradlew")),
                () -> assertFalse(script.contains("runServer")),
                () -> assertFalse(script.contains("mods/commons-compress")),
                () -> assertTrue(packagingVerifier.contains(
                        "name.startsWith(\"org/apache/commons/compress/\")")),
                () -> assertTrue(packagingVerifier.contains(
                        "nestedJarCount != 1")),
                () -> assertTrue(packagingVerifier.contains(
                        "META-INF/LICENSE")),
                () -> assertTrue(packagingVerifier.contains(
                        "META-INF/NOTICE")),
                () -> assertFalse(build.contains("shadowJar")),
                () -> assertFalse(build.contains("relocate(")),
                () -> assertFalse(build.contains("com.gradleup.shadow")),
                () -> assertFalse(build.contains("com.github.johnrengelman.shadow")),
                () -> assertEquals(1, dependencyErrorCatchCount(production)),
                () -> assertEquals(1, reviewedStartupErrorCatchCount(startup)),
                () -> assertEquals(0, catchTypeCount(storeService, "Throwable")),
                () -> assertEquals(lexicalFixture.length(), maskedLexicalFixture.length()),
                () -> assertLineEndingsPreserved(lexicalFixture, maskedLexicalFixture),
                () -> assertEquals(1, dependencyErrorCatchCount(maskedLexicalFixture)),
                () -> assertTrue(build.contains(
                        "dependsOn(verifyP4B2PackagedRuntimeOutput)")),
                () -> assertTrue(build.contains("p4B2RuntimePackagingGate")),
                () -> assertTrue(workflow.contains(
                        "./gradlew --no-daemon --console=plain p4B2FixedHeapGate")),
                () -> assertFalse(workflow.contains("continue-on-error")),
                () -> assertFalse(workflow.contains("allow-failure")),
                () -> assertTrue(configurationGate.contains(
                        "runP4B2PackagedRuntimeSmoke")),
                () -> assertTrue(configurationGate.contains(
                        "p4B2RuntimePackagingGate")));
    }

    private static Set<String> javaFiles(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }
    }

    private static String sources(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .map(P4B2BApiGateTest::read)
                    .collect(Collectors.joining("\n"));
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("unable to inspect " + path, exception);
        }
    }

    private static int occurrences(String value, String needle) {
        var result = 0;
        for (var offset = 0; (offset = value.indexOf(needle, offset)) >= 0;
                offset += needle.length()) {
            result++;
        }
        return result;
    }

    private static int dependencyErrorCatchCount(String source) {
        var catchClause = Pattern.compile("catch\\s*\\(([^)]*)\\)", Pattern.DOTALL)
                .matcher(source);
        var forbiddenType = Pattern.compile(
                "(?<![A-Za-z0-9_$.])(?:java\\.lang\\.)?"
                        + "(?:NoClassDefFoundError|LinkageError|Error)"
                        + "(?![A-Za-z0-9_$])");
        var count = 0;
        while (catchClause.find()) {
            if (forbiddenType.matcher(catchClause.group(1)).find()) {
                count++;
            }
        }
        return count;
    }

    private static int catchTypeCount(String source, String typeName) {
        var catchClause = Pattern.compile("catch\\s*\\(([^)]*)\\)", Pattern.DOTALL)
                .matcher(source);
        var exactType = Pattern.compile(
                "(?<![A-Za-z0-9_$.])(?:java\\.lang\\.)?"
                        + Pattern.quote(typeName)
                        + "(?![A-Za-z0-9_$])");
        var count = 0;
        while (catchClause.find()) {
            if (exactType.matcher(catchClause.group(1)).find()) {
                count++;
            }
        }
        return count;
    }

    private static int reviewedStartupErrorCatchCount(String methodBody) {
        var reviewed = Pattern.compile(
                "catch\\s*\\(\\s*RuntimeException\\s*\\|\\s*Error\\s+(failure)\\s*\\)"
                        + "\\s*\\{\\s*if\\s*\\(\\s*recording\\s*\\)\\s*\\{\\s*"
                        + "observationView\\s*\\.\\s*abortRecording\\s*"
                        + "\\(\\s*server\\s*\\)\\s*;\\s*}\\s*"
                        + "throw\\s+\\1\\s*;\\s*}",
                Pattern.DOTALL)
                .matcher(methodBody);
        var count = 0;
        while (reviewed.find()) {
            if (methodBody.substring(reviewed.end()).isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static String methodBody(String source, String methodName) {
        var signature = source.indexOf("private void " + methodName + "(");
        if (signature < 0) {
            throw new AssertionError("method not found: " + methodName);
        }
        var open = source.indexOf('{', signature);
        var depth = 0;
        for (var index = open; index < source.length(); index++) {
            var character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(open + 1, index);
            }
        }
        throw new AssertionError("method body did not close: " + methodName);
    }

    private static String withoutCommentsAndLiterals(String source) {
        var masked = new StringBuilder(source.length());
        var state = LexicalState.CODE;
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            var hasNext = index + 1 < source.length();
            var next = hasNext ? source.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    if (current == '/' && next == '/') {
                        masked.append("  ");
                        index++;
                        state = LexicalState.LINE_COMMENT;
                    } else if (current == '/' && next == '*') {
                        masked.append("  ");
                        index++;
                        state = LexicalState.BLOCK_COMMENT;
                    } else if (isTextBlockOpeningDelimiterAt(source, index)) {
                        masked.append("   ");
                        index += 2;
                        state = LexicalState.TEXT_BLOCK;
                    } else if (current == '"') {
                        masked.append(' ');
                        state = LexicalState.STRING;
                    } else if (current == '\'') {
                        masked.append(' ');
                        state = LexicalState.CHARACTER;
                    } else {
                        masked.append(current);
                    }
                }
                case LINE_COMMENT -> {
                    appendMasked(masked, current);
                    if (current == '\r' || current == '\n') {
                        state = LexicalState.CODE;
                    }
                }
                case BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        masked.append("  ");
                        index++;
                        state = LexicalState.CODE;
                    } else {
                        appendMasked(masked, current);
                    }
                }
                case STRING, CHARACTER -> {
                    appendMasked(masked, current);
                    if (current == '\\' && hasNext) {
                        appendMasked(masked, next);
                        index++;
                    } else if ((state == LexicalState.STRING && current == '"')
                            || (state == LexicalState.CHARACTER && current == '\'')) {
                        state = LexicalState.CODE;
                    }
                }
                case TEXT_BLOCK -> {
                    if (isTripleQuoteAt(source, index)) {
                        masked.append("   ");
                        index += 2;
                        state = LexicalState.CODE;
                    } else if (current == '\\' && hasNext) {
                        appendMasked(masked, current);
                        appendMasked(masked, next);
                        index++;
                        if (next == '\r'
                                && index + 1 < source.length()
                                && source.charAt(index + 1) == '\n') {
                            appendMasked(masked, '\n');
                            index++;
                        }
                    } else {
                        appendMasked(masked, current);
                    }
                }
            }
        }
        if (masked.length() != source.length()) {
            throw new AssertionError("lexical masker changed source length");
        }
        return masked.toString();
    }

    private static boolean isTextBlockOpeningDelimiterAt(String source, int index) {
        if (!isTripleQuoteAt(source, index)) {
            return false;
        }
        for (var cursor = index + 3; cursor < source.length(); cursor++) {
            var character = source.charAt(cursor);
            if (character == '\r' || character == '\n') {
                return true;
            }
            if (character != ' ' && character != '\t' && character != '\f') {
                return false;
            }
        }
        return false;
    }

    private static boolean isTripleQuoteAt(String source, int index) {
        return index + 2 < source.length()
                && source.charAt(index) == '"'
                && source.charAt(index + 1) == '"'
                && source.charAt(index + 2) == '"';
    }

    private static void appendMasked(StringBuilder masked, char character) {
        masked.append(character == '\r' || character == '\n' ? character : ' ');
    }

    private static void assertLineEndingsPreserved(String original, String masked) {
        for (var index = 0; index < original.length(); index++) {
            if (original.charAt(index) == '\r' || original.charAt(index) == '\n'
                    || masked.charAt(index) == '\r' || masked.charAt(index) == '\n') {
                assertEquals(original.charAt(index), masked.charAt(index),
                        "line terminator changed at " + index);
            }
        }
    }

    private static String fixedHeapGateBlock(String build) {
        var start = build.indexOf("tasks.register('p4B2FixedHeapGate')");
        var end = build.indexOf("tasks.named('test', Test).configure", start);
        if (start < 0 || end < 0) {
            throw new AssertionError("P4-B2 fixed-heap aggregate block not found");
        }
        return build.substring(start, end);
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

    private enum LexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TEXT_BLOCK
    }
}
