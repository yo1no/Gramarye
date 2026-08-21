package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Static lifecycle proof for the synchronous recovery-to-publication P4-E2 call chain. */
final class P4E2LifecycleOrderingTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path STORE_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store");
    private static final Path RECOVERY_SERVICE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/submission/"
                    + "SkillSubmissionRecoveryService.java");
    private static final Path COORDINATOR = STORE_ROOT.resolve(
            "P4E2OnlineReconciliationCoordinator.java");
    private static final Path GROUPED_VALIDATION = STORE_ROOT.resolve(
            "P4E2GroupedStoreValidation.java");
    private static final Path STORE_SERVICE = STORE_ROOT.resolve(
            "SkillDefinitionStoreService.java");
    private static final Path PLAYER_SERVICE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java");

    @Test
    void soleLoginOwnerRunsRecoveryBeforeItsSingleTypedContinuation() throws Exception {
        var recovery = withoutCommentsAndLiterals(Files.readString(RECOVERY_SERVICE));
        var production = javaSources(MAIN_JAVA);
        assertAll(
                () -> assertEquals(1, occurrences(
                        production, "PlayerEvent.PlayerLoggedInEvent")),
                () -> assertEquals(
                        Set.of(relative(RECOVERY_SERVICE)),
                        sourcePathsContaining("PlayerEvent.PlayerLoggedInEvent")),
                () -> assertEquals(1, occurrences(recovery, "recoverPersistedPlayer(player)")),
                () -> assertTrue(recovery.indexOf(
                                "requireE2RecoveryVocabularyInitialized()")
                        < recovery.indexOf("recoverPersistedPlayer(player)")),
                () -> assertTrue(recovery.contains(
                        "RecoveryKind.NO_PENDING.ordinal()")),
                () -> assertTrue(recovery.contains(
                        "recoveryKind(RecoveryUnavailableReason.JOURNAL_NOT_BOOTSTRAPPED)")),
                () -> assertEquals(1, occurrences(recovery, ".reconcileAfterRecovery(")),
                () -> assertTrue(recovery.indexOf("recoverPersistedPlayer(player)")
                        < recovery.indexOf(".reconcileAfterRecovery(")),
                () -> assertTrue(recovery.indexOf(
                                "var exactOutcome = Objects.requireNonNull(outcome")
                        < recovery.indexOf(".reconcileAfterRecovery(")),
                () -> assertFalse(recovery.contains("PlayerLoggedOutEvent")),
                () -> assertFalse(recovery.contains("PlayerEvent.Clone")));
    }

    @Test
    void preparationAndFreshnessBracketAcceptedInvalidationBeforePublication()
            throws Exception {
        var coordinator = withoutCommentsAndLiterals(Files.readString(COORDINATOR));
        var prepare = coordinator.indexOf(".prepareOnlineReconciliation(");
        var firstCheck = coordinator.indexOf(".checkPreparedReconciliationCurrent(", prepare);
        var invalidation = coordinator.indexOf(
                "var invalidation = invalidate(server, player);", firstCheck);
        var finalCheck = coordinator.indexOf(
                ".checkPreparedReconciliationCurrent(", invalidation);
        var finalFreshnessGate = coordinator.lastIndexOf("if (!allFresh(");
        var finalStoreCheck = coordinator.indexOf("storeWitness.isCurrent()", invalidation);
        var finalAuditCheck = coordinator.indexOf(
                "rootAuditService.isReconciliationInvalidationCurrent(", invalidation);
        var publish = coordinator.indexOf(".publishPreparedReconciliation(", finalCheck);

        assertAll(
                () -> assertTrue(prepare >= 0, "preparation call is absent"),
                () -> assertTrue(firstCheck > prepare,
                        "first currentness check does not follow preparation"),
                () -> assertTrue(invalidation > firstCheck,
                        "invalidation does not follow the first currentness check"),
                () -> assertTrue(finalCheck > invalidation,
                        "final no-yield currentness check does not follow invalidation"),
                () -> assertTrue(finalFreshnessGate > invalidation
                                && finalFreshnessGate < publish,
                        "final combined freshness gate does not guard publication"),
                () -> assertTrue(finalStoreCheck > invalidation && finalStoreCheck < publish,
                        "final Store witness check does not guard publication"),
                () -> assertTrue(finalAuditCheck > invalidation && finalAuditCheck < publish,
                        "final audit invalidation check does not guard publication"),
                () -> assertTrue(publish > finalCheck,
                        "publication does not follow the final currentness check"),
                () -> assertEquals(2, occurrences(
                        coordinator, ".checkPreparedReconciliationCurrent(")),
                () -> assertEquals(2, occurrences(
                        coordinator, "var invalidation = invalidate(server, player);")),
                () -> assertEquals(1, occurrences(
                        coordinator, ".invalidateForReconciliation(")),
                () -> assertEquals(1, occurrences(
                        coordinator, ".publishPreparedReconciliation(")),
                () -> assertFalse(coordinator.contains("Thread.yield(")),
                () -> assertFalse(coordinator.contains("Thread.sleep(")));
    }

    @Test
    void changedRecoveryInvalidatesBeforeAnyE2StatusAllocation() throws Exception {
        var recovery = withoutCommentsAndLiterals(Files.readString(RECOVERY_SERVICE));
        var coordinator = withoutCommentsAndLiterals(Files.readString(COORDINATOR));
        var continuation = slice(
                recovery,
                "    public static final class RecoveryContinuation",
                "\n    interface Dependencies");
        var changedCheck = coordinator.indexOf(
                "if (entriesCleared > 0 || stepsReplayed > 0)");
        var earlyInvalidation = coordinator.indexOf(
                "var invalidation = invalidate(server, player);", changedCheck);
        var statusAllocation = coordinator.indexOf(
                "status = recoveryStatus(", changedCheck);
        var kind = continuation.indexOf("var kind = exactOutcome.e2Kind()");
        var entriesCleared = continuation.indexOf(
                "var entriesCleared = exactOutcome.e2EntriesCleared()", kind);
        var stepsReplayed = continuation.indexOf(
                "var stepsReplayed = exactOutcome.e2StepsReplayed()", entriesCleared);
        var exceptionClass = continuation.indexOf(
                "var existingExceptionClass = exactOutcome.e2ExceptionClass()", stepsReplayed);
        var dispatch = continuation.indexOf(".reconcileAfterRecovery(", exceptionClass);

        assertAll(
                () -> assertTrue(changedCheck >= 0),
                () -> assertTrue(earlyInvalidation > changedCheck),
                () -> assertTrue(statusAllocation > earlyInvalidation),
                () -> assertFalse(recovery.contains("recoveryStatus(")),
                () -> assertFalse(continuation.contains("new RecoveryStatus")),
                () -> assertFalse(continuation.contains(".map(")),
                () -> assertFalse(continuation.contains(".substring(")),
                () -> assertTrue(kind >= 0),
                () -> assertTrue(entriesCleared > kind),
                () -> assertTrue(stepsReplayed > entriesCleared),
                () -> assertTrue(exceptionClass > stepsReplayed),
                () -> assertTrue(dispatch > exceptionClass),
                () -> assertEquals(1, occurrences(
                        continuation, "exactOutcome.e2Kind()")),
                () -> assertEquals(1, occurrences(
                        continuation, "exactOutcome.e2EntriesCleared()")),
                () -> assertEquals(1, occurrences(
                        continuation, "exactOutcome.e2StepsReplayed()")),
                () -> assertEquals(1, occurrences(
                        continuation, "exactOutcome.e2ExceptionClass()")));
    }

    @Test
    void finalFreshnessTruthTableFailsClosedBeforePublication() throws Exception {
        for (var mask = 0; mask < 8; mask++) {
            var playerCurrent = (mask & 1) != 0;
            var storeCurrent = (mask & 2) != 0;
            var invalidationCurrent = (mask & 4) != 0;
            assertEquals(
                    mask == 7,
                    P4E2OnlineReconciliationCoordinator.allFresh(
                            playerCurrent, storeCurrent, invalidationCurrent),
                    "freshness mask " + mask);
        }

        var coordinator = withoutCommentsAndLiterals(Files.readString(COORDINATOR));
        var finalGate = coordinator.lastIndexOf("if (!allFresh(");
        var freshnessFailure = coordinator.indexOf("FRESHNESS_LOST", finalGate);
        var publication = coordinator.indexOf(
                ".publishPreparedReconciliation(", finalGate);
        assertAll(
                () -> assertTrue(finalGate >= 0, "final freshness gate is absent"),
                () -> assertTrue(freshnessFailure > finalGate,
                        "final freshness drift does not enter the fail-closed branch"),
                () -> assertTrue(publication > freshnessFailure,
                        "publication does not follow the final fail-closed branch"),
                () -> assertEquals(3, occurrences(
                        coordinator,
                        "var storeCurrent = playerCurrent && storeWitness.isCurrent();")),
                () -> assertEquals(4, occurrences(
                        coordinator,
                        "storeCurrent = playerCurrent && storeWitness.isCurrent();")),
                () -> assertEquals(3, occurrences(
                        coordinator,
                        "var indexCurrent = storeCurrent && invalidationCurrent(")),
                () -> assertEquals(1, occurrences(
                        coordinator,
                        "indexCurrent = storeCurrent\n"
                                + "                    && rootAuditService"
                                + ".isReconciliationInvalidationCurrent(")));
    }

    @Test
    void directResultInvalidationAndSetDataCoordinatesAreExhaustiveAndAdjacent()
            throws Exception {
        var coordinator = withoutCommentsAndLiterals(Files.readString(COORDINATOR));
        var player = withoutCommentsAndLiterals(Files.readString(PLAYER_SERVICE));
        var storeService = withoutCommentsAndLiterals(Files.readString(STORE_SERVICE));
        var wrapper = slice(
                coordinator,
                "    @Override\n    public void reconcileAfterRecovery(",
                "\n    P4E2ReconciliationResult reconcile(");
        var invalidation = slice(
                coordinator,
                "    private SkillRetentionRootAuditService.InvalidationResult invalidate(",
                "\n    private boolean invalidationCurrent(");
        var publisher = slice(
                player,
                "    private static void publishReplacement(\n"
                        + "            ServerPlayer player,"
                        + " PlayerSkillAttachmentReady replacement) {",
                "\n    private static MutationRejectionCode mapBuildFailure(");
        var e2Publication = slice(
                player,
                "    public ReconciliationPublication publishPreparedReconciliation(",
                "\n    public void discardPreparedReconciliation(");
        var stop = slice(
                storeService,
                "    private void onServerStopped(",
                "\n    GramaryeSkillSavedData installedAdapter(");

        var resultCall = wrapper.indexOf("var result = reconcile(");
        var directSwitch = wrapper.indexOf("switch (result)", resultCall);
        var finalRecord = wrapper.indexOf(
                "qualificationStoreView.recordReconciliation(", directSwitch);
        var invalidationAttempt = invalidation.indexOf(
                "qualificationStoreView.recordInvalidationAttempt(");
        var actualInvalidation = invalidation.indexOf(
                "rootAuditService.invalidateForReconciliation(", invalidationAttempt);
        var acceptedBranch = invalidation.indexOf(
                "instanceof SkillRetentionRootAuditService.InvalidationResult.Accepted",
                actualInvalidation);
        var invalidationAccepted = invalidation.indexOf(
                "qualificationStoreView.recordInvalidationAccepted(", acceptedBranch);
        var setDataAttempt = publisher.indexOf(
                "qualificationPlayerView.recordE2SetDataAttempt(");
        var actualSetData = publisher.indexOf("player.setData(type, replacement)");
        var setDataSuccess = publisher.indexOf(
                "qualificationPlayerView.recordE2SetDataSuccess(");
        var e2PublishCall = e2Publication.indexOf(
                "publishReplacement(player, replacement, qualificationPlayerView)");
        var applied = e2Publication.indexOf("ReconciliationPublication.APPLIED", e2PublishCall);

        assertAll(
                () -> assertTrue(resultCall >= 0),
                () -> assertTrue(directSwitch > resultCall),
                () -> assertTrue(finalRecord > directSwitch),
                () -> assertEquals(1, occurrences(wrapper,
                        "case P4E2ReconciliationResult.NoChanges")),
                () -> assertEquals(1, occurrences(wrapper,
                        "case P4E2ReconciliationResult.RecoveryChanged")),
                () -> assertEquals(1, occurrences(wrapper,
                        "case P4E2ReconciliationResult.Changed")),
                () -> assertEquals(1, occurrences(wrapper,
                        "case P4E2ReconciliationResult.Deferred")),
                () -> assertEquals(1, occurrences(wrapper,
                        "case P4E2ReconciliationResult.Failed")),
                () -> assertEquals(1, occurrences(wrapper,
                        "case P4E2ReconciliationResult.GenerationExhausted")),
                () -> assertFalse(wrapper.contains("default")),
                () -> assertTrue(invalidationAttempt >= 0),
                () -> assertTrue(actualInvalidation > invalidationAttempt),
                () -> assertTrue(acceptedBranch > actualInvalidation),
                () -> assertTrue(invalidationAccepted > acceptedBranch),
                () -> assertEquals(1, occurrences(coordinator,
                        ".recordInvalidationAttempt(")),
                () -> assertEquals(1, occurrences(coordinator,
                        ".recordInvalidationAccepted(")),
                () -> assertTrue(setDataAttempt >= 0),
                () -> assertTrue(actualSetData > setDataAttempt),
                () -> assertTrue(setDataSuccess > actualSetData),
                () -> assertEquals(1, occurrences(player,
                        ".recordE2SetDataAttempt(")),
                () -> assertEquals(1, occurrences(player,
                        ".recordE2SetDataSuccess(")),
                () -> assertEquals(1, occurrences(player,
                        "player.setData(type, replacement)")),
                () -> assertTrue(publisher.contains(
                        "publishReplacement(player, replacement, null)")),
                () -> assertTrue(e2PublishCall >= 0),
                () -> assertTrue(applied > e2PublishCall),
                () -> assertTrue(stop.indexOf("qualificationStoreView.clearOnServerStopped()")
                        < stop.indexOf("uninstall(event.getServer())")));
    }

    @Test
    void publisherDriftAndAcceptedFailureBothRemainBeforeTheActualSetDataSeam()
            throws Exception {
        var coordinator = withoutCommentsAndLiterals(Files.readString(COORDINATOR));
        var player = withoutCommentsAndLiterals(Files.readString(PLAYER_SERVICE));
        var eligible = slice(
                coordinator,
                "    private P4E2ReconciliationResult reconcileEligible(",
                "\n    private Optional<P4E2ReconciliationResult> ineligibleResult(");
        var publication = slice(
                player,
                "    public ReconciliationPublication publishPreparedReconciliation(",
                "\n    public void discardPreparedReconciliation(");

        var invalidation = eligible.indexOf("var invalidation = invalidate(server, player);");
        var accepted = eligible.indexOf(
                "accepted = (SkillRetentionRootAuditService.InvalidationResult.Accepted)",
                invalidation);
        var finalFreshnessGate = eligible.indexOf("if (!allFresh(", accepted);
        var acceptedFailure = eligible.indexOf("return failed(", finalFreshnessGate);
        var publisherInvocation = eligible.indexOf(
                "var publication = attachmentService.publishPreparedReconciliation(",
                acceptedFailure);
        var publisherResultGate = eligible.indexOf(
                "if (publication != PlayerSkillAttachmentService"
                        + ".ReconciliationPublication.APPLIED)",
                publisherInvocation);
        var publisherDriftFailure = eligible.indexOf("return failed(", publisherResultGate);

        var finalIdentityDrift = publication.indexOf(
                "!onlineReconciliationIdentityCurrent(handle, player)");
        var stateChangedReturn = publication.indexOf(
                "return ReconciliationPublication.STATE_CHANGED", finalIdentityDrift);
        var actualSetDataRoute = publication.indexOf(
                "publishReplacement(player, replacement, qualificationPlayerView)",
                stateChangedReturn);

        assertAll(
                () -> assertTrue(invalidation >= 0,
                        "the accepted-invalidation path is absent"),
                () -> assertTrue(accepted > invalidation,
                        "accepted invalidation is not retained in the exact local"),
                () -> assertTrue(finalFreshnessGate > accepted,
                        "accepted invalidation is not followed by a final freshness gate"),
                () -> assertTrue(acceptedFailure > finalFreshnessGate,
                        "accepted invalidation cannot fail before publication"),
                () -> assertTrue(publisherInvocation > acceptedFailure,
                        "the accepted failure branch is not pre-publication"),
                () -> assertTrue(publisherResultGate > publisherInvocation,
                        "publisher invocation is not observed through its typed result"),
                () -> assertTrue(publisherDriftFailure > publisherResultGate,
                        "publisher STATE_CHANGED does not become the direct failed result"),
                () -> assertTrue(finalIdentityDrift >= 0,
                        "publisher final identity gate is absent"),
                () -> assertTrue(stateChangedReturn > finalIdentityDrift,
                        "publisher drift does not return STATE_CHANGED"),
                () -> assertTrue(actualSetDataRoute > stateChangedReturn,
                        "publisher drift is not ordered before the sole setData route"));
    }

    @Test
    void errorIdentityAndSynchronousExecutionCannotBeInterceptedOrEscaped()
            throws Exception {
        var coordinator = withoutCommentsAndLiterals(Files.readString(COORDINATOR));
        assertAll(
                () -> assertFalse(coordinator.contains("catch (Throwable")),
                () -> assertFalse(coordinator.contains("catch (Error")),
                () -> assertFalse(coordinator.contains("catch (OutOfMemoryError")),
                () -> assertFalse(coordinator.contains("CompletableFuture")),
                () -> assertFalse(coordinator.contains("ExecutorService")),
                () -> assertFalse(coordinator.contains("Executors.")),
                () -> assertFalse(coordinator.contains("parallelStream(")),
                () -> assertFalse(coordinator.contains("new Thread(")));
    }

    @Test
    void e2CallChainCannotMutateStoreJournalSnapshotOrReclaim() throws Exception {
        var e2 = new StringBuilder();
        for (var fileName : P4E2PhaseTypes.NEW_STORE_SOURCE_FILE_NAMES) {
            e2.append(Files.readString(STORE_ROOT.resolve(fileName))).append('\n');
        }
        for (var forbidden : Set.of(
                ".reclaim(",
                ".commit(",
                ".pin(",
                "prepareJournalPrefixClear(",
                "commitPreparedJournalClear(",
                "SkillRetentionRootSnapshot.fromCompleteRoots",
                "NbtIo.",
                "CompoundTag",
                "java.nio.file")) {
            assertFalse(e2.toString().contains(forbidden), forbidden);
        }
    }

    @Test
    void boundedResultVariantsRejectEverySemanticallyFalseTerminalSummary() {
        assertThrows(IllegalArgumentException.class, () ->
                new P4E2ReconciliationResult.NoChanges(summary(1, 0, 0, 0, 0, 0, 1)));
        assertThrows(IllegalArgumentException.class, () ->
                new P4E2ReconciliationResult.NoChanges(summary(0, 1, 0, 0, 0, 0, 1)));
        assertThrows(IllegalArgumentException.class, () ->
                new P4E2ReconciliationResult.RecoveryChanged(
                        summary(0, 0, 0, 0, 0, 0, 1)));
        assertThrows(IllegalArgumentException.class, () ->
                new P4E2ReconciliationResult.Changed(summary(0, 1, 0, 0, 0, 1, 1)));
        assertThrows(IllegalArgumentException.class, () ->
                new P4E2ReconciliationResult.GenerationExhausted(
                        summary(0, 0, 0, 0, 0, 0, 1)));
        assertThrows(IllegalArgumentException.class, () ->
                new P4E2ReconciliationResult.Failed(
                        summary(0, 0, 0, 0, 0, 0, 0),
                        P4E2ReconciliationResult.FailureReason.RECOVERY_RUNTIME_FAILURE,
                        Optional.empty()));

        var changed = new P4E2ReconciliationResult.Changed(
                summary(1, 1, 1, 1, 1, 1, 2));
        assertEquals(1, changed.summary().staleLatestPruned());
        var runtime = new P4E2ReconciliationResult.Failed(
                summary(1, 0, 0, 0, 0, 1, 0),
                P4E2ReconciliationResult.FailureReason.RECOVERY_RUNTIME_FAILURE,
                Optional.of(IllegalStateException.class.getName()));
        assertEquals(Optional.of(IllegalStateException.class.getName()),
                runtime.exceptionClass());
    }

    @Test
    void exactFreshnessAndConsumeFirstGuardsPrecedeTheSolePlayerPublication()
            throws Exception {
        var player = withoutCommentsAndLiterals(Files.readString(PLAYER_SERVICE));
        var recovery = withoutCommentsAndLiterals(Files.readString(RECOVERY_SERVICE));
        var storeService = withoutCommentsAndLiterals(Files.readString(STORE_SERVICE));
        var grouped = withoutCommentsAndLiterals(Files.readString(GROUPED_VALIDATION));
        var identity = slice(
                player,
                "    private boolean onlineReconciliationIdentityCurrent(",
                "\n    private void discardOnlineReconciliationHandleInternal(");
        var prepare = slice(
                player,
                "    public ReconciliationPreparationResult prepareOnlineReconciliation(",
                "\n    public ReconciliationCurrentness checkPreparedReconciliationCurrent(");
        var publish = slice(
                player,
                "    public ReconciliationPublication publishPreparedReconciliation(",
                "\n    public void discardPreparedReconciliation(");
        var continuation = slice(
                recovery,
                "    public static final class RecoveryContinuation",
                "\n    interface Dependencies");
        var storeCurrent = slice(
                storeService,
                "    boolean isP4E2StoreReadyCurrent(",
                "\n    private void requireP4E2Composition(");
        var storeMatches = slice(
                grouped,
                "        boolean matches(",
                "\n        void discard()");

        assertAll(
                () -> assertTrue(identity.contains("handle.serverIdentity.isSameThread()")),
                () -> assertTrue(identity.contains("player == handle.playerIdentity")),
                () -> assertTrue(identity.contains("player.getServer() == handle.serverIdentity")),
                () -> assertTrue(identity.contains("player.getUUID().equals(handle.playerId)")),
                () -> assertTrue(identity.contains(
                        "handle.serverIdentity.getPlayerList() == handle.playerListIdentity")),
                () -> assertTrue(identity.contains(
                        "handle.playerListIdentity.getPlayer(handle.playerId)")),
                () -> assertTrue(identity.contains("player.hasData(type)")),
                () -> assertTrue(identity.contains("player.getData(type) == handle.stateIdentity")),
                () -> assertEquals(1, occurrences(
                        identity, "return onlineReconciliationFactsCurrent(")),
                () -> assertTrue(prepare.indexOf("opaque.consumeAndClear()")
                        < prepare.indexOf("if (ownerIdentity != this)")),
                () -> assertTrue(prepare.indexOf("opaque.lifecycle.claim()")
                        < prepare.indexOf("opaque.consumeAndClear()")),
                () -> assertTrue(prepare.contains(
                        "continuationIdentity != continuationWitnessIdentity")),
                () -> assertTrue(publish.indexOf("prepared.consumeAndClear()")
                        < publish.indexOf("onlineReconciliationIdentityCurrent(")),
                () -> assertTrue(publish.contains(
                        "publishReplacement(player, replacement, qualificationPlayerView)")),
                () -> assertEquals(1, occurrences(player, ".setData(")),
                () -> assertTrue(storeCurrent.contains("rootAuditService == null")),
                () -> assertTrue(storeCurrent.contains(
                        "onlineReconciliationDependency == null")),
                () -> assertTrue(storeCurrent.contains("installedAdapter(server)")),
                () -> assertTrue(storeMatches.contains("owner == candidateOwner")),
                () -> assertTrue(storeMatches.contains("server == candidateServer")),
                () -> assertTrue(storeMatches.contains("adapter == candidateAdapter")),
                () -> assertTrue(storeMatches.contains("adapter.state() == ready")),
                () -> assertTrue(storeMatches.contains("ready.store() == store")),
                () -> assertTrue(storeMatches.contains("audit == candidateAudit")),
                () -> assertTrue(storeMatches.contains(
                        "coordinator == candidateCoordinator")),
                () -> assertTrue(continuation.indexOf("lifecycle.claim()")
                        < continuation.indexOf(".reconcileAfterRecovery(")),
                () -> assertFalse(continuation.contains("recoveryStatus(")),
                () -> assertTrue(continuation.indexOf(
                                "var exactOutcome = Objects.requireNonNull(outcome")
                        < continuation.indexOf(".reconcileAfterRecovery(")));
    }

    private static P4E2ReconciliationResult.Summary summary(
            int recoveryChanged,
            int staleLatestObserved,
            int staleEquippedObserved,
            int staleLatestPruned,
            int staleEquippedPruned,
            int accepted,
            int classifications) {
        return new P4E2ReconciliationResult.Summary(
                recoveryChanged,
                0,
                staleLatestObserved,
                staleLatestPruned,
                staleEquippedObserved,
                staleEquippedPruned,
                classifications,
                0,
                accepted == 0 ? OptionalLong.empty() : OptionalLong.of(accepted));
    }

    private static String slice(String source, String start, String end) {
        var startIndex = source.indexOf(start);
        var endIndex = source.indexOf(end, startIndex);
        if (startIndex < 0 || endIndex <= startIndex) {
            throw new AssertionError("source slice markers are absent");
        }
        return source.substring(startIndex, endIndex);
    }

    private static Set<String> sourcePathsContaining(String token) throws Exception {
        try (var stream = Files.walk(MAIN_JAVA)) {
            return stream.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return withoutCommentsAndLiterals(Files.readString(path))
                                    .contains(token);
                        } catch (java.io.IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    })
                    .map(P4E2LifecycleOrderingTest::relative)
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    private static String relative(Path path) {
        return MAIN_JAVA.relativize(path).toString().replace('\\', '/');
    }

    private static String javaSources(Path root) throws Exception {
        var text = new StringBuilder();
        try (var stream = Files.walk(root)) {
            for (var path : stream.filter(candidate -> candidate.toString().endsWith(".java"))
                    .toList()) {
                text.append(withoutCommentsAndLiterals(Files.readString(path))).append('\n');
            }
        }
        return text.toString();
    }

    private static String withoutCommentsAndLiterals(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ")
                .replaceAll("(?s)\"\"\".*?\"\"\"", "\"\"")
                .replaceAll("(?s)\"(?:\\\\.|[^\"\\\\])*\"", "\"\"")
                .replaceAll("'[^']*'", "''");
    }

    private static int occurrences(String text, String token) {
        var count = 0;
        var offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
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
}
