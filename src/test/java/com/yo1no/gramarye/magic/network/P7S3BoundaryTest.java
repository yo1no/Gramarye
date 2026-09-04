package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class P7S3BoundaryTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path NETWORK_MAIN = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/network");
    private static final Set<String> S3_NETWORK_PATHS = Set.of(
            "P7AdmissionDispositionMapper.java",
            "P7AdvisoryTargetValidator.java",
            "P7ReloadAdmissionGate.java",
            "P7ServerAccess.java",
            "P7ServerAuthorizationBoundary.java",
            "P7ServerAuthorizationDispatcher.java",
            "P7ServerDisconnectPort.java",
            "P7ServerIntentResult.java",
            "P7ServerIntentResultSink.java",
            "P7ServerSessionService.java",
            "P7ServerSessionState.java");

    @Test
    void S3ProductionPathSetAndSolePublicBoundaryAreExact() throws Exception {
        var actual = S3_NETWORK_PATHS.stream()
                .filter(name -> Files.isRegularFile(NETWORK_MAIN.resolve(name)))
                .collect(Collectors.toUnmodifiableSet());
        var publicP7 = Pattern.compile(
                "(?m)^public\\s+(?:(?:final|sealed|non-sealed|abstract)\\s+)*"
                        + "(?:class|interface|record|enum)\\s+(P7[A-Za-z0-9_$]*)\\b");
        Set<String> names;
        try (var paths = Files.walk(MAIN_JAVA)) {
            names = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> publicP7.matcher(read(path)).results())
                    .map(match -> match.group(1))
                    .collect(Collectors.toUnmodifiableSet());
        }

        assertEquals(S3_NETWORK_PATHS, actual);
        assertEquals(Set.of("P7ServerAuthorizationBoundary"), names);
        assertEquals(4, Arrays.stream(P7ServerAuthorizationBoundary.class.getDeclaredClasses())
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .count());
    }

    @Test
    void rootOwnerHasOnlyTheMandatoryPublicOverrideAndThreeExactServices()
            throws Exception {
        var owner = Class.forName("com.yo1no.gramarye.P7AuthenticatedPlayerCastIngress");
        var publicProtected = Arrays.stream(owner.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())
                        || Modifier.isProtected(method.getModifiers()))
                .toList();
        var retained = Arrays.stream(owner.getDeclaredFields())
                .map(field -> field.getType().getName())
                .collect(Collectors.toSet());

        assertTrue(Modifier.isFinal(owner.getModifiers()));
        assertFalse(Modifier.isPublic(owner.getModifiers()));
        assertEquals(1, publicProtected.size());
        assertEquals("authorizeAndAdmit", publicProtected.get(0).getName());
        assertEquals(Set.of(
                "com.yo1no.gramarye.SkillRuntimeService",
                "com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService",
                "com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService"),
                retained);
    }

    @Test
    void S3SourcesRetainNoLiveObjectsOrExcludedTransmissionAndExecutionPaths() {
        var combined = S3_NETWORK_PATHS.stream()
                .map(NETWORK_MAIN::resolve)
                .map(P7S3BoundaryTest::read)
                .collect(Collectors.joining("\n"));

        for (var forbidden : List.of(
                "IPayloadContext",
                "ByteBuf",
                "PacketDistributor",
                "sendToPlayer(",
                "sendToServer(",
                "P7ManaSnapshotBridge",
                "P6RuntimeExecutionBridge",
                "CompletableFuture",
                "ExecutorService",
                "new Thread(",
                "System.currentTimeMillis(",
                "System.nanoTime(")) {
            assertFalse(combined.contains(forbidden), forbidden);
        }
        assertEquals(0, occurrences(combined, "@SubscribeEvent"));
        assertEquals(0, occurrences(combined, "@Game" + "Test"));
    }

    @Test
    void bootstrapInstallsOneIngressAfterTheExistingServiceGraph() {
        var source = read(MAIN_JAVA.resolve("com/yo1no/gramarye/Gramarye.java"));
        var attachment = source.indexOf("PlayerSkillAttachmentService.registerOn(modBus)");
        var store = source.indexOf("SkillDefinitionStoreService.registerOn(");
        var runtime = source.indexOf("SkillRuntimeService.create(");
        var ingress = source.indexOf("new P7AuthenticatedPlayerCastIngress(");
        var capability = source.indexOf("P6RuntimeExecutionCapability.forRuntimeAdapter()");
        var install = source.indexOf("P7ServerAuthorizationBoundary.install(");

        assertTrue(attachment >= 0 && attachment < store);
        assertTrue(store < runtime && runtime < ingress);
        assertTrue(ingress < install && install < capability);
        assertEquals(1, occurrences(source, "new P7AuthenticatedPlayerCastIngress("));
        assertEquals(1, occurrences(source, "P7ServerAuthorizationBoundary.install("));
    }

    @Test
    void GameTestInventoryRemainsNineteen() throws Exception {
        String combined;
        try (var paths = Files.walk(MAIN_JAVA)) {
            combined = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(P7S3BoundaryTest::read)
                    .collect(Collectors.joining("\n"));
        }
        assertEquals(19, occurrences(combined, "@Game" + "Test("));
    }

    private static long occurrences(String source, String needle) {
        return source.split(Pattern.quote(needle), -1).length - 1L;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception failure) {
            throw new AssertionError("cannot read " + path, failure);
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
