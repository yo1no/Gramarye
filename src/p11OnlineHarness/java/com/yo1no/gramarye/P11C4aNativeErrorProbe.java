package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.ReportedException;
import net.minecraft.CrashReport;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Excluded, separately frozen native fault episodes. No production authority is acquired here. */
public final class P11C4aNativeErrorProbe {
    public enum Mode { CALLER_TAIL, FRAME_SEND, RAW_ERROR, PRIMARY_SECONDARY, REPORTED_OOME, COMPLETE_B_FAULT }
    public interface EntryView { Connection error$connection(); Object error$control(); }
    private static volatile Run active;
    private P11C4aNativeErrorProbe() { }

    static void start(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output, Mode mode)
            throws IOException {
        require(active == null && server.isSameThread() && server.isDedicatedServer() && !server.isHardcore()
                && actor != peer && actor.isAlive() && peer.isAlive()
                && actor.connection.getConnection().isEncrypted() && peer.connection.getConnection().isEncrypted()
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                && server.getPlayerList().getPlayer(peer.getUUID()) == peer
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor)
                && P11NativeStorageBoundary.nativeDeliveryEligible(peer), "ERROR_REAL_AUTHENTICATED_PAIR");
        active = new Run(server, actor, peer, output, mode);
        if (mode == Mode.COMPLETE_B_FAULT) { P11C4aCompleteBFaultProbe.start(server, actor, peer, output); }
        P11C4aEvidence.write(output, "native-error-armed.json", report(active, "ARMED_NOT_ACCEPTANCE"));
        P11C4aEvidence.cue(output, "a-native-error.ready");
    }

    /** Ordinary parent tick: waits for the client to arm, then causes exactly one genuine death. */
    static boolean tick() throws IOException {
        var run = active; require(run != null && run.server.isSameThread(), "ERROR_TICK_OWNER");
        require(run.failure == null && ++run.ticks <= 2400, "ERROR_OBSERVER_OR_DEADLINE");
        if (!run.killed && P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-a"), "native-error-armed.json")) {
            require(run.connection.isConnected() && run.peerConnection.isConnected()
                    && run.server.getPlayerList().getPlayer(run.actor.getUUID()) == run.actor,
                    "ERROR_DEATH_OWNER_CURRENT");
            run.killed = true;
            run.actor.hurt(run.actor.damageSources().genericKill(), Float.MAX_VALUE);
            require(run.actor.isDeadOrDying(), "ERROR_ORIGINAL_DEATH_NOT_REACHED");
        }
        // The actual ServerPacketListener policy logs and returns for these ordinary Exceptions.
        if (!run.policySealed || !suppressed(run.mode) && !run.outerObserved) { return false; }
        if (!P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-a"), "native-error-client.json")) { return false; }
        if (!run.finished) {
            run.finished = true;
            P11C4aEvidence.write(run.output, "native-error.json", report(run, "ACTUAL_NATIVE_FAULT_POLICY_AND_CORRELATION"));
        }
        return true; // Parent may now perform its existing normal ordered cleanup.
    }

    public static Object executing(Object rawEntry, Object rawDrain) {
        var run = active;
        if (run == null || !(rawEntry instanceof EntryView entry) || entry.error$connection() != run.connection) { return null; }
        observe(run, () -> {
            require(run.killed && run.server.isSameThread() && run.executions++ == 0
                    && run.connection.getPacketListener() == run.caller, "ERROR_EXACT_ORIGINAL_EXECUTION");
            run.control = (P11TransitionControl) entry.error$control();
            run.request = ((P11TransitionControl.Drain) rawDrain).request();
            require(run.request.command() == Command.TRY && run.request.scope() == Scope.PLAY
                    && run.request.kind() == Kind.DEATH && run.request.requestSeq() > 0, "ERROR_REAL_TRY_REQUIRED");
            run.executing = true;
        });
        return run;
    }

    /** After the original query, after B assignment; never a replacement respawn body. */
    public static void afterHardcoreQuery(ServerGamePacketListenerImpl caller) {
        var run = active;
        if (run == null || run.mode == Mode.FRAME_SEND || caller != run.caller || !run.executing) { return; }
        if (run.mode == Mode.COMPLETE_B_FAULT) {
            observe(run, "COMPLETE_B_UNEXPECTED_CALLER_TAIL", () -> require(false, "ERROR_EVENT_FAULT_DID_NOT_UNWIND"));
            return;
        }
        var source = P11NativeStorageBoundary.nativeSourceOwner(caller.player);
        var body = source == null ? null : source.body(caller.player);
        require(run.failure == null && run.injections == 0 && run.frameSendReturns == 1
                && caller.player != run.actor && caller.player.connection == caller
                && run.server.getPlayerList().getPlayer(run.actor.getUUID()) == caller.player
                && body != null && source.canCopy(body)
                && P11NativeStorageBoundary.nativeDeliveryEligible(caller.player), "ERROR_TAIL_NOT_COMPLETE_NATIVE_B");
        run.injections++;
        run.partialActorReplaced = true;
        run.sourceAtTail = P11C4aEvidence.sourceObservation(source.diagnostics(caller.player.getUUID()));
        if (run.mode == Mode.REPORTED_OOME) { throw run.reportedOome; }
        if (!suppressed(run.mode)) { throw run.rawFault; }
        throw run.fault;
    }

    /** Exact real PlayerRespawnEvent B, before the original caller can assign its player field. */
    static void completeBFault(ServerPlayer next) {
        var run = active;
        var source = next == null ? null : P11NativeStorageBoundary.nativeSourceOwner(next);
        var body = source == null ? null : source.body(next);
        require(run != null && run.mode == Mode.COMPLETE_B_FAULT && run.executing && run.server.isSameThread()
                && run.failure == null && run.injections == 0 && run.frameSendReturns == 1
                && next == P11C4aCompleteBFaultProbe.exactBody() && next != run.actor
                && run.caller.player == run.actor && next.connection == run.caller
                && run.server.getPlayerList().getPlayer(run.actor.getUUID()) == next
                && body != null && body.complete && source.canCopy(body), "ERROR_COMPLETE_B_EVENT_OWNER");
        run.injections++;
        run.sourceAtTail = P11C4aEvidence.sourceObservation(source.diagnostics(next.getUUID()));
        throw run.fault;
    }

    /** Called at the unique Connection.send invocation inside Common.send's original try. */
    public static boolean beforeConnectionSend(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var run = active;
        if (run == null || !run.executing || listener != run.caller || !correlatedFrame(run, packet)) { return false; }
        require(run.failure == null && run.frameSendEntries++ == 0, "ERROR_FRAME_SEND_NOT_ONCE");
        run.frameState = run.control.state().orElseThrow();
        require(run.frameState.outcome() == Outcome.NATIVE_FRAME && same(run.request, run.frameState), "ERROR_FRAME_NOT_REAL_CORRELATION");
        if (run.mode == Mode.FRAME_SEND) {
            require(run.injections++ == 0, "ERROR_SEND_FAULT_NOT_ONCE");
            throw run.fault;
        }
        return true;
    }

    public static void afterConnectionSend(boolean selected) {
        var run = active;
        if (run != null && selected) { observe(run, () -> run.frameSendReturns++); }
    }

    private static boolean correlatedFrame(Run run, Packet<?> packet) {
        if (!(packet instanceof ClientboundBundlePacket bundle)) { return false; }
        var iterator = bundle.subPackets().iterator();
        if (!iterator.hasNext() || !(iterator.next() instanceof ClientboundCustomPayloadPacket custom)
                || !(custom.payload() instanceof P11TransitionStatePayload marker)
                || !same(run.request, marker.state()) || marker.state().outcome() != Outcome.NATIVE_FRAME) { return false; }
        return iterator.hasNext() && iterator.next() instanceof ClientboundRespawnPacket && !iterator.hasNext();
    }

    public static void policyEntered(PacketListener listener, Packet<?> packet, Exception failure) {
        var run = active; if (run == null || !run.executing) { return; }
        run.policyInputCategory = category(failure);
        run.policyInputSameFault = failure == run.fault;
        run.policyInputReportedCauseSameFault = failure instanceof ReportedException && failure.getCause() == run.fault;
        observe(run, "POLICY_ENTRY", () -> {
            require(suppressed(run.mode) && run.injections == 1 && run.policyEntries++ == 0 && listener == run.caller
                    && packet instanceof ServerboundClientCommandPacket command
                    && command.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN,
                    "ERROR_WRONG_ORIGINAL_POLICY_RECEIVER");
            require(run.mode != Mode.FRAME_SEND ? failure == run.fault
                    : failure instanceof ReportedException && failure.getCause() == run.fault,
                    "ERROR_NATIVE_SEND_CATCH_OR_PRIMARY_IDENTITY");
            run.policyInput = failure;
        });
    }

    /** The actual inherited ServerPacketListener override normally logs and RETURNS. */
    public static void policyReturned() {
        var run = active; if (run == null || !run.executing) { return; }
        observe(run, "POLICY_RETURN", () -> {
            require(suppressed(run.mode) && run.policyEntries == 1 && run.policyReturns++ == 0
                    && run.policyInput != null, "ERROR_ORIGINAL_SERVER_POLICY_RETURN");
        });
    }

    public static void policyEscaped(Throwable escaping) {
        var run = active; if (run == null || !run.executing) { return; }
        run.policyEscapingCategory = category(escaping);
        run.policyEscapingSameInput = escaping == run.policyInput;
        observe(run, "POLICY_THROW", () -> {
            run.policyThrows++;
            require(false, "ERROR_SERVER_POLICY_UNEXPECTED_THROW");
        });
    }

    /** Original adapter result, not an observer-supplied false or a substitute receiver. */
    public static void packetBodyEnded(boolean normal, boolean result, Throwable escaping) {
        var run = active; if (run == null || !run.executing) { return; }
        run.bodyEscapingCategory = category(escaping);
        observe(run, "BODY_RETURN", () -> {
            if (suppressed(run.mode)) {
                require(normal && !result && escaping == null && run.policyReturns == 1
                        && run.bodyReturns++ == 0, "ERROR_NATIVE_BODY_FALSE_NOT_OBSERVED");
            } else {
                require(!normal && escaping == expectedPrimary(run) && run.policyEntries == 0
                        && run.bodyThrows++ == 0, "ERROR_RAW_BODY_IDENTITY");
            }
        });
    }

    public static void specialEntered(Exception failure, Packet<?> packet, PacketListener listener) {
        var run = active; if (run == null || !run.executing) { return; }
        observe(run, "SPECIAL_ENTRY", () -> require(run.mode == Mode.REPORTED_OOME
                && run.specialEntries++ == 0 && run.injections == 1 && failure == run.reportedOome
                && failure.getCause() == run.ownedOome && listener == run.caller
                && packet instanceof ServerboundClientCommandPacket command
                && command.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN,
                "ERROR_SPECIAL_NOT_EXACT_ORIGINAL_INPUT"));
    }

    public static void specialReturned(ReportedException result) {
        var run = active; if (run == null || !run.executing) { return; }
        observe(run, "SPECIAL_RETURN", () -> require(run.specialEntries == 1 && run.specialReturns++ == 0
                && result == run.reportedOome && result.getCause() == run.ownedOome,
                "ERROR_SPECIAL_REPLACED_PRIMARY"));
    }

    /** Existing real finish call only. The private ticket is compared and never retained. */
    public static Object finishing(Object service, Object ticket, boolean normal, Throwable primary) {
        var run = active;
        if (run == null || run.mode != Mode.PRIMARY_SECONDARY || !run.executing) { return null; }
        observe(run, () -> {
            require(service instanceof P11LiveTransitionService && !normal && primary == run.rawFault && run.policyEntries == 0 && run.bodyThrows == 1
                    && ticket == P11LiveTransitionBoundary.currentNativeAttempt(run.caller)
                    && run.finishEntered++ == 0, "ERROR_FINISH_NOT_EXACT_PRIMARY");
            var source = P11NativeStorageBoundary.nativeSourceOwner(run.caller.player);
            var body = source == null ? null : source.body(run.caller.player);
            require(body != null && source.canCopy(body), "ERROR_FINISH_SOURCE_NOT_CURRENT");
            run.transitionBeforeClose = body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()];
            require(run.transitionBeforeClose == 1, "ERROR_FINISH_MISSING_ACTUAL_CUSTODY");
            run.terminalFailuresBefore = ((P11LiveTransitionService) service).terminalFailureCount();
            run.finishing = true;
        });
        return run;
    }

    /** Unique runtime branch after the real original release; three pinned finally bytecode copies. */
    public static void custodyClosed(Object owner, Object custody) {
        var run = active;
        if (run == null || run.mode != Mode.PRIMARY_SECONDARY || !run.executing || !run.finishing) { return; }
        var source = P11NativeStorageBoundary.nativeSourceOwner(run.caller.player);
        var body = source == null ? null : source.body(run.caller.player);
        require(run.failure == null && owner == source && custody != null && body != null && source.canCopy(body)
                && run.secondaryInjections == 0 && run.bodyThrows == 1 && run.policyEntries == 0, "ERROR_SECONDARY_WRONG_ORIGINAL_RELEASE");
        run.transitionAfterClose = body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()];
        require(run.transitionAfterClose == 0, "ERROR_ORIGINAL_CUSTODY_RELEASE_NOT_OBSERVED");
        run.secondaryInjections++;
        throw run.secondary;
    }

    public static void finished(Object token, Object service, Throwable escaping) {
        if (!(token instanceof Run run)) { return; }
        try {
            observe(run, () -> {
                require(escaping == null && run.finishing && run.finishReturned++ == 0
                        && run.secondaryInjections == 1 && service instanceof P11LiveTransitionService,
                        "ERROR_FINISH_REPLACED_PRIMARY_WITH_SECONDARY");
                run.terminalFailuresAfter = ((P11LiveTransitionService) service).terminalFailureCount();
                require(run.terminalFailuresBefore != Long.MAX_VALUE
                        && run.terminalFailuresAfter == run.terminalFailuresBefore + 1,
                        "ERROR_SECONDARY_COUNTER_NOT_EXACT");
            });
        } finally { run.finishing = false; }
    }

    static int expectedFrames(Mode mode) { return mode == Mode.FRAME_SEND ? 0 : 1; }

    static boolean suppressed(Mode mode) { return mode == Mode.CALLER_TAIL || mode == Mode.FRAME_SEND || mode == Mode.COMPLETE_B_FAULT; }

    static Throwable expectedPrimary(Run run) { return run.mode == Mode.REPORTED_OOME ? run.reportedOome : run.rawFault; }

    static boolean primaryMatches(Mode mode, Throwable raw, Throwable escaping, int policyEntries,
            int policyReturns, int policyThrows, int bodyReturns, int bodyThrows) {
        return suppressed(mode)
                ? escaping == null && policyEntries == 1 && policyReturns == 1 && policyThrows == 0
                    && bodyReturns == 1 && bodyThrows == 0
                : escaping == raw && (mode == Mode.REPORTED_OOME
                    ? raw instanceof ReportedException && raw.getCause() instanceof OutOfMemoryError
                    : raw instanceof Error) && policyEntries == 0 && policyReturns == 0
                    && policyThrows == 0 && bodyReturns == 0 && bodyThrows == 1;
    }

    public static void executed(Object token, Object service, Throwable escaping) {
        if (!(token instanceof Run run)) { return; }
        try {
            run.executeEscapingCategory = category(escaping);
            run.executeSameRaw = escaping == run.rawFault;
            if (escaping == null) { run.executeReturns++; } else { run.executeThrows++; }
            observe(run, "EXECUTE_RETURN", () -> {
                run.finalState = run.control.state().orElseThrow();
                require(run.injections == 1 && primaryMatches(run.mode, expectedPrimary(run), escaping, run.policyEntries,
                                run.policyReturns, run.policyThrows, run.bodyReturns, run.bodyThrows)
                        && (suppressed(run.mode) ? run.executeReturns == 1 && run.executeThrows == 0
                            : run.executeThrows == 1 && run.executeReturns == 0) && run.executions == 1 && run.frameSendEntries == 1
                        && run.frameSendReturns == expectedFrames(run.mode)
                        && run.finalState.outcome() == (run.mode == Mode.PRIMARY_SECONDARY ? Outcome.UNKNOWN : Outcome.FAULT)
                        && run.finalState.availability() == Availability.DISABLED
                        && same(run.request, run.finalState) && run.finalState.statusVersion() > run.frameState.statusVersion(),
                        "ERROR_PRIMARY_REPLACED_OR_FALSE_COMPLETION");
                if (run.mode == Mode.REPORTED_OOME) {
                    require(run.specialEntries == 1 && run.specialReturns == 1
                            && escaping == run.reportedOome && escaping.getCause() == run.ownedOome,
                            "ERROR_REPORTED_OOME_SPECIAL_ROUTE_MISSING");
                }
                if (!suppressed(run.mode)) { run.policyOutput = escaping; }
                if (run.mode == Mode.PRIMARY_SECONDARY) {
                    require(run.finishEntered == 1 && run.finishReturned == 1 && run.secondaryInjections == 1
                            && run.transitionBeforeClose == 1 && run.transitionAfterClose == 0
                            && run.terminalFailuresAfter == run.terminalFailuresBefore + 1,
                            "ERROR_SECONDARY_NOT_CONTAINED_OR_CUSTODY_NOT_RELEASED");
                }
                if (run.mode != Mode.FRAME_SEND) {
                    var exact = run.mode == Mode.COMPLETE_B_FAULT ? P11C4aCompleteBFaultProbe.exactBody() : run.caller.player;
                    var source = P11NativeStorageBoundary.nativeSourceOwner(exact);
                    var body = source == null ? null : source.body(exact);
                    require(body != null && source.canCopy(body)
                            && body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] == 0,
                            "ERROR_FINAL_SOURCE_OR_CUSTODY_CHANGED");
                    run.sourceAfterFinally = P11C4aEvidence.sourceObservation(source.diagnostics(exact.getUUID()));
                }
                if (run.mode == Mode.COMPLETE_B_FAULT) {
                    try { P11C4aCompleteBFaultProbe.policyReturned(); }
                    catch (IOException secondary) { throw new IllegalStateException("ERROR_COMPLETE_B_READBACK_IO", secondary); }
                }
                run.policySealed = true;
                if (P11C4aErrorStatusProbe.selected(run.mode)) {
                    require(service instanceof P11LiveTransitionService
                            && P11C4aErrorStatusProbe.run(run.server, run.caller.player, run.output,
                                    run.mode, run.control, run.request, run.finalState,
                                    (P11LiveTransitionService) service, escaping), "ERROR_STATUS_NEGATIVES_FAILED");
                }
            });
            write(run, "native-error-caller.json", suppressed(run.mode)
                    ? "ORIGINAL_SERVER_POLICY_AND_ADAPTER_RETURNED_AFTER_PRODUCTION_FINALLY"
                    : run.mode == Mode.REPORTED_OOME
                        ? "ORIGINAL_REPORTED_OOME_CALLER_ESCAPED_AFTER_PRODUCTION_FINALLY"
                        : "ORIGINAL_RAW_CALLER_ESCAPED_AFTER_PRODUCTION_FINALLY");
        } finally { run.executing = false; }
    }

    /** Observation before the original native task/server logger, never a new catch policy. */
    public static void outer(Throwable failure, boolean taskCatch) {
        var run = active;
        if (run != null && run.mode == Mode.COMPLETE_B_FAULT) {
            P11C4aCompleteBFaultProbe.laterOuter(failure, taskCatch);
            return; // A separate later native guard primary is not the sealed original event fault.
        }
        if (run == null || suppressed(run.mode) || failure != run.policyOutput || run.outerObserved) { return; }
        observe(run, () -> { require(run.policySealed && (run.mode == Mode.REPORTED_OOME || !taskCatch),
                "ERROR_OUTER_BEFORE_FINALLY_OR_RAW_ROUTED_TO_EXCEPTION"); run.outerObserved = true; run.taskCatch = taskCatch; });
        write(run, "native-error-outer.json", "ACTUAL_ORIGINAL_TASK_OR_SERVER_EXCEPTION_RECEIVER");
    }

    static void stopped(MinecraftServer server) {
        var run = active; if (run == null || run.server != server) { return; }
        write(run, "native-error-stopped.json", "ORIGINAL_SERVER_STOPPED_NOT_FULL_ACCEPTANCE");
        active = null; // Release the bounded engineering actor/Throwable graph only after real stop.
    }
    static void releaseAfterNormalCleanup() { active = null; }

    private static boolean same(Request request, State state) {
        return request != null && state.connectionEpoch() == request.connectionEpoch()
                && state.sceneSerial() == request.sceneSerial() && state.actorGeneration() == request.actorGeneration()
                && state.requestSeq() == request.requestSeq() && state.kind() == request.kind() && state.scope() == request.scope();
    }
    private static void observe(Run run, Runnable observation) { observe(run, "OTHER_NATIVE_OBSERVATION", observation); }
    private static void observe(Run run, String stage, Runnable observation) {
        try { observation.run(); }
        catch (RuntimeException | Error secondary) {
            if (run.failure == null) { run.failure = "ERROR_OBSERVER_FAILED"; run.failureStage = stage; }
        }
    }
    private static String category(Throwable value) {
        return value == null ? "NONE" : value instanceof ReportedException ? "REPORTED_EXCEPTION"
                : value instanceof Error ? "ERROR" : value instanceof RuntimeException ? "RUNTIME_EXCEPTION" : "OTHER_THROWABLE";
    }
    private static void write(Run run, String leaf, String status) {
        try { P11C4aEvidence.write(run.output, leaf, report(run, status)); }
        catch (IOException | RuntimeException | Error secondary) { run.failure = "ERROR_EVIDENCE_FAILED"; }
    }
    private static java.util.Map<String,Object> report(Run run, String status) {
        var map = new LinkedHashMap<String,Object>();
        map.put("status", status); map.put("mode", run.mode.name()); map.put("failureCode", run.failure == null ? "NONE" : run.failure);
        map.put("executions", run.executions); map.put("faultInjections", run.injections);
        map.put("actualFrameConnectionSendEntries", run.frameSendEntries); map.put("actualFrameConnectionSendReturns", run.frameSendReturns);
        map.put("originalPolicyCalls", run.policyEntries); map.put("originalPolicyOutputObserved", run.policyOutput != null);
        map.put("originalServerPolicyReturns", run.policyReturns); map.put("originalServerPolicyThrows", run.policyThrows);
        map.put("originalPacketBodyFalseReturns", run.bodyReturns);
        map.put("originalPacketBodyRawThrows", run.mode == Mode.REPORTED_OOME ? 0 : run.bodyThrows);
        map.put("originalPacketBodyReportedOomeThrows", run.mode == Mode.REPORTED_OOME ? run.bodyThrows : 0);
        map.put("originalExecuteNormalReturns", run.executeReturns); map.put("originalExecuteThrows", run.executeThrows);
        map.put("policyInputCategory", run.policyInputCategory); map.put("policyInputSameFault", run.policyInputSameFault);
        map.put("policyInputReportedCauseSameFault", run.policyInputReportedCauseSameFault);
        map.put("policyEscapingCategory", run.policyEscapingCategory); map.put("policyEscapingSameInput", run.policyEscapingSameInput);
        map.put("bodyEscapingCategory", run.bodyEscapingCategory); map.put("executeEscapingCategory", run.executeEscapingCategory);
        map.put("executeEscapingSameRaw", run.executeSameRaw); map.put("firstObserverFailureStage", run.failureStage);
        map.put("ordinaryExceptionSuppressedByOriginalServerOverride", suppressed(run.mode) && run.policySealed);
        map.put("primarySecondaryUsesRawErrorNotOrdinaryException", run.mode == Mode.PRIMARY_SECONDARY);
        map.put("sameNativeListenerReusedByRespawn", true); map.put("newActorAssignmentBeforeTailFault", run.partialActorReplaced);
        map.put("actualRequest", run.request); map.put("actualFrameState", run.frameState); map.put("actualFinalState", run.finalState);
        map.put("originalOuterReceiver", suppressed(run.mode) ? "NOT_APPLICABLE_ORIGINAL_SERVER_POLICY_RETURNED"
                : !run.outerObserved ? "NOT_YET_OBSERVED" : run.taskCatch ? "NATIVE_TASK_CATCH" : "NATIVE_RUN_SERVER_CATCH");
        map.put("policySealed", run.policySealed); map.put("peerSameConnectionAliveAtObservation", run.peerConnection.isConnected()
                && run.server.getPlayerList().getPlayer(run.peer.getUUID()) == run.peer && run.peer.isAlive());
        map.put("distinctListenerErrorOwnershipClaimed", false);
        map.put("rawErrorObserved", run.mode != Mode.REPORTED_OOME && !suppressed(run.mode) && run.policySealed && run.outerObserved && !run.taskCatch);
        map.put("ownedReportedOomeSpecialRouteObserved", run.mode == Mode.REPORTED_OOME && run.policySealed);
        map.put("originalMakeReportedExceptionEntries", run.specialEntries);
        map.put("originalMakeReportedExceptionReturns", run.specialReturns);
        map.put("actualHeapExhaustionClaimed", false);
        map.put("secondaryInjections", run.secondaryInjections); map.put("finishEntered", run.finishEntered);
        map.put("finishReturned", run.finishReturned); map.put("transitionBeforeClose", run.transitionBeforeClose);
        map.put("transitionAfterClose", run.transitionAfterClose); map.put("terminalFailuresBefore", run.terminalFailuresBefore);
        map.put("terminalFailuresAfter", run.terminalFailuresAfter);
        map.put("sourceAtActualTail", run.sourceAtTail); map.put("sourceAfterOriginalFinally", run.sourceAfterFinally);
        map.put("faultCheckpoint", run.mode == Mode.COMPLETE_B_FAULT ? "ACTUAL_FULL_B_RESPAWN_EVENT_BEFORE_CALLER_ASSIGNMENT" : "EXISTING_MODE_CHECKPOINT");
        map.put("sourceDurableOrStopSuccessClaimed", false);
        map.put("configCatchOrQueueSubmissionFailureClaimed", false); map.put("fullC4aAcceptance", false);
        return map;
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class InjectedFault extends RuntimeException { InjectedFault() { super("C4A_OWNED_NATIVE_ERROR_FIXTURE"); } }
    private static final class InjectedRawError extends Error { InjectedRawError() { super("C4A_OWNED_RAW_ERROR_FIXTURE"); } }
    private static final class InjectedSecondary extends Error { InjectedSecondary() { super("C4A_OWNED_SECONDARY_ERROR_FIXTURE"); } }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final ServerGamePacketListenerImpl caller;
        final Connection connection, peerConnection; final Path output; final Mode mode; final InjectedFault fault = new InjectedFault();
        final InjectedRawError rawFault = new InjectedRawError(); final InjectedSecondary secondary = new InjectedSecondary();
        final OutOfMemoryError ownedOome; final ReportedException reportedOome;
        int specialEntries, specialReturns;
        P11TransitionControl control; Request request; State frameState, finalState; Throwable policyInput, policyOutput;
        java.util.Map<String,Object> sourceAtTail, sourceAfterFinally;
        int finishEntered, finishReturned, secondaryInjections;
        long transitionBeforeClose = -1, transitionAfterClose = -1, terminalFailuresBefore = -1, terminalFailuresAfter = -1;
        boolean finishing;
        int policyReturns, policyThrows, bodyReturns, bodyThrows, executeReturns, executeThrows;
        String failureStage = "NONE", policyInputCategory = "NONE", policyEscapingCategory = "NONE",
                bodyEscapingCategory = "NONE", executeEscapingCategory = "NONE";
        boolean policyInputSameFault, policyInputReportedCauseSameFault, policyEscapingSameInput, executeSameRaw;
        volatile String failure; int ticks, executions, injections, frameSendEntries, frameSendReturns, policyEntries;
        boolean killed, executing, partialActorReplaced, policySealed, outerObserved, taskCatch, finished;
        Run(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output, Mode mode) {
            this.server=server;this.actor=actor;this.peer=peer;this.output=output;this.mode=mode;
            ownedOome = mode == Mode.REPORTED_OOME ? new OutOfMemoryError("C4A_OWNED_OOME_NO_HEAP_PRESSURE") : null;
            reportedOome = ownedOome == null ? null : new ReportedException(CrashReport.forThrowable(ownedOome, "C4A_OWNED_SPECIAL_ROUTE"));
            caller=actor.connection; connection=caller.getConnection(); peerConnection=peer.connection.getConnection();
        }
    }
}
