package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService.MetadataContinuation;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService.MetadataInitialStage;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Excluded same-owner fault interception, not a claim about native LoggedIn fault cleanup. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11NativeMetadataProbe {
    private static final RuntimeException FAULT = new IllegalStateException("P11_METADATA_OWNED_POST_STAGE_FAULT");
    private static Observation observation;
    private static volatile boolean ready;
    private static volatile String report;
    private static volatile RuntimeException failure;
    private static volatile String pendingDiagnostic = "NOT_ENTERED";

    private P11NativeMetadataProbe() {}

    public static boolean begin(SkillSubmissionRecoveryService owner, PlayerEvent.PlayerLoggedInEvent event) {
        String selected = System.getProperty("gramarye.p11.sourceWriter.case", "");
        if (System.getProperty(P11SourceWriterClientHarness.OUTPUT_PROPERTY) == null
                || !List.of("metadata-recovery", "metadata-reconciliation", "metadata-session", "metadata-source-change").contains(selected)
                || observation != null || !(event.getEntity() instanceof ServerPlayer actor)) { return false; }
        require(actor.getServer().isSameThread() && !actor.isFakePlayer()
                        && actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor
                        && actor.connection != null && actor.connection.player == actor
                        && actor.connection.getConnection().isConnected()
                        && actor.connection.getConnection().getPacketListener() == actor.connection,
                "metadata fault requires genuine authenticated exact native B");
        var diagnostic = P11NativeStorageBoundary.diagnostics(actor.getServer(), actor.getUUID());
        require(diagnostic.bodyComplete() && !diagnostic.candidatePresent() && diagnostic.sourceFault().equals("NONE"),
                "metadata fault needs complete qualified source, not partial B");
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.body(actor);
        long enteredRoots = transitionRoots(diagnostic);
        // The outer original CONFIG -> PLAY body still owns its separate control H.
        require(body != null && body.account.metadata == null && enteredRoots == 1,
                "metadata owner entry lacks the sole original control root or already has metadata");
        observation = new Observation(owner, actor, selected, source, body, enteredRoots);
        updateDiagnostic(observation, "ORIGINAL_OWNER_ENTERED");
        return true;
    }

    /** Called after the real production stage recorded its result; never records a receipt itself. */
    public static void completed(MetadataContinuation exact, String stage, long openedEpoch) {
        completed(exact, stage, openedEpoch, 0);
    }

    public static void completed(MetadataContinuation exact, String stage, long openedEpoch, long openedGeneration) {
        var value = observation;
        if (value == null) { return; }
        if (exact == null) {
            if (ready) { return; }
            value.nullReceipts++;
            updateDiagnostic(value, "NULL_RECEIPT_AT_" + stage);
            return;
        }
        if (value.receipt == null) {
            require(stage.equals("recovery"), "first real metadata receipt did not complete recovery");
            requireRetainedMetadata(value, true);
            value.receipt = exact;
        }
        if (value.receipt != exact) { return; }
        switch (stage) {
            case "recovery" -> value.recovery++;
            case "reconciliation" -> value.reconciliation++;
            case "session" -> {
                require(openedEpoch > 0 && openedGeneration > 0, "actual session coordinates are not positive");
                value.session++; value.sessionEpoch = openedEpoch; value.sessionGeneration = openedGeneration;
            }
            default -> throw new IllegalArgumentException("unknown native metadata stage");
        }
        // Retired-receipt replay remains observable after the first phase has finished.
        // Do not reinject the fault or query the retired server for diagnostics here.
        if (ready) { return; }
        updateDiagnostic(value, "COMPLETED_" + stage);
        if (!value.thrown && stage.equals(value.selected.equals("metadata-source-change")
                ? "recovery" : value.selected.substring("metadata-".length()))) {
            value.thrown = true;
            throw FAULT;
        }
    }

    public static void consumed(MetadataContinuation exact) {
        var value = observation;
        if (value != null && value.receipt == exact && exact != null) { value.consume++; }
    }

    /** Read-only observation of the same real receipt entering/leaving the original P7 stage. */
    public static void initialSync(MetadataContinuation exact, P11NativeStorageBoundary.MetadataLease lease,
            long epoch, long generation, MetadataInitialStage stage, boolean returned) {
        var value = observation;
        if (value == null || ready || failure != null || !value.resumed || value.receipt != exact) { return; }
        try {
            require(epoch == value.sessionEpoch && generation == value.sessionGeneration && stage != null,
                    "initial sync changed exact P7 session or server generation");
            if (!returned) {
                var owner = P11NativeStorageBoundary.nativeSourceOwner(value.actor);
                var body = owner == null ? null : owner.body(value.actor);
                require(body != null && body.account.metadata == lease
                                && P11NativeStorageBoundary.metadataSessionCurrent(lease),
                        "actual initial sync did not retain the same current actor/session lease");
                value.initialStages[stage.ordinal()]++;
                if (diagnostic(value).sourceVersion() > value.version
                        && !P11NativeStorageBoundary.metadataCurrent(lease)) {
                    value.versionChangedBeforeInitial = true;
                    require(!P11NativeStorageBoundary.resumeMetadataAfterCauseRemoved(value.actor),
                            "natural play-version change incorrectly refreshed strict resume authority");
                    value.strictResumeRejected = true;
                }
            } else if (stage == MetadataInitialStage.COOLDOWN_SUBMITTED) {
                require(transitionRoots(diagnostic(value)) == 0,
                        "actual final P7 family submission did not release existing metadata H");
                value.releaseAtFinalSubmission = true;
            }
            updateDiagnostic(value, (returned ? "AFTER_" : "BEFORE_") + stage.name());
        } catch (RuntimeException observed) { failure = observed; }
    }

    public static boolean isOwnedFault(RuntimeException candidate) {
        return observation != null && observation.thrown && candidate == FAULT;
    }

    /** The original onPlayerLoggedIn finally has genuinely observed normal=false before this call. */
    public static void afterUnwoundFault(SkillSubmissionRecoveryService owner) {
        var value = observation;
        require(value != null && value.owner == owner && value.thrown && !value.intercepted,
                "metadata fault did not unwind the same original owner");
        value.intercepted = true;
        updateDiagnostic(value, "ORIGINAL_FINALLY_UNWOUND");
        var before = diagnostic(value);
        requireRetainedMetadata(value, false);
        value.failedTransitionRoots = transitionRoots(before);
        value.sameLeaseAfterFault = true;
        value.epoch = before.sourceEpoch(); value.version = before.sourceVersion();
        require(!P11NativeStorageBoundary.resumeMetadataAfterCauseRemoved(null),
                "null actor unexpectedly resumed a retained continuation");
        if (value.selected.equals("metadata-source-change")) {
            new AdvancementRewards(1, List.of(), List.of(), Optional.empty()).grant(value.actor);
            var changed = diagnostic(value);
            require(changed.sourceEpoch() == value.epoch && changed.sourceVersion() > value.version,
                    "actual native reward did not invalidate exact source version");
            require(!P11NativeStorageBoundary.resumeMetadataAfterCauseRemoved(value.actor)
                            && value.recovery == 1 && value.reconciliation == 0 && value.session == 0,
                    "changed-source continuation resumed or repeated completed recovery");
            finish(value, "SOURCE_VERSION_CHANGED_RESUME_FALSE_RETAIN_UNRESOLVED");
            return;
        }
        require(P11NativeStorageBoundary.resumeMetadataAfterCauseRemoved(value.actor),
                "same exact owner refused known missing stages after original finally");
        value.resumed = true;
        updateDiagnostic(value, "SAME_OWNER_RESUME_RETURNED");
        require(value.recovery == 1 && value.reconciliation == 1 && value.session == 1
                        && value.sessionEpoch > 0 && value.sessionGeneration > 0,
                "completed recovery/reconciliation/session was repeated or omitted");
        require(value.consume == (value.selected.equals("metadata-recovery") ? 0 : 1),
                "resume consumed the original one-shot continuation again");
        // The ordinary P7 queued sender runs later; no facade/receipt is manufactured here.
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void tick(ServerTickEvent.Post event) {
        var value = observation;
        if (value == null || ready || failure != null || event.getServer() != value.actor.getServer()) { return; }
        try {
            updateDiagnostic(value, "SERVER_TICK_AFTER_NATIVE_ATTEMPT");
            if (!value.resumed) { return; }
            var current = diagnostic(value);
            if (transitionRoots(current) != 0) { return; }
            require(current.sourceEpoch() == value.epoch && value.recovery == 1
                            && value.reconciliation == 1 && value.session == 1,
                    "completion changed source or repeated an already completed stage");
            require(current.sourceVersion() > value.version && value.versionChangedBeforeInitial
                            && value.strictResumeRejected && value.releaseAtFinalSubmission
                            && java.util.Arrays.equals(value.initialStages, new int[] {1, 1, 0, 1, 1, 0}),
                    "natural stale-v session completion or exact original P7 stage sequence was not proved");
            require(value.actor.connection != null && value.actor.connection.player == value.actor
                            && value.actor.connection.getConnection().isConnected(),
                    "fixture actor left the original authenticated connection");
            require(!P11NativeStorageBoundary.resumeMetadataAfterCauseRemoved(value.actor),
                    "completed/released metadata continuation resumed again");
            finish(value, "KNOWN_MISSING_STAGES_ONLY_THEN_ORIGINAL_P7_INITIAL_SYNC_RELEASE");
        } catch (RuntimeException observed) { failure = observed; }
    }

    private static void finish(Observation value, String outcome) {
        report = "P11-NATIVE-METADATA-PROBE-V1\ncase=" + value.selected
                + "\nentry=GENUINE_QUALIFIED_LOGGED_IN_OWNER\n"
                + "fault=EXACT_POST_STAGE_SENTINEL_AFTER_REAL_STAGE_RECEIPT\n"
                + "originalFinallyObservedFailure=true\n"
                + "entryControlTransitionRoots=" + value.enteredTransitionRoots
                + "\nfailedOwnerAggregateTransitionRoots=" + value.failedTransitionRoots
                + "\nsameMetadataLeaseRetainedAcrossFault=" + value.sameLeaseAfterFault + "\n"
                + "fixtureInterceptsOriginalFault=true\n"
                + "nativeF1DisconnectBehaviorQualified=false\n"
                + "resume=EXCLUDED_SAME_OWNER_AFTER_ORIGINAL_METHOD_UNWOUND\n"
                + "syntheticActor=false\nfabricatedReceipt=false\npublicRepair=false\n"
                + "recoveryCompleted=" + value.recovery + "\nreconciliationCompleted=" + value.reconciliation
                + "\nsessionOpened=" + value.session + "\nsessionEpoch=" + value.sessionEpoch
                + "\nsessionServerGeneration=" + value.sessionGeneration
                + "\noriginalConsumeCalls=" + value.consume + "\noutcome=" + outcome
                + "\nsourceVersionChangedBeforeInitial=" + value.versionChangedBeforeInitial
                + "\nstrictResumeRejectedWhileRetained=" + value.strictResumeRejected
                + "\ninitialStageEnumOrder=" + java.util.Arrays.toString(MetadataInitialStage.values())
                + "\ninitialStageCounts=" + java.util.Arrays.toString(value.initialStages)
                + "\nreleaseAtFinalNativeSubmission=" + value.releaseAtFinalSubmission
                + "\nwrongActor=NULL_REJECTED_NON_NULL_DISTINCT_ACTOR_NOT_RUN\n"
                + "wrongConnection=NOT_RUN_REQUIRES_ACTUAL_DISTINCT_CONNECTION\n"
                + "diagnostic=" + diagnostic(value) + "\nRESULT=PASS\n";
        ready = true;
    }

    static boolean ready() { if (failure != null) { throw failure; } return ready; }
    static String report() { require(ready(), "metadata probe is not complete"); return report; }
    static boolean isNegative() { return observation != null && observation.selected.equals("metadata-source-change"); }
    static String pendingDiagnostic() { return pendingDiagnostic; }

    /** Actual fresh connection/actor, not an isolated counterfactual connection predicate. */
    static String rejectRetiredContinuation(ServerPlayer current) {
        var value = observation;
        require(isNegative() && ready() && value.lease != null && current != value.actor
                        && current.getUUID().equals(value.actor.getUUID())
                        && current.getServer() != value.actor.getServer() && !current.isFakePlayer()
                        && current.getServer().isSameThread()
                        && current.getServer().getPlayerList().getPlayer(current.getUUID()) == current
                        && current.connection != null && current.connection.player == current
                        && current.connection.getConnection() != value.connection
                        && current.connection.getConnection().isConnected() && !value.connection.isConnected(),
                "stale continuation negative lacks a genuine distinct actor and fresh authenticated connection");
        var before = P11NativeStorageBoundary.diagnostics(current.getServer(), current.getUUID());
        int recovery = value.recovery, reconciliation = value.reconciliation;
        int session = value.session, consume = value.consume;
        require(before.bodyComplete() && !before.candidatePresent()
                        && before.sourceFault().equals("NONE"), "fresh B is not independently qualified");
        require(!P11NativeStorageBoundary.metadataCurrent(value.lease)
                        && !P11NativeStorageBoundary.metadataSessionCurrent(value.lease)
                        && !value.receipt.authorizesRetention(current)
                        && !value.receipt.resume(value.lease)
                        && !P11NativeStorageBoundary.resumeMetadataAfterCauseRemoved(value.actor),
                "retired actor/connection continuation replay was admitted");
        var after = P11NativeStorageBoundary.diagnostics(current.getServer(), current.getUUID());
        require(value.recovery == recovery && value.reconciliation == reconciliation
                        && value.session == session && value.consume == consume
                        && after.sourceEpoch() == before.sourceEpoch()
                        && after.sourceVersion() == before.sourceVersion()
                        && after.bodyComplete() && after.sourceFault().equals("NONE"),
                "stale replay changed original stage counts or fresh B source");
        return "P11-NATIVE-METADATA-STALE-RECONNECT-V1\n"
                + "entry=ORIGINAL_STOP_AND_FRESH_SAME_ACCOUNT_CLIENT_LOGIN\n"
                + "differentActor=true\ndifferentConnection=true\ndifferentServerRoot=true\n"
                + "oldConnectionClosed=true\noriginalReceiptAndLeaseRetainedByExcludedObserver=true\n"
                + "syntheticActor=false\nmanualListenerAssignment=false\n"
                + "outcome=COMBINED_STALE_CONTINUATION_REPLAY_REJECTED\n"
                + "isolatedConnectionPredicateRuntimeProof=false\n"
                + "stageCountsUnchanged=true\nfreshSourceUnchanged=true\nRESULT=PASS\n";
    }

    private static void updateDiagnostic(Observation value, String stage) {
        var owner = P11NativeStorageBoundary.nativeSourceOwner(value.actor);
        var body = owner == null ? null : owner.body(value.actor);
        pendingDiagnostic = "stage=" + stage + ";receipt=" + (value.receipt != null)
                + ";nullReceiptObservations=" + value.nullReceipts
                + ";recovery=" + value.recovery + ";reconciliation=" + value.reconciliation
                + ";session=" + value.session + ";thrown=" + value.thrown
                + ";sessionEpoch=" + value.sessionEpoch + ";sessionServerGeneration=" + value.sessionGeneration
                + ";entryControlRoots=" + value.enteredTransitionRoots
                + ";failedAggregateRoots=" + value.failedTransitionRoots
                + ";sameLeaseAfterFault=" + value.sameLeaseAfterFault
                + ";intercepted=" + value.intercepted + ";resumed=" + value.resumed
                + ";preResumeEpoch=" + value.epoch + ";preResumeVersion=" + value.version
                + ";initialStages=" + java.util.Arrays.toString(value.initialStages)
                + ";strictResumeRejected=" + value.strictResumeRejected
                + ";releaseAtFinalSubmission=" + value.releaseAtFinalSubmission
                + ";body=" + (body != null) + ";complete=" + (body != null && body.complete)
                + ";canSerialize=" + (body != null && owner.canSerialize(body))
                + ";nativeEligible=" + P11NativeStorageBoundary.nativeDeliveryEligible(value.actor)
                + ";diagnostic=" + diagnostic(value);
    }

    private static P11QualifiedSourceOwner.Diagnostics diagnostic(Observation value) {
        return P11NativeStorageBoundary.diagnostics(value.actor.getServer(), value.actor.getUUID());
    }
    private static void requireRetainedMetadata(Observation value, boolean firstReceipt) {
        var source = P11NativeStorageBoundary.nativeSourceOwner(value.actor);
        var body = source == null ? null : source.body(value.actor);
        var lease = body == null ? null : body.account.metadata;
        require(source == value.source && body == value.body && lease != null
                        && value.actor.connection != null && value.actor.connection.player == value.actor
                        && value.actor.connection.getConnection() == value.connection
                        && P11NativeStorageBoundary.metadataCurrent(lease)
                        && value.enteredTransitionRoots == 1
                        && transitionRoots(diagnostic(value)) == Math.addExact(value.enteredTransitionRoots, 1),
                "exact metadata lease and independent original control root were not both retained");
        if (firstReceipt) {
            require(value.lease == null, "metadata lease was already captured before its first receipt");
            value.lease = lease;
        } else {
            require(value.lease == lease, "failed finally replaced the original retained metadata lease");
        }
    }
    private static long transitionRoots(P11QualifiedSourceOwner.Diagnostics diagnostic) {
        return diagnostic.nativeResponsibilities().roots().stream().filter(root -> root.kind().equals("TRANSITION"))
                .findFirst().orElseThrow().count();
    }
    private static void require(boolean value, String message) { if (!value) { throw new IllegalStateException(message); } }

    private static final class Observation {
        final SkillSubmissionRecoveryService owner;
        final ServerPlayer actor;
        final String selected;
        final P11QualifiedSourceOwner source;
        final P11QualifiedSourceOwner.Body body;
        final long enteredTransitionRoots;
        MetadataContinuation receipt;
        P11NativeStorageBoundary.MetadataLease lease;
        final net.minecraft.network.Connection connection;
        final int[] initialStages = new int[MetadataInitialStage.values().length];
        int recovery, reconciliation, session, consume, nullReceipts;
        long epoch, version, sessionEpoch, sessionGeneration, failedTransitionRoots;
        boolean thrown, intercepted, resumed;
        boolean versionChangedBeforeInitial, strictResumeRejected, releaseAtFinalSubmission, sameLeaseAfterFault;
        Observation(SkillSubmissionRecoveryService owner, ServerPlayer actor, String selected,
                P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body, long enteredTransitionRoots) {
            this.owner = owner; this.actor = actor; this.selected = selected;
            this.source = source; this.body = body; this.enteredTransitionRoots = enteredTransitionRoots;
            this.connection = actor.connection.getConnection();
        }
    }
}
