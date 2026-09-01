package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** Exact package, public-surface, and owner-bound handoff gate for the P4-E1-B1 capture. */
final class P4E1B1ApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path MAIN_CLASSES = PROJECT_ROOT.resolve("build/classes/java/main");
    private static final Path STORE_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store");
    private static final Path PLAYER_SERVICE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java");
    private static final Path PLAYER_OBSERVATION = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSourceObservation.java");

    @Test
    void exactB1OwnersArePackagePrivateAndInventoryIsClosed() {
        var owners = List.of(
                P4E1RootSourceFamily.class,
                P4E1SourceInventory.class,
                P4E1GlobalSourceCapture.class,
                P4E1RawClaimBuffer.class,
                P4E1PendingJournalObservation.class);
        assertTrue(owners.stream().noneMatch(type -> Modifier.isPublic(type.getModifiers())));
        assertEquals(
                List.of("PLAYER_SKILL_ATTACHMENT", "PENDING_ATTACHMENT_JOURNAL"),
                Arrays.stream(P4E1RootSourceFamily.values()).map(Enum::name).toList());
        assertEquals(
                Set.of("Captured", "Incomplete", "OverLimit"),
                Arrays.stream(P4E1GlobalSourceCapture.CaptureResult.class
                                .getDeclaredClasses())
                        .map(Class::getSimpleName)
                        .collect(Collectors.toSet()));
    }

    @Test
    void onlineSurfaceIsExactOpaqueAndContainsNoRootCollection() {
        var reviewedNames = Set.of(
                "observeOnlineForRootAudit",
                "maximumRootAuditAttachmentEncodedBytes",
                "onlineRootState",
                "onlineRootUnavailableReason",
                "onlineRootCount",
                "drainOnlineRootProjection",
                "discardOnlineRootProjection",
                "isOnlineRootWitnessCurrent",
                "discardOnlineRootWitness",
                "discardOnlineRootAuditHandle");
        var reviewed = Arrays.stream(PlayerSkillAttachmentService.class.getDeclaredMethods())
                .filter(method -> reviewedNames.contains(method.getName()))
                .toList();
        assertEquals(reviewedNames,
                reviewed.stream().map(method -> method.getName()).collect(Collectors.toSet()));
        assertEquals(reviewedNames.size(), reviewed.size());
        assertTrue(reviewed.stream().allMatch(method -> Modifier.isPublic(method.getModifiers())));
        assertTrue(reviewed.stream()
                .flatMap(method -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(method.getReturnType()),
                        Arrays.stream(method.getParameterTypes())))
                .noneMatch(type -> Collection.class.isAssignableFrom(type)
                        || Iterable.class.isAssignableFrom(type)
                        || Tag.class.isAssignableFrom(type)));

        var handle = PlayerSkillAttachmentService.OnlineRootAuditHandle.class;
        assertTrue(Modifier.isPublic(handle.getModifiers()));
        assertTrue(Modifier.isFinal(handle.getModifiers()));
        assertTrue(Arrays.stream(handle.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
        assertEquals(0, Arrays.stream(handle.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())
                        || Modifier.isProtected(method.getModifiers()))
                .count());
        assertTrue(Arrays.stream(handle.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())));
    }

    @Test
    void staticBoundaryHasNoSecondCeilingRawLeakOrLaterPhaseOwner() throws Exception {
        var b1 = Files.readString(STORE_ROOT.resolve("P4E1GlobalSourceCapture.java"))
                + Files.readString(STORE_ROOT.resolve("P4E1RawClaimBuffer.java"))
                + Files.readString(STORE_ROOT.resolve("P4E1SourceInventory.java"))
                + Files.readString(STORE_ROOT.resolve("P4E1PendingJournalObservation.java"));
        var service = Files.readString(PLAYER_SERVICE);
        assertTrue(b1.contains("MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM")
                || Files.readString(STORE_ROOT.resolve("P4E1AuditBudget.java"))
                        .contains("MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM"));
        for (var forbidden : List.of(
                "65_536",
                "Files.readAllBytes",
                "java.util.zip.GZIPInputStream",
                "NbtAccounter.unlimitedHeap",
                "@SuppressWarnings",
                "java.lang.reflect",
                "sun.misc.Unsafe",
                "Executor",
                "Future",
                "parallelStream(",
                "SkillRetentionRootSnapshot",
                ".reclaim(",
                "ServerStartingEvent",
                "PlayerEvent",
                "CustomPacketPayload",
                "P4E1B2BApiGateTest",
                "SkillRetentionRootAuditResult",
                "ReconciliationRequired",
                "CompleteResult",
                "RootIndex")) {
            assertFalse(b1.contains(forbidden), forbidden);
        }
        assertFalse(service.contains("List<OnlineRoot"));
        assertFalse(service.contains("Iterable<OnlineRoot"));
    }

    @Test
    void checkpointOrderAndOnlineRootCleanupRemainExplicit() throws Exception {
        var capture = Files.readString(STORE_ROOT.resolve("P4E1GlobalSourceCapture.java"));
        assertOrdered(
                capture,
                "P4E1SourceAdmissionPreflight.evaluate()",
                "observeP4E1StoreReady(server)",
                "observeP4E1Journal(server, storeWitness)",
                "P4E1SourceInventory.capture(attachmentService, journal)",
                "P4E1PlayerDataDirectorySnapshot.capture(",
                "captureOnlineIdentities(",
                "P4E1IntegratedSnapshotTraversal.captureForGlobal(server, budget)",
                "arbitrate(directory, integrated, online)");
        assertOrdered(
                capture,
                "for (var selectedSource : selected.values())",
                "processPlayerSource(context, selectedSource)",
                "directory.verifyUnchanged()",
                "processJournal(context)");

        var observation = Files.readString(PLAYER_OBSERVATION);
        assertTrue(observation.contains("finally {\n                ready = null;"));
        assertTrue(observation.contains("void discardRoots()"));
        assertTrue(observation.contains("void requireCurrentThread()"));
        assertTrue(Files.readString(PLAYER_SERVICE)
                .contains("handle.observation.requireCurrentThread()"));
    }

    @Test
    void sourceInventorySwitchIsExhaustiveAndCaptureClearsHeavyReferences() throws Exception {
        var inventory = Files.readString(STORE_ROOT.resolve("P4E1SourceInventory.java"));
        var capture = Files.readString(STORE_ROOT.resolve("P4E1GlobalSourceCapture.java"));
        assertTrue(inventory.contains("EnumSet.allOf(P4E1RootSourceFamily.class)"));
        assertTrue(inventory.contains("case PLAYER_SKILL_ATTACHMENT ->"));
        assertTrue(inventory.contains("case PENDING_ATTACHMENT_JOURNAL ->"));
        assertFalse(inventory.contains("default ->"));
        for (var cleared : List.of(
                "ownerIdentity = null",
                "serverIdentity = null",
                "creationThreadIdentity = null",
                "playerListIdentity = null",
                "storeWitness = null",
                "journalWitness = null",
                "inventoryWitness = null",
                "directoryWitness = null",
                "integratedWitness = null",
                "claims = null",
                "sources = null",
                "selectedFiles = null",
                "summary = null")) {
            assertTrue(capture.contains(cleared), cleared);
        }
        assertTrue(capture.contains("claims.discard()"));
        assertTrue(capture.contains("sources.clear()"));

        var witness = P4E1SourceInventory.Witness.class;
        assertEquals(
                Set.of(
                        "coverage",
                        "playerProviderIdentity",
                        "journalProviderIdentity",
                        "definitionIdentity"),
                Arrays.stream(witness.getDeclaredFields())
                        .map(field -> field.getName())
                        .collect(Collectors.toSet()));
        var current = witness.getDeclaredMethod(
                "isCurrent",
                PlayerSkillAttachmentService.class,
                P4E1PendingJournalObservation.Ready.class);
        assertFalse(Modifier.isPublic(current.getModifiers()));
        assertFalse(Modifier.isProtected(current.getModifiers()));
        assertFalse(Modifier.isPrivate(current.getModifiers()));
    }

    @Test
    void captureAndClaimedHandlesHaveNoPublicConstructionOrStaticRetention() {
        for (var type : List.of(
                P4E1GlobalSourceCapture.Captured.class,
                P4E1GlobalSourceCapture.Claimed.class)) {
            assertFalse(Modifier.isPublic(type.getModifiers()));
            assertTrue(Arrays.stream(type.getDeclaredConstructors())
                    .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                            || Modifier.isProtected(constructor.getModifiers())));
            assertTrue(Arrays.stream(type.getDeclaredFields())
                    .noneMatch(field -> Modifier.isStatic(field.getModifiers())));
        }
        assertFalse(Arrays.stream(P4E1GlobalSourceCapture.class.getDeclaredFields())
                .anyMatch(field -> Modifier.isStatic(field.getModifiers())));
    }

    @Test
    void capturedHandoffIsBoundOnlyToTheExactGroupedAuditOwner() throws Exception {
        var captures = Arrays.stream(P4E1GlobalSourceCapture.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("capture"))
                .toList();
        assertEquals(1, captures.size());
        assertEquals(
                List.of(
                        net.minecraft.server.MinecraftServer.class,
                        SkillDefinitionStoreService.class,
                        PlayerSkillAttachmentService.class,
                        P4E1GroupedStoreAudit.class),
                Arrays.asList(captures.getFirst().getParameterTypes()));

        var claims = Arrays.stream(P4E1GlobalSourceCapture.Captured.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("claim"))
                .toList();
        assertEquals(1, claims.size());
        var claim = claims.getFirst();
        assertEquals(
                List.of(
                        P4E1GroupedStoreAudit.class,
                        ProductThreadPrecondition.Decision.class),
                Arrays.asList(claim.getParameterTypes()));
        assertEquals(P4E1GlobalSourceCapture.Claimed.class, claim.getReturnType());
        assertFalse(Modifier.isPublic(claim.getModifiers()));
        assertFalse(Modifier.isProtected(claim.getModifiers()));
        assertFalse(Modifier.isPrivate(claim.getModifiers()));
        assertFalse(Arrays.stream(P4E1GlobalSourceCapture.Captured.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("claim")
                        && method.getParameterCount() != 2));

        var activeMethods = Arrays.stream(
                        P4E1GlobalSourceCapture.Claimed.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("requireActive"))
                .toList();
        assertEquals(1, activeMethods.size());
        var requireActive = activeMethods.getFirst();
        assertEquals(
                List.of(
                        P4E1GroupedStoreAudit.class,
                        ProductThreadPrecondition.Decision.class),
                Arrays.asList(requireActive.getParameterTypes()));
        assertEquals(void.class, requireActive.getReturnType());
        assertTrue(Modifier.isPrivate(requireActive.getModifiers()));

        for (var owner : List.of(
                P4E1GlobalSourceCapture.class,
                P4E1GlobalSourceCapture.Captured.class,
                P4E1GlobalSourceCapture.Claimed.class,
                P4E1GroupedStoreAudit.class)) {
            assertTrue(Arrays.stream(owner.getDeclaredFields())
                    .noneMatch(field -> field.getType()
                            == ProductThreadPrecondition.Decision.class));
        }

        var grouped = withoutCommentsAndLiterals(
                Files.readString(STORE_ROOT.resolve("P4E1GroupedStoreAudit.java")));
        var capture = withoutCommentsAndLiterals(
                Files.readString(STORE_ROOT.resolve("P4E1GlobalSourceCapture.java")));
        var holder = withoutCommentsAndLiterals(
                Files.readString(STORE_ROOT.resolve("SkillSavedDataLifecycleGameTests.java")));
        var auditCore = bodyFollowing(
                grouped,
                "Result audit(P4E1GlobalSourceCapture.Captured capture, long observedThreadId)");
        var claimBody = bodyFollowing(capture, "Claimed claim(");
        var activeBody = bodyFollowing(capture, "private void requireActive(");

        assertOrdered(
                auditCore,
                "var decision = ProductThreadPrecondition.classify(",
                "capture.claim(this, decision)");
        assertEquals(1, qualifiedInvocationCount(
                auditCore, "ProductThreadPrecondition", "classify"));
        assertEquals(1, invocationCount(auditCore, "claim"));
        assertOrdered(
                claimBody,
                "Objects.requireNonNull(decision",
                "consumed = true",
                "new Claimed(",
                "clearReferences()",
                "moved.requireActive(owner, decision)");
        assertEquals(1, invocationCount(claimBody, "requireActive"));
        assertOrdered(
                activeBody,
                "Objects.requireNonNull(decision",
                "requireClaimedOwner(owner)",
                "owner.requireCaptureBinding(",
                "decision");
        assertEquals(1, invocationCount(activeBody, "requireCaptureBinding"));
        assertFalse(Pattern.compile("\\bdecision\\s*=(?!=)")
                .matcher(claimBody + activeBody)
                .find());
        assertFalse(Pattern.compile(
                        "\\bpublic\\b[^;{]*ProductThreadPrecondition\\s*\\.\\s*Decision",
                        Pattern.DOTALL)
                .matcher(grouped + capture)
                .find());
        assertFalse(Pattern.compile(
                        "\\.\\s*claim\\s*\\([^;]*"
                                + "ProductThreadPrecondition\\s*\\.\\s*Decision",
                        Pattern.DOTALL)
                .matcher(holder)
                .find());
        assertEquals(0, invocationCount(holder, "requireActive"));

        var groupedBytecode = javap("P4E1GroupedStoreAudit");
        var capturedBytecode = javap("P4E1GlobalSourceCapture$Captured");
        var claimedBytecode = javap("P4E1GlobalSourceCapture$Claimed");
        var decisionDescriptor = "Lcom/yo1no/gramarye/magic/definition/store/"
                + "ProductThreadPrecondition$Decision;";
        var claimDescriptor = "(Lcom/yo1no/gramarye/magic/definition/store/"
                + "P4E1GroupedStoreAudit;" + decisionDescriptor + ")"
                + "Lcom/yo1no/gramarye/magic/definition/store/"
                + "P4E1GlobalSourceCapture$Claimed;";
        var activeDescriptor = "(Lcom/yo1no/gramarye/magic/definition/store/"
                + "P4E1GroupedStoreAudit;" + decisionDescriptor + ")V";
        var bindingDescriptor = "(Lnet/minecraft/server/MinecraftServer;"
                + "Ljava/lang/Thread;I" + decisionDescriptor + ")V";
        assertTrue(capturedBytecode.contains("descriptor: " + claimDescriptor));
        assertTrue(claimedBytecode.contains("descriptor: " + activeDescriptor));
        assertEquals(1, invocationCommentCount(
                groupedBytecode,
                "P4E1GlobalSourceCapture$Captured.claim:" + claimDescriptor));
        assertEquals(1, invocationCommentCount(
                capturedBytecode,
                "P4E1GlobalSourceCapture$Claimed.requireActive:" + activeDescriptor));
        assertEquals(1, invocationCommentCount(
                claimedBytecode,
                "P4E1GroupedStoreAudit.requireCaptureBinding:" + bindingDescriptor));
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

    private static void assertOrdered(String source, String... tokens) {
        var previous = -1;
        for (var token : tokens) {
            var next = source.indexOf(token, previous + 1);
            assertTrue(next > previous, token);
            previous = next;
        }
    }

    private static int qualifiedInvocationCount(
            String source, String receiver, String methodName) {
        var invocation = Pattern.compile(
                        "(?<![A-Za-z0-9_$])"
                                + Pattern.quote(receiver)
                                + "\\s*\\.\\s*"
                                + Pattern.quote(methodName)
                                + "\\s*\\(")
                .matcher(source);
        var count = 0;
        while (invocation.find()) {
            count++;
        }
        return count;
    }

    private static int invocationCount(String source, String methodName) {
        var invocation = Pattern.compile(
                        "\\.\\s*" + Pattern.quote(methodName) + "\\s*\\(")
                .matcher(source);
        var count = 0;
        while (invocation.find()) {
            count++;
        }
        return count;
    }

    private static String bodyFollowing(String source, String marker) {
        var signature = source.indexOf(marker);
        if (signature < 0) {
            throw new AssertionError("source marker not found: " + marker);
        }
        var open = source.indexOf('{', signature + marker.length());
        if (open < 0) {
            throw new AssertionError("body opening brace not found after: " + marker);
        }
        var depth = 0;
        for (var index = open; index < source.length(); index++) {
            var character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(open + 1, index);
            }
        }
        throw new AssertionError("body did not close after: " + marker);
    }

    private static String javap(String simpleBinaryName) {
        var binaryName = "com.yo1no.gramarye.magic.definition.store." + simpleBinaryName;
        var result = runJdkTool(
                "javap", "-classpath", MAIN_CLASSES.toString(), "-p", "-s", "-c", binaryName);
        assertEquals(0, result.exitCode(), result.output());
        return result.output();
    }

    private static JdkToolResult runJdkTool(String toolName, String... arguments) {
        var tool = ToolProvider.findFirst(toolName)
                .orElseThrow(() -> new AssertionError("JDK tool unavailable: " + toolName));
        var output = new StringWriter();
        var writer = new PrintWriter(output);
        var exitCode = tool.run(writer, writer, arguments);
        writer.flush();
        return new JdkToolResult(exitCode, output.toString());
    }

    private static int invocationCommentCount(String bytecode, String ownerMethodDescriptor) {
        var needle = ownerMethodDescriptor;
        var count = 0;
        for (var offset = 0; (offset = bytecode.indexOf(needle, offset)) >= 0;
                offset += needle.length()) {
            count++;
        }
        return count;
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

    private enum LexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TEXT_BLOCK
    }

    private record JdkToolResult(int exitCode, String output) {
    }
}
