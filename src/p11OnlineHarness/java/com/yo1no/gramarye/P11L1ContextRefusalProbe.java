package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Actual W-created function scope; the only held future belongs to this reload listener. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11L1ContextRefusalProbe {
    private static final long TASK_HOLD_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static volatile Run active;
    private P11L1ContextRefusalProbe() { }
    static boolean selected() { return "l1-work-context-refusal".equals(P11C4aEvidence.property("case")); }

    static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output) {
        require(selected() && active == null && server.isSameThread() && actor != peer
                && !actor.getUUID().equals(peer.getUUID()), "ARM");
        var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = owner == null ? null : owner.nativeRecipient(actor);
        var peerBody = owner == null ? null : owner.nativeRecipient(peer);
        require(body != null && peerBody != null && body.account != peerBody.account
                && owner.canCopy(body) && owner.canCopy(peerBody), "EXISTING_TWO_QUALIFIED_SOURCES");
        active = new Run(server, actor, peer, output, owner, body, peerBody);
    }

    /** Original hasJoined branch observation, not a profile or connection minted by this probe. */
    static void authenticated(MinecraftServer server, Connection connection, UUID id) {
        var r = active;
        if (r == null || r.server != server || !r.actor.getUUID().equals(id)) { return; }
        synchronized (r) {
            if (!r.departureReady || r.reloadActive || r.naturalHurtSeen || connection == r.oldConnection || r.replacement != null
                    || !connection.isConnected() || !connection.isEncrypted() || connection.isMemoryConnection()) {
                r.failure = "AUTHENTICATED_REPLACEMENT_ORDER"; return;
            }
            r.replacement = connection;
        }
    }

    /** Main-thread observation after the whole original logout, before reconnect is cued. */
    static void normalLogoutReturned() {
        var r = active;
        require(r != null && r.server.isSameThread() && !r.departureReady && !r.reloadActive
                && !r.oldConnection.isConnected() && r.actor.isRemoved()
                && r.server.getPlayerList().getPlayer(r.actor.getUUID()) == null
                && r.body.account.current == r.body && r.owner.canCopy(r.body)
                && roots(r.body, P11ControlBudgets.Root.WORK) > 0, "RECONNECT_AFTER_TRUE_LOGOUT");
        r.departureReady = true;
    }

    /** Only the actual native startNextTask call to this existing required task can be retained. */
    public static boolean captureTask(ServerConfigurationPacketListenerImpl listener, ConfigurationTask task,
            Consumer<Packet<?>> sender, Operation<Void> original) {
        var r = active;
        if (r == null || task.getClass() != P11ConfigurationTask.class
                || listener.getConnection() != r.replacement) { return false; }
        synchronized (r) {
            r.taskCaptureAttempted = true; r.taskCaptureOnMain = r.server.isSameThread();
            require(active == r && r.taskCaptureOnMain && r.departureReady && !r.reloadActive && !r.naturalHurtSeen && r.entries == 0
                    && r.heldTask == null && r.taskCaptures == 0 && !r.oldConnection.isConnected()
                    && listener.getMainThreadEventLoop() == r.server && r.replacement.isConnected()
                    && r.replacement.getPacketListener() == listener
                    && P11ConfigurationTask.TYPE.equals(((P11ConfigurationBoundary.Access) listener).p11$currentConfigurationTask()),
                    "EXACT_NATIVE_REQUIRED_TASK_CAPTURE");
            var held = new HeldTask(listener, task, sender, original, System.nanoTime());
            r.heldTask = held; r.taskCaptures = 1;
        }
        return true;
    }

    /** The already-existing P6→original hurt callsite; late task arrival fails, never delays the hit. */
    static void beforeOriginalHurt() {
        var r = active; if (r == null) { return; }
        require(r.server.isSameThread(), "NATURAL_HURT_OWNER");
        synchronized (r) {
            require(!r.naturalHurtSeen && r.heldTask != null && r.taskCaptures == 1 && r.taskCalls == 0
                    && holdWithinBound(r.heldTask.capturedAt, System.nanoTime()), "TASK_MUST_ARRIVE_BEFORE_NATURAL_HIT");
            r.naturalHurtSeen = true;
        }
    }

    /** Ordinary Post observes the fixture bound; this does not poll/tick networking or move the hit. */
    static void checkTaskHold() {
        var r = active; if (r == null) { return; }
        require(r.server.isSameThread(), "TASK_HOLD_OWNER");
        synchronized (r) {
            if (r.heldTask != null) {
                require(holdWithinBound(r.heldTask.capturedAt, System.nanoTime()), "NATIVE_TASK_HOLD_DEADLINE");
            }
        }
    }

    private static boolean holdWithinBound(long capturedAt, long now) {
        long elapsed = now - capturedAt;
        return elapsed >= 0 && elapsed <= TASK_HOLD_NANOS;
    }

    private static void releaseTask(Run r) {
        HeldTask held;
        synchronized (r) {
            held = r.heldTask;
            require(active == r && r.server.isSameThread() && r.reloadActive && r.waiting && r.inManagedBlock
                    && held != null && r.taskCaptures == 1 && r.taskCalls == 0
                    && holdWithinBound(held.capturedAt, System.nanoTime())
                    && held.listener.getConnection() == r.replacement && r.replacement.isConnected()
                    && r.replacement.getPacketListener() == held.listener
                    && P11ConfigurationTask.TYPE.equals(((P11ConfigurationBoundary.Access) held.listener).p11$currentConfigurationTask()),
                    "ORIGINAL_TASK_RELEASE_IN_REAL_WORK_CONTEXT");
            r.heldTask = null; r.taskCalls = 1;
        }
        // No monitor across original task/control/network callbacks; the captured original is invoked once.
        held.original.call(held.task, held.sender);
        r.taskReturns++;
    }

    /** Called after the existing observer and the unique original peer executable, once. */
    static void afterCommand(MinecraftServer server, ServerPlayer actor, ServerPlayer peer,
            ServerSlot.InstanceState instance, ExecutionContext<?> context, String command) {
        var r = active;
        if (r == null || !"experience add @s 5 points".equals(command)) { return; }
        require(server == r.server && actor == r.actor && peer == r.peer && server.isSameThread()
                && r.entries++ == 0 && instance.work != null && !instance.lease.pin.isClosed()
                && instance.logoutState == SkillRuntimeService.LogoutState.COMPLETE
                && actor.isRemoved() && !r.oldConnection.isConnected()
                && server.getPlayerList().getPlayer(actor.getUUID()) == null, "REAL_OFFLINE_WORK_FUNCTION");
        r.instance = instance; r.context = context;
        synchronized (r) {
            require(r.naturalHurtSeen && r.heldTask != null && r.taskCaptures == 1 && r.taskCalls == 0
                    && holdWithinBound(r.heldTask.capturedAt, System.nanoTime()), "CAPTURE_REMAINS_OWNED_AT_COMMAND");
        }
        var observed = P11NativeOperationBoundary.observe(context);
        require(!observed.terminal() && observed.retainedBindings() == 2
                && roots(r.body, P11ControlBudgets.Root.WORK) > 0
                && roots(r.body, P11ControlBudgets.Root.OPERATION) > 0
                && roots(r.body, P11ControlBudgets.Root.COMMAND_CONTEXT) == 1
                && roots(r.peerBody, P11ControlBudgets.Root.COMMAND_CONTEXT) == 1,
                "ACTUAL_COMBINED_FOP_AND_TWO_QCTX_BINDINGS");
        r.fopAtEntry = roots(r.body, P11ControlBudgets.Root.OPERATION);
        r.workAtEntry = roots(r.body, P11ControlBudgets.Root.WORK);
        r.reloadActive = true;
        r.gate.orTimeout(30, TimeUnit.SECONDS);
        try {
            // Original platform reload supplies managedBlock. It may genuinely cancel P5;
            // that is recorded, never reversed or relabelled as ordinary work lifetime.
            try {
                server.reloadResources(List.copyOf(server.getPackRepository().getSelectedIds())).join();
            } catch (RuntimeException | Error primary) {
                try { r.reloadFailure = reloadCategory(primary); }
                catch (RuntimeException | Error ignoredDiagnostic) { r.reloadFailure = "DIAGNOSTIC_UNAVAILABLE"; }
                try { r.reloadFailureDetails = reloadFailureDetails(primary); }
                catch (RuntimeException | Error ignoredDiagnostic) { r.reloadFailureObservationFailed = true; }
                throw primary;
            }
            r.reloadReturned = true;
            require(r.failure.equals("NONE") && r.registrations == 1 && r.applications == 1
                    && r.managedEntries == 1 && r.managedReturns == 1 && r.wait != null && r.gateReleased
                    && r.factories == 0 && r.frames == 0 && r.tries == 0
                    && r.taskCaptures == 1 && r.taskCalls == 1 && r.taskReturns == 1 && r.heldTask == null,
                    "RELOAD_REFUSAL_WITHOUT_NATIVE_BODY");
            r.workPresentAfterReload = instance.work != null;
            require(!P11NativeOperationBoundary.observe(context).terminal()
                    && roots(r.body, P11ControlBudgets.Root.COMMAND_CONTEXT) == 1
                    && roots(r.peerBody, P11ControlBudgets.Root.COMMAND_CONTEXT) == 1,
                    "ORIGINAL_CONTEXT_REMAINS_OWNED_AFTER_RELOAD");
        } finally { r.reloadActive = false; }
    }

    @SubscribeEvent static void listeners(AddReloadListenerEvent event) {
        var r = active;
        if (r == null || !r.reloadActive) { return; }
        synchronized (r) {
            if (active != r || !r.reloadActive) { return; }
            require(++r.registrations == 1, "ONE_RELOAD_LISTENER");
        }
        // Registration may run on the registry background executor; application is on game.
        event.addListener(new PreparableReloadListener() {
            @Override public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                    ProfilerFiller preparation, ProfilerFiller application,
                    java.util.concurrent.Executor background, java.util.concurrent.Executor game) {
                return barrier.wait(Boolean.TRUE).thenComposeAsync(ignored -> {
                    require(active == r && r.reloadActive && r.server.isSameThread()
                            && r.inManagedBlock && ++r.applications == 1, "ORIGINAL_RELOAD_APPLICATION");
                    r.waiting = true;
                    releaseTask(r);
                    return r.gate;
                }, game);
            }
        });
    }

    public static void managed(MinecraftServer server, boolean entering, boolean normal) {
        var r = active; if (r == null || r.server != server || !r.reloadActive) { return; }
        if (entering) { r.managedEntries++; r.inManagedBlock = true; }
        else { r.inManagedBlock = false; if (normal) { r.managedReturns++; } }
    }

    public static void ingress(Object value, Connection connection) {
        var r = active;
        if (r == null || connection != r.replacement || !(value instanceof Request request)
                || request.command() != Command.TRY) { return; }
        synchronized (r) {
            if (request.kind() != Kind.JOIN || request.scope() != Scope.CONFIG
                    || r.may == null || !r.localSealed || ++r.tries != 1
                    || request.requestSeq() != 1 || !sameScene(r.wait, request)) {
                r.failure = "FRESH_ORIGINAL_RETRY_IDENTITY"; return;
            }
            r.retry = request;
        }
    }

    /** Actual Common send normal-return; no call into the control owner. */
    public static void submitted(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var r = active;
        if (r == null || listener.getConnection() != r.replacement) { return; }
        if (!relevantNativeSend(packet)) { return; }
        String stage = "CURRENT_LISTENER";
        State observedState = null;
        try {
            require(r.server.isSameThread() && r.replacement.getPacketListener() == listener, "CURRENT_NATIVE_LISTENER");
            if (packet instanceof ClientboundLoginPacket) {
                stage = "LOGIN_FRAME";
                require(r.localSealed && r.retry != null && r.factories == 1 && ++r.frames == 1, "ONE_REAL_LOGIN_FRAME");
                return;
            }
            if (!(packet instanceof ClientboundCustomPayloadPacket custom)
                    || !(custom.payload() instanceof P11TransitionStatePayload payload)) { return; }
            var state = payload.state(); observedState = state;
            if (state.kind() != Kind.JOIN) { return; }
            if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.WAIT_NOTIFY) {
                if (r.wait != null) {
                    stage = "REPEATED_CONFIG_WAIT";
                    // STATUS may resend this immutable record after the original blockers ended.
                    // It is not a new refusal observation and must never reopen the owned gate.
                    require(state.equals(r.wait) && r.gateReleased && !r.gate.isCompletedExceptionally()
                            && r.failure.equals("NONE") && r.may == null && r.retry == null && r.completed == null
                            && r.factories == 0 && r.frames == 0 && r.tries == 0,
                            "EXACT_PROVEN_WAIT_RESEND");
                    return;
                }
                stage = "CONFIG_WAIT";
                require(state.scope() == Scope.CONFIG && state.requestSeq() == 0 && state.actorGeneration() == 0
                        && state.reason() == Reason.ACTIVE_OPERATION && r.waiting && r.inManagedBlock && r.reloadActive
                        && r.factories == 0 && r.frames == 0 && r.tries == 0 && r.body.account.current == r.body
                        && roots(r.body, P11ControlBudgets.Root.OPERATION) > 0
                        && roots(r.body, P11ControlBudgets.Root.COMMAND_CONTEXT) == 1
                        && roots(r.peerBody, P11ControlBudgets.Root.COMMAND_CONTEXT) == 1,
                        "REAL_CONFIG_COMBINED_BLOCKER");
                stage = "REFUSED_IDENTITY";
                require(r.wait == null || sameStateAttempt(r.wait, state), "ONE_REFUSED_SCENE");
                r.wait = state;
                if (!r.gateReleased) {
                    stage = "GATE_RELEASE";
                    r.gateReleased = r.gate.complete(null);
                    require(r.gateReleased, "OWNED_RELOAD_GATE_ALREADY_TERMINAL");
                }
            } else if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.MAY_TRY) {
                stage = "MAY_TRY";
                require(r.wait != null && sameStateAttempt(r.wait, state) && state.requestSeq() == 0
                        && !r.reloadActive && !r.inManagedBlock && r.factories == 0
                        && P11NativeOperationBoundary.observe(r.context).terminal()
                        && roots(r.body, P11ControlBudgets.Root.OPERATION) == 0
                        && roots(r.body, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0
                        && roots(r.peerBody, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0,
                        "MAY_ONLY_AFTER_REAL_OUTER_TERMINAL");
                r.may = state;
            } else if (state.outcome() == Outcome.COMPLETED) {
                stage = "COMPLETED";
                require(r.retry != null && sameScene(state, r.retry) && state.requestSeq() == 1
                        && state.scope() == Scope.PREPLAY && state.targetActorGeneration() > 0
                        && r.frames == 1 && r.factories == 1 && r.localSealed, "ORIGINAL_COMPLETED_FRESH_REQUEST");
                r.completed = state;
            }
        } catch (RuntimeException | Error failure) {
            try { captureNativeSendFailure(r, listener, packet, observedState, stage, failure); }
            catch (RuntimeException | Error ignoredDiagnostic) { /* Keep the original observer failure path. */ }
            finally { fail(r, "NATIVE_SEND_OBSERVER"); } // Outside the diagnostic monitor, even if capture failed.
        }
    }

    /** This scenario observes only the native Login frame and its P11 JOIN state stream. */
    private static boolean relevantNativeSend(Packet<?> packet) {
        return packet instanceof ClientboundLoginPacket
                || packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof P11TransitionStatePayload payload
                && payload.state().kind() == Kind.JOIN;
    }

    /** First observer failure only: fixed labels/scalars, never packet content or Throwable text. */
    private static void captureNativeSendFailure(Run r, ServerCommonPacketListenerImpl listener,
            Packet<?> packet, State observedState, String stage, Throwable failure) {
        synchronized (r) {
            if (r.nativeSendFailure != null) { return; }
            // Claim before any diagnostic callback: later failures cannot overwrite this first failure.
            r.nativeSendFailure = Map.of("status", "UNAVAILABLE");
            boolean main = r.server.isSameThread();
            var f = new LinkedHashMap<String, Object>();
            f.put("status", "FIRST_NATIVE_SEND_OBSERVER_FAILURE_NOT_PRODUCT_ATTRIBUTION");
            f.put("stage", stage); f.put("fixedCode", nativeSendFailureCode(failure));
            f.put("category", failure instanceof Error ? "ERROR" : "RUNTIME_EXCEPTION");
            f.put("packetCategory", packet instanceof ClientboundLoginPacket ? "LOGIN"
                    : packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof P11TransitionStatePayload ? "P11_STATE" : "OTHER");
            f.put("serverThread", main);
            f.put("listenerCurrent", r.replacement.getPacketListener() == listener);
            f.put("replacementConnected", r.replacement.isConnected());
            if (observedState == null && packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof P11TransitionStatePayload payload) { observedState = payload.state(); }
            f.put("statePresent", observedState != null);
            if (observedState != null) {
                f.put("scope", observedState.scope().name()); f.put("kind", observedState.kind().name());
                f.put("outcome", observedState.outcome().name()); f.put("availability", observedState.availability().name());
                f.put("reason", observedState.reason().name());
                f.put("connectionEpoch", observedState.connectionEpoch()); f.put("sceneSerial", observedState.sceneSerial());
                f.put("actorGeneration", observedState.actorGeneration()); f.put("requestSeq", observedState.requestSeq());
                f.put("statusVersion", observedState.statusVersion()); f.put("targetActorGeneration", observedState.targetActorGeneration());
            }
            f.put("mainOwnedFactsAvailable", main);
            if (main) {
                f.put("waiting", r.waiting); f.put("inManagedBlock", r.inManagedBlock); f.put("reloadActive", r.reloadActive);
                f.put("taskCaptures", r.taskCaptures); f.put("taskCalls", r.taskCalls); f.put("taskReturns", r.taskReturns);
                f.put("factories", r.factories); f.put("frames", r.frames); f.put("tries", r.tries);
                f.put("bodyCurrent", r.body.account.current == r.body);
                f.put("operationRoots", roots(r.body, P11ControlBudgets.Root.OPERATION));
                f.put("actorContextRoots", roots(r.body, P11ControlBudgets.Root.COMMAND_CONTEXT));
                f.put("peerContextRoots", roots(r.peerBody, P11ControlBudgets.Root.COMMAND_CONTEXT));
                f.put("contextPresent", r.context != null);
                if (r.context != null) { f.put("contextTerminal", P11NativeOperationBoundary.observe(r.context).terminal()); }
            }
            r.nativeSendFailure = Map.copyOf(f);
        }
    }

    private static String nativeSendFailureCode(Throwable failure) {
        return switch (P11C4aEvidence.failureCode(failure)) {
            case "L1_CONTEXT_CURRENT_NATIVE_LISTENER", "L1_CONTEXT_ONE_REAL_LOGIN_FRAME",
                    "L1_CONTEXT_EXACT_PROVEN_WAIT_RESEND",
                    "L1_CONTEXT_REAL_CONFIG_COMBINED_BLOCKER", "L1_CONTEXT_ONE_REFUSED_SCENE",
                    "L1_CONTEXT_OWNED_RELOAD_GATE_ALREADY_TERMINAL", "L1_CONTEXT_MAY_ONLY_AFTER_REAL_OUTER_TERMINAL",
                    "L1_CONTEXT_ORIGINAL_COMPLETED_FRESH_REQUEST" -> P11C4aEvidence.failureCode(failure);
            default -> "UNAVAILABLE";
        };
    }

    public static void factory(UUID id) {
        var r = active; if (r == null || !r.actor.getUUID().equals(id)) { return; }
        try {
            require(r.server.isSameThread() && r.localSealed && r.may != null && r.retry != null
                    && r.tries == 1 && !r.reloadActive && ++r.factories == 1, "EXACT_FACTORY_AFTER_MANUAL_RETRY");
        } catch (RuntimeException | Error failure) { fail(r, "FACTORY_OBSERVER"); }
    }

    /** First ordinary Post, after the existing whole reward proof has sealed its original R/e. */
    static void localSealed() throws IOException {
        var r = active;
        require(r != null && r.server.isSameThread() && r.failure.equals("NONE") && !r.localSealed
                && r.reloadReturned && r.instance.work == null && r.instance.lease.pin.isClosed()
                && P11NativeOperationBoundary.observe(r.context).terminal()
                && r.body.account.current == r.body && r.owner.canCopy(r.body)
                && r.factories == 0 && r.frames == 0, "LOCAL_REWARD_BEFORE_REPLACEMENT");
        r.localSealed = true;
        P11C4aEvidence.write(r.output, "work-context-local.json", facts(r, "ORIGINAL_REWARD_AND_CONTEXT_RETURNED"));
        P11C4aEvidence.cue(r.output, "a-context-retry.ready");
    }

    static boolean finish(ServerPlayer successor) throws IOException {
        var r = active;
        require(r != null && r.server.isSameThread() && r.failure.equals("NONE"), "FINISH_OWNER");
        if (r.completed == null || !P11C4aEvidence.receiptPresent(r.output.resolveSibling("client-a"), "work-context-client.json")) { return false; }
        require(r.localSealed && r.tries == 1 && r.factories == 1 && r.frames == 1 && successor != r.actor
                && successor.getUUID().equals(r.actor.getUUID()) && successor.connection.getConnection() == r.replacement
                && r.server.getPlayerList().getPlayer(successor.getUUID()) == successor,
                "REAL_NEW_BODY_AND_ONE_ORIGINAL_CONTINUATION");
        P11C4aEvidence.write(r.output, "work-context-result.json", facts(r, "NAMED_W_CONTEXT_REFUSAL_AND_FRESH_NATIVE_RETRY"));
        r.complete = true; return true;
    }
    static void abort() {
        var r = active; if (r == null) { return; }
        synchronized (r) { r.heldTask = null; }
        r.gate.completeExceptionally(new IllegalStateException("L1_CONTEXT_OWNER_ABORT"));
        active = null;
    }
    /** Fixed bounded diagnostics, captured before abort; never a success predicate. */
    static Map<String, Object> pending() {
        var r = active; if (r == null) { return Map.of("status", "NO_CONTEXT_RUN"); }
        synchronized (r) {
            var f = new LinkedHashMap<String, Object>(facts(r, "CAPTURE_BEFORE_ABORT_NOT_CAUSAL_ATTRIBUTION"));
            f.put("registrations", r.registrations); f.put("applications", r.applications);
            f.put("reloadActive", r.reloadActive); f.put("waiting", r.waiting); f.put("inManagedBlock", r.inManagedBlock);
            f.put("gateDone", r.gate.isDone()); f.put("gateExceptional", r.gate.isCompletedExceptionally());
            f.put("originalHasJoinedReplacementObserved", r.replacement != null);
            f.put("replacementConnected", r.replacement != null && r.replacement.isConnected());
            Object listener = r.replacement == null ? null : r.replacement.getPacketListener();
            f.put("replacementListenerCategory", listener == null ? "ABSENT"
                    : listener instanceof ServerLoginPacketListenerImpl ? "LOGIN"
                    : listener instanceof ServerConfigurationPacketListenerImpl ? "CONFIG"
                    : listener instanceof P11ParkingPacketListener ? "PARKING"
                    : listener instanceof ServerGamePacketListenerImpl ? "PLAY" : "OTHER");
            f.put("heldTaskPresent", r.heldTask != null); f.put("localSealed", r.localSealed);
            f.put("taskCaptureAttempted", r.taskCaptureAttempted); f.put("taskCaptureOnMain", r.taskCaptureOnMain);
            return f;
        }
    }
    private static String reloadCategory(Throwable failure) {
        if (failure.getClass() == java.util.concurrent.CompletionException.class
                && failure.getCause() instanceof java.util.concurrent.TimeoutException) { return "COMPLETION_TIMEOUT"; }
        if (failure instanceof java.util.concurrent.CompletionException) { return "COMPLETION_EXCEPTION"; }
        return failure instanceof Error ? "ERROR" : "RUNTIME_EXCEPTION";
    }

    /** At most four exact wrapper nodes; only closed categories/codes, no Throwable text or stack. */
    private static List<Map<String, Object>> reloadFailureDetails(Throwable original) {
        var values = new java.util.ArrayList<Map<String, Object>>();
        Throwable value = original;
        for (int depth = 0; value != null && depth < 4; depth++) {
            String category = value instanceof RuntimeKernelException ? "P5_KERNEL"
                    : value.getClass() == java.util.concurrent.CompletionException.class ? "COMPLETION_EXCEPTION"
                    : value.getClass() == java.util.concurrent.ExecutionException.class ? "EXECUTION_EXCEPTION"
                    : value.getClass() == net.minecraft.ReportedException.class ? "REPORTED_EXCEPTION"
                    : value instanceof IllegalStateException ? "ILLEGAL_STATE"
                    : value instanceof IllegalArgumentException ? "ILLEGAL_ARGUMENT"
                    : value instanceof NullPointerException ? "NULL_POINTER"
                    : value instanceof Error ? "ERROR" : "OTHER_EXCEPTION";
            values.add(Map.of("depth", depth, "category", category,
                    "kernelCode", value instanceof RuntimeKernelException kernel ? kernel.code().name() : "UNAVAILABLE",
                    "fixedOwnCode", reloadOwnFailureCode(value)));
            if (depth == 3 || !(value.getClass() == java.util.concurrent.CompletionException.class
                    || value.getClass() == java.util.concurrent.ExecutionException.class
                    || value.getClass() == net.minecraft.ReportedException.class)) { break; }
            var next = value.getCause();
            if (next == value) { break; }
            value = next;
        }
        return List.copyOf(values);
    }

    private static String reloadOwnFailureCode(Throwable failure) {
        String send = nativeSendFailureCode(failure);
        if (!"UNAVAILABLE".equals(send)) { return send; }
        return switch (P11C4aEvidence.failureCode(failure)) {
            case "L1_CONTEXT_ONE_RELOAD_LISTENER", "L1_CONTEXT_ORIGINAL_RELOAD_APPLICATION",
                    "L1_CONTEXT_ORIGINAL_TASK_RELEASE_IN_REAL_WORK_CONTEXT",
                    "L1_CONTEXT_REAL_OFFLINE_WORK_FUNCTION", "L1_CONTEXT_CAPTURE_REMAINS_OWNED_AT_COMMAND",
                    "L1_CONTEXT_ACTUAL_COMBINED_FOP_AND_TWO_QCTX_BINDINGS",
                    "L1_CONTEXT_RELOAD_REFUSAL_WITHOUT_NATIVE_BODY",
                    "L1_CONTEXT_ORIGINAL_CONTEXT_REMAINS_OWNED_AFTER_RELOAD",
                    "L1_CONTEXT_TASK_MUST_ARRIVE_BEFORE_NATURAL_HIT",
                    "L1_CONTEXT_EXACT_NATIVE_REQUIRED_TASK_CAPTURE", "L1_CONTEXT_NATIVE_TASK_HOLD_DEADLINE",
                    "L1_CONTEXT_RECONNECT_AFTER_TRUE_LOGOUT", "L1_CONTEXT_NATURAL_HURT_OWNER",
                    "L1_CONTEXT_TASK_HOLD_OWNER" -> P11C4aEvidence.failureCode(failure);
            default -> "UNAVAILABLE";
        };
    }

    private static void fail(Run r, String code) {
        if (r.failure.equals("NONE")) { r.failure = code; }
        r.gate.completeExceptionally(new IllegalStateException(code));
    }
    private static long roots(P11QualifiedSourceOwner.Body body, P11ControlBudgets.Root root) { return body.account.nativeCounts[root.ordinal()]; }
    private static boolean sameStateAttempt(State a, State b) {
        return a.scope() == b.scope() && a.connectionEpoch() == b.connectionEpoch() && a.sceneSerial() == b.sceneSerial()
                && a.actorGeneration() == b.actorGeneration() && a.requestSeq() == b.requestSeq() && a.kind() == b.kind();
    }
    private static boolean sameScene(State state, Request request) {
        return state != null && state.connectionEpoch() == request.connectionEpoch() && state.sceneSerial() == request.sceneSerial()
                && state.actorGeneration() == request.actorGeneration() && state.kind() == request.kind();
    }
    private static Map<String,Object> facts(Run r, String status) {
        var f = new LinkedHashMap<String,Object>(); f.put("status", status); f.put("failure", r.failure);
        f.put("firstNativeSendFailure", r.nativeSendFailure == null ? Map.of("status", "NOT_OBSERVED") : r.nativeSendFailure);
        f.put("wait", r.wait); f.put("mayTry", r.may); f.put("freshRequest", r.retry); f.put("completed", r.completed);
        f.put("actualWAtCommand", r.workAtEntry); f.put("actualFopAtCommand", r.fopAtEntry);
        f.put("actualContextBindingsAtCommand", 2); f.put("combinedFopAndQctxNotIsolatedQctx", true);
        f.put("originalReloadCalls", r.entries); f.put("managedEntries", r.managedEntries); f.put("managedNormalReturns", r.managedReturns);
        f.put("originalReloadReturned", r.reloadReturned); f.put("workPresentAfterOriginalReload", r.workPresentAfterReload);
        f.put("reloadFailureCategory", r.reloadFailure);
        f.put("originalReloadFailure", r.reloadFailureDetails);
        f.put("reloadFailureObservationFailed", r.reloadFailureObservationFailed);
        f.put("requiredTaskCaptures", r.taskCaptures); f.put("originalRequiredTaskCalls", r.taskCalls);
        f.put("originalRequiredTaskNormalReturns", r.taskReturns);
        f.put("taskAlreadyCapturedBeforeOriginalHurt", r.naturalHurtSeen);
        f.put("engineeringRequiredTaskDeliveryHold", true); f.put("extraNetworkTickPump", false);
        f.put("workNullAtLocalTerminal", r.instance != null && r.instance.work == null);
        f.put("normalWorkLifetimePreservationClaim", false); f.put("hitDelayOrGrantReplay", false);
        f.put("originalTryIngress", r.tries); f.put("factoryTicketReturns", r.factories); f.put("originalLoginSendReturns", r.frames);
        f.put("gateReleasedByOriginalWaitSendReturn", r.gateReleased); f.put("fullC4aMatrixClaim", false); return f;
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "L1_CONTEXT_" + code); }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final Path output; final Connection oldConnection;
        final P11QualifiedSourceOwner owner; final P11QualifiedSourceOwner.Body body, peerBody;
        final CompletableFuture<Void> gate = new CompletableFuture<>();
        volatile Connection replacement; volatile boolean departureReady, reloadActive, localSealed;
        volatile String failure = "NONE", reloadFailure = "NONE";
        volatile Map<String, Object> nativeSendFailure;
        List<Map<String, Object>> reloadFailureDetails; boolean reloadFailureObservationFailed;
        volatile int registrations, tries; volatile Request retry;
        ServerSlot.InstanceState instance; ExecutionContext<?> context; volatile State wait, may; State completed;
        boolean waiting, inManagedBlock, gateReleased, reloadReturned, workPresentAfterReload, complete;
        int entries, applications, managedEntries, managedReturns, factories, frames; long workAtEntry, fopAtEntry;
        HeldTask heldTask; boolean naturalHurtSeen, taskCaptureAttempted, taskCaptureOnMain;
        int taskCaptures, taskCalls, taskReturns;
        Run(MinecraftServer s, ServerPlayer a, ServerPlayer p, Path out, P11QualifiedSourceOwner o,
                P11QualifiedSourceOwner.Body b, P11QualifiedSourceOwner.Body pb) {
            server=s; actor=a; peer=p; output=out; owner=o; body=b; peerBody=pb; oldConnection=a.connection.getConnection();
        }
    }
    private record HeldTask(ServerConfigurationPacketListenerImpl listener, ConfigurationTask task,
            Consumer<Packet<?>> sender, Operation<Void> original, long capturedAt) { }
}
