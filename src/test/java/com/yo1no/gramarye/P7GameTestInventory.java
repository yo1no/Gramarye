package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Exact current source inventory consumed by historical GameTest regression assertions. */
public final class P7GameTestInventory {
    private static final String MAIN = "src/main/java/com/yo1no/gramarye/";
    private static final Pattern TEST = Pattern.compile(
            "@GameTest\\s*\\([^)]*\\)\\s+public\\s+static\\s+void\\s+(\\w+)\\s*\\(",
            Pattern.DOTALL);
    private static final Map<String, Set<String>> HISTORICAL = Map.of(
            "gametest/PlatformGameTests.java", Set.of(
                    "dedicatedServerLoads",
                    "customDescriptorRegistriesLoadEmpty",
                    "productionDefinitionLookupsResolveMissingTypesSafely",
                    "descriptorMigrationCoverageAuditPassesAfterRegistryFreeze"),
            "magic/definition/player/PlayerSkillAttachmentGameTests.java", Set.of(
                    "registeredAttachmentPersistsThroughActualPlayerdataSaveAndReload",
                    "registeredQuarantineAndCopyLifecycleRemainTotal"),
            "magic/definition/store/SkillSavedDataLifecycleGameTests.java", Set.of(
                    "startupInstalledExactReadyAdapterInOverworldCache"),
            "magic/definition/store/SkillSubmissionRecoveryGameTests.java", Set.of(
                    "persistedBaseReplaysPendingChainOnLogin",
                    "persistedIntermediateClearsPrefixBeforeReplayOnLogin",
                    "persistedFinalClearsPendingChainWithoutReplayOnLogin"),
            "magic/definition/submission/SkillDefinitionSubmissionGameTests.java", Set.of(
                    "fullSubmissionCommitsStoreJournalThenAttachmentExactlyOnce",
                    "postCommitAttachmentDriftReturnsPendingRecovery"),
            "magic/runtime/mana/ManaLifecycleGameTests.java", Set.of(
                    "newPlayerAbsentStateIsAvailableZero",
                    "validAttachmentSerializesAndLoadsExactly",
                    "malformedAttachmentRemainsUnavailableWithoutMutation",
                    "deathCloneCopiesExactManaState",
                    "nonDeathCloneCopiesExactManaState",
                    "dimensionTravelKeepsSingleManaTruth",
                    "duplicatePersistentManaTruthIsAbsent"));
    private static final Map<String, Set<String>> S4 = Map.of(
            "P7S4LoginManaGameTests.java", Set.of(
                    "manaObservationPreservesAvailableAndMalformedAttachmentTruth",
                    "loginPortRejectsNoncurrentPlayerBeforeSessionOpen",
                    "e2NormalAndChangedTerminalsHandoffOnceAndQuarantineNeverHandoffs",
                    "e2LoginPortRuntimeFailurePropagatesTheSameObject",
                    "e2LoginPortErrorPropagatesTheSameObject"),
            "magic/network/P7S4NetworkGameTests.java", Set.of(
                    "actualPostE2LoginOpensOneSessionAndSubmitsOneInitialFullSet",
                    "actualRespawnDimensionAndReconnectPreserveThenReplaceSessionIdentity"));

    private P7GameTestInventory() {
    }

    public static int totalCount() {
        verify();
        return 19 + s4Count();
    }

    public static int s4Count() {
        return S4.values().stream().mapToInt(Set::size).sum();
    }

    public static boolean isS4Harness(Path path) {
        var normalized = path.toAbsolutePath().normalize();
        var root = projectRoot().resolve(MAIN);
        return normalized.equals(root.resolve("P7S4LoginManaGameTests.java"))
                || normalized.equals(root.resolve(
                        "magic/definition/store/SkillSubmissionRecoveryGameTests.java"))
                || normalized.equals(root.resolve("magic/network/P7S4NetworkGameTests.java"));
    }

    public static String productionSource() {
        try (var paths = Files.walk(projectRoot().resolve(MAIN))) {
            var source = new StringBuilder();
            for (var path : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !isS4Harness(path)).toList()) {
                source.append(Files.readString(path)).append('\n');
            }
            return source.toString();
        } catch (IOException failure) {
            throw new AssertionError("production source unavailable", failure);
        }
    }

    public static boolean isS4ReconciliationPath(Path path) {
        var relative = projectRoot().resolve(MAIN).relativize(path.toAbsolutePath().normalize())
                .toString();
        return Set.of("magic/network/P7ReloadAdmissionGate.java",
                "magic/network/P7ServerLifecycleCoordinator.java",
                "P7S4LoginManaGameTests.java",
                "magic/definition/store/SkillSubmissionRecoveryGameTests.java",
                "magic/network/P7S4NetworkGameTests.java").contains(relative);
    }

    public static void verify() {
        var root = projectRoot().resolve(MAIN);
        var expected = new java.util.HashMap<>(HISTORICAL);
        expected.putAll(S4);
        var actual = new java.util.HashMap<String, Set<String>>();
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")).toList()) {
                var source = Files.readString(path);
                var methods = new HashSet<String>();
                var matcher = TEST.matcher(source);
                while (matcher.find()) {
                    if (!methods.add(matcher.group(1))) {
                        throw new AssertionError("duplicate GameTest method: " + path);
                    }
                }
                var markers = Pattern.compile("@GameTest\\s*\\(")
                        .matcher(source).results().count();
                if (markers != methods.size()) {
                    throw new AssertionError("unsupported GameTest declaration: " + path);
                }
                if (!methods.isEmpty()) {
                    actual.put(root.relativize(path).toString(), Set.copyOf(methods));
                }
            }
        } catch (IOException failure) {
            throw new AssertionError("GameTest sources unavailable", failure);
        }
        if (!actual.equals(expected)) {
            throw new AssertionError("GameTest source path/method inventory differs: " + actual);
        }
    }

    private static Path projectRoot() {
        for (var path = Path.of("").toAbsolutePath().normalize(); path != null;
                path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("settings.gradle"))) {
                return path;
            }
        }
        throw new AssertionError("project root unavailable");
    }
}
