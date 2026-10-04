package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Excluded finite native scenarios. The only latch controls our real reload listener, never a permit. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aReloadBlockerProbe {
    public enum Mode { FOP, QCTX }
    private static final ResourceLocation ADVANCEMENT = ResourceLocation.withDefaultNamespace("adventure/sleep_in_bed");
    private static volatile Run active;

    private P11C4aReloadBlockerProbe() { }

    /** Fixture prearm only; neither holds a production root nor creates a transition request. */
    static void prepareUiClient(MinecraftServer server, ServerPlayer actor, String role, Path output) throws IOException {
        require(P11C4aEvidence.enabled() && server.isSameThread() && active == null
                && List.of("a", "host").contains(role) && actor.getServer() == server
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor && !actor.isFakePlayer()
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor)
                && (P11C4aScenario.MODE == P11C4aScenario.Mode.UI_CALLBACK
                    || P11C4aScenario.MODE == P11C4aScenario.Mode.UI_HELD), "RELOAD_UI_PREPARE_OWNER");
        P11C4aEvidence.cue(output, role + "-ui-input-prepare.ready");
    }

    private static boolean uiOrderedDeath(Mode mode) {
        return mode == Mode.FOP && (P11C4aScenario.MODE == P11C4aScenario.Mode.UI_CALLBACK
                || P11C4aScenario.MODE == P11C4aScenario.Mode.UI_HELD);
    }

    /** Called once by the existing authenticated scenario owner after its normal subset. */
    static void start(MinecraftServer server, ServerPlayer actor, Mode mode, String role, Path output) throws IOException {
        require(P11C4aEvidence.enabled() && server.isSameThread() && active == null, "RELOAD_START_OWNERSHIP");
        require(List.of("a", "host").contains(role) && actor.getServer() == server
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor && !actor.isFakePlayer()
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor), "RELOAD_EXACT_NATIVE_ACTOR");
        if (uiOrderedDeath(mode)) {
            require(P11C4aEvidence.cuePresent(output, role + "-ui-input-prepare.ready")
                    && P11C4aEvidence.cuePresent(P11C4aEvidence.root().resolve("client-" + role),
                            "ui-input-armed.ready"), "RELOAD_UI_CLIENT_NOT_PREARMED");
        }
        var run = new Run(server, actor, mode, role, output);
        active = run;
        var rules = server.getGameRules();
        run.keepInventory = rules.getBoolean(GameRules.RULE_KEEPINVENTORY);
        run.immediate = rules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(P11C4aNativeSenderProbe.selected(), server);
        var holder = server.getAdvancements().get(ADVANCEMENT);
        require(holder != null && holder.value().rewards().equals(AdvancementRewards.EMPTY), "RELOAD_ZERO_REWARD_PA_FIXTURE");
        for (var criterion : holder.value().criteria().keySet()) {
            actor.getAdvancements().revoke(holder, criterion);
            require(actor.getAdvancements().award(holder, criterion), "RELOAD_NATIVE_PA_AWARD");
        }
        require(actor.getAdvancements().getOrStartProgress(holder).isDone(), "RELOAD_NATIVE_PA_NOT_DONE");
        P11C4aEvidence.write(output, run.label + "-armed.json", Map.of("status", "ARMED_NOT_ACCEPTANCE", "mode", mode.name()));
        if (P11C4aNativeSenderProbe.selected()) {
            require(mode == Mode.FOP && actor.isDeadOrDying(), "SENDERS_RELOAD_REQUIRES_PRECEDING_NATIVE_HEALTH_TICK");
        } else if (!uiOrderedDeath(mode)) {
            actor.kill();
            require(actor.isDeadOrDying(), "RELOAD_NATIVE_DEATH_MISSING");
        }
        run.xpBefore = actor.totalExperience;
        run.xpArmed = true;
        try {
            if (mode == Mode.FOP) {
                AdvancementRewards.Builder.experience(run.amount).build().grant(actor);
            } else {
                var playerSource = actor.createCommandSourceStack().withPermission(2).withSuppressedOutput();
                var function = CommandFunction.fromLines(ResourceLocation.fromNamespaceAndPath(
                        "gramarye_p11_engineering", "c4a_reload_enrollment"), server.getCommands().getDispatcher(),
                        playerSource, List.of("tag @s add p11_c4a_reload_enrollment"));
                // The real function enrolls A in this original queue. Console owns the outer call;
                // after the parsed command returns its Fop is gone but the same Qctx still owns A.
                Commands.executeCommandInContext(server.createCommandSourceStack(), context -> {
                    run.context = context;
                    server.getFunctions().execute(function, playerSource);
                    context.queueNext(new CommandQueueEntry<>(new Frame(0, CommandResultCallback.EMPTY, () -> { }),
                            (queue, frame) -> actor.giveExperiencePoints(run.amount)));
                });
                var terminal = P11NativeOperationBoundary.observe(run.context);
                require(terminal.terminal() && terminal.outerNormal() && terminal.retainedBindings() == 0
                        && actor.getTags().contains("p11_c4a_reload_enrollment"), "RELOAD_QCTX_TRUE_TERMINAL");
                run.context = null;
            }
            run.outerReturned = true;
            require(run.failure == null && run.xpCallbacks == 1 && run.reloadReturned
                    && actor.totalExperience == run.xpBefore + run.amount, "RELOAD_NATIVE_TAIL_NOT_EXACT");
            require(roots(run, P11ControlBudgets.Root.OPERATION) == 0
                    && roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0, "RELOAD_ROOT_NOT_RELEASED");
            P11C4aEvidence.write(output, run.label + "-tail.json", report(run, "NATIVE_RELOAD_AND_OWNER_TAIL_RETURNED"));
        } finally { run.xpArmed = false; }
    }

    /** Main-thread polling after start returns; no retry is performed here. */
    static boolean finish(ServerPlayer successor, Path clientOutput) throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread(), "RELOAD_FINISH_OWNERSHIP");
        require(run.failure == null, "RELOAD_OBSERVER_FAILED");
        if (run.completed == null || !P11C4aEvidence.receiptPresent(clientOutput, run.label + ".json")) { return false; }
        require(!uiOrderedDeath(run.mode) || run.uiDeathInsideApplication, "RELOAD_UI_DEATH_CAUSE_MISSING");
        if (run.mode == Mode.FOP && P11C4aScenario.MODE == P11C4aScenario.Mode.UI_CALLBACK
                && !P11C4aEvidence.receiptPresent(clientOutput, "ui-input-callback-subset.json")) { return false; }
        if (run.mode == Mode.FOP && P11C4aScenario.MODE == P11C4aScenario.Mode.UI_HELD
                && !P11C4aEvidence.receiptPresent(clientOutput, "ui-held-input.json")) { return false; }
        require(run.outerReturned && run.waitState != null && run.mayState != null && run.tries == 2
                && run.bodies == 1 && run.frames == 1 && run.managedEntries == 1 && run.managedReturns == 1
                && run.listenerRegistrations == 1 && run.listenerCalls == 1 && run.gateReleased
                && successor != run.actor && successor.getUUID().equals(run.actor.getUUID())
                && successor.connection.getConnection() == run.connection
                && run.server.getPlayerList().getPlayer(successor.getUUID()) == successor
                && successor.getAdvancements() == run.canonical && P11NativeStorageBoundary.nativeDeliveryEligible(successor)
                && successor.totalExperience == run.xpBefore + run.amount, "RELOAD_SUCCESSOR_OR_COUNTS");
        var newHolder = run.server.getAdvancements().get(ADVANCEMENT);
        require(newHolder != null && successor.getAdvancements().getOrStartProgress(newHolder).isDone(), "RELOAD_CANONICAL_PROGRESS_LOST");
        require(run.server.saveEverything(true, false, false), "RELOAD_ORIGINAL_SAVE_FAILED");
        var saved = NbtIo.readCompressed(run.server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(successor.getUUID() + ".dat"), NbtAccounter.unlimitedHeap());
        require(saved.getInt("XpTotal") == successor.totalExperience, "RELOAD_ORIGINAL_XP_READBACK");
        P11C4aEvidence.write(run.output, run.label + ".json", report(run, "ACTUAL_REFUSAL_MANUAL_RETRY_NATIVE_BODY_AND_READBACK"));
        restoreRules(run);
        active = null;
        return true;
    }

    /** Called by the existing finite harness failure path before its original server shutdown. */
    static void abort() {
        var run = active;
        if (run == null) { return; }
        require(run.server.isSameThread(), "RELOAD_ABORT_NOT_MAIN");
        run.gate.completeExceptionally(new IllegalStateException("RELOAD_OWNER_ABORT"));
        restoreRules(run);
        active = null;
    }

    static Map<String, Object> pending() {
        var run = active;
        return run == null ? Map.of("active", false) : report(run, "PENDING_NOT_ACCEPTANCE");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void xp(PlayerXpEvent.XpChange event) {
        var run = active;
        if (run == null || !run.xpArmed || event.getEntity() != run.actor || event.getAmount() != run.amount) { return; }
        run.xpArmed = false;
        run.xpCallbacks++;
        require(run.server.isSameThread(), "RELOAD_XP_NOT_MAIN");
        run.fopAtXp = roots(run, P11ControlBudgets.Root.OPERATION);
        run.qctxAtXp = roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT);
        require(run.mode == Mode.FOP ? run.fopAtXp > 0 && run.qctxAtXp == 0
                : run.fopAtXp == 0 && run.qctxAtXp > 0, "RELOAD_WRONG_REAL_BLOCKER");
        if (P11C4aNativeSenderProbe.selected()) { P11C4aNativeSenderProbe.fopObserved(run.actor); }
        run.reloadActive = true;
        run.gate.orTimeout(30, TimeUnit.SECONDS);
        try {
            // Original method enters its own managedBlock. This is a global pack/resource reload,
            // including PlayerList.reloadResources and the real canonical PA reload consumer.
            run.server.reloadResources(List.copyOf(run.server.getPackRepository().getSelectedIds())).join();
            run.reloadReturned = true;
            require(run.listenerRegistrations == 1, "RELOAD_LISTENER_NOT_REGISTERED");
            require(run.listenerCalls == 1 && run.listenerWaiting && run.listenerApplicationOnServerThread,
                    "RELOAD_LISTENER_NOT_APPLIED_ON_SERVER");
            require(run.gateReleased && run.waitState != null, "RELOAD_NO_ACTUAL_REFUSAL_GATE_RELEASE");
            require(run.bodies == 0 && run.frames == 0, "RELOAD_BODY_STARTED_BEFORE_OWNER_RETURN");
        } finally { run.reloadActive = false; }
    }

    @SubscribeEvent
    static void addReloadListener(AddReloadListenerEvent event) {
        var run = active;
        if (run == null || !run.reloadActive) { return; }
        // Locked Z posts this event from the reloadable-registry future's background
        // continuation. Only the listener application below belongs on the game executor.
        synchronized (run) {
            if (active != run || !run.reloadActive) { return; }
            require(++run.listenerRegistrations == 1, "RELOAD_MULTIPLE_LISTENER_REGISTRATION");
            run.listenerRegistrationOnServerThread = run.server.isSameThread();
        }
        event.addListener(new PreparableReloadListener() {
            @Override public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                    ProfilerFiller preparation, ProfilerFiller application,
                    java.util.concurrent.Executor background, java.util.concurrent.Executor game) {
                run.listenerCalls++;
                return barrier.wait(Boolean.TRUE).thenComposeAsync(ignored -> {
                    require(active == run && run.reloadActive && run.server.isSameThread(), "RELOAD_LISTENER_APPLICATION_OWNER");
                    run.listenerApplicationOnServerThread = true;
                    run.listenerWaiting = true;
                    if (uiOrderedDeath(run.mode)) {
                        // Fixture cause ordering, not a product/player input delay:
                        // the original death happens only after this actual Fop/reload gate is ready.
                        require(run.inManagedBlock && roots(run, P11ControlBudgets.Root.OPERATION) > 0
                                && roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0
                                && !run.uiDeathInsideApplication, "RELOAD_UI_REAL_OWNER_NOT_READY");
                        run.uiDeathInsideApplication = true;
                        run.actor.kill();
                        require(run.actor.isDeadOrDying(), "RELOAD_UI_NATIVE_DEATH_MISSING");
                    }
                    try { P11C4aEvidence.cue(run.output, run.role + '-' + run.label + ".ready"); }
                    catch (IOException failure) { fail(run, "RELOAD_CUE_IO_FAILURE"); }
                    return run.gate;
                }, game);
            }
        });
    }

    /** Exact reloadResources → managedBlock callsite observation; no substitute wait loop. */
    public static void managed(MinecraftServer server, boolean entering, boolean normal) {
        var run = active;
        if (run == null || run.server != server || !run.reloadActive) { return; }
        if (entering) { run.managedEntries++; run.inManagedBlock = true; }
        else { run.inManagedBlock = false; if (normal) { run.managedReturns++; } }
    }

    /** Observes the current listener's real inbound request; it never calls ingress itself. */
    public static void ingress(Object value, Connection connection) {
        var run = active;
        if (run == null || connection != run.connection || !(value instanceof Request request)
                || request.command() != Command.TRY || request.kind() != Kind.DEATH) { return; }
        synchronized (run) {
            run.tries++;
            if (run.tries == 1) { run.firstRequest = request; }
            else if (run.tries == 2) { run.secondRequest = request; }
            else { fail(run, "RELOAD_MORE_THAN_TWO_TRIES"); }
        }
    }

    /** Runs only after original send returns. No graph or scope is handed to production. */
    public static void submitted(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection) { return; }
        if (!(packet instanceof ClientboundRespawnPacket)
                && !(packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof P11TransitionStatePayload)) { return; }
        try {
            require(run.server.isSameThread() && run.connection.getPacketListener() == listener, "RELOAD_SEND_OWNER");
            if (packet instanceof ClientboundRespawnPacket) { run.frames++; return; }
            if (!(packet instanceof ClientboundCustomPayloadPacket custom)
                    || !(custom.payload() instanceof P11TransitionStatePayload payload)) { return; }
            var state = payload.state();
            if (state.kind() != Kind.DEATH || state.outcome() == Outcome.BINDING) { return; }
            if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.WAIT_NOTIFY) {
                require(matches(run.firstRequest, state) && run.inManagedBlock
                        && run.reloadActive && (run.listenerWaiting || P11C4aNativeSenderProbe.selected())
                        && run.bodies == 0 && run.frames == 0
                        && state.reason() == (run.mode == Mode.FOP ? Reason.ACTIVE_OPERATION : Reason.ACTIVE_CONTEXT)
                        && (run.mode == Mode.FOP ? roots(run, P11ControlBudgets.Root.OPERATION) > 0
                        : roots(run, P11ControlBudgets.Root.OPERATION) == 0 && roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT) > 0),
                        "RELOAD_REFUSAL_NOT_FROM_REAL_OWNER");
                if (run.waitState == null) {
                    run.waitState = state;
                }
                // A later original STATUS resubmits the same WAIT. Only this selected
                // fixture keeps its real reload listener pending until the hidden sender
                // actually returned on the client. No extra status or transition is sent.
                if (run.listenerWaiting && !run.gateReleased && (!P11C4aNativeSenderProbe.selected()
                        || P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-" + run.role),
                                "native-hidden-auto.json"))) {
                    run.gateReleased = run.gate.complete(null);
                    require(run.gateReleased, "RELOAD_GATE_ALREADY_TERMINAL");
                }
            } else if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.MAY_TRY) {
                require(matches(run.firstRequest, state) && run.waitState != null && !run.inManagedBlock
                        && roots(run, P11ControlBudgets.Root.OPERATION) == 0
                        && roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0 && run.bodies == 0,
                        "RELOAD_MAY_BEFORE_OWNER_TERMINAL");
                run.mayState = state;
            } else if (state.outcome() == Outcome.COMPLETED) {
                require(run.mayState != null && matches(run.secondRequest, state)
                        && run.secondRequest.requestSeq() > run.firstRequest.requestSeq(), "RELOAD_COMPLETION_NOT_FRESH_RETRY");
                run.completed = state;
            }
        } catch (RuntimeException | Error observerFailure) { fail(run, "RELOAD_SUBMISSION_OBSERVER"); }
    }

    public static void body(ServerPlayer original) {
        var run = active;
        if (run == null || original != run.actor) { return; }
        run.bodies++;
        if (run.mayState == null || run.tries != 2 || run.inManagedBlock) { fail(run, "RELOAD_BODY_BEFORE_FRESH_RETRY"); }
    }

    @SubscribeEvent static void stopped(ServerStoppedEvent event) {
        var run = active;
        if (run != null && run.server == event.getServer()) { run.gate.completeExceptionally(new IllegalStateException("RELOAD_SERVER_STOP")); active = null; }
    }

    private static long roots(Run run, P11ControlBudgets.Root root) {
        var source = P11NativeStorageBoundary.nativeSourceOwner(run.actor);
        var body = source == null ? null : source.nativeRecipient(run.actor);
        require(body != null && body.actor == run.actor, "RELOAD_NATIVE_SOURCE_NOT_EXACT");
        return body.account.nativeCounts[root.ordinal()];
    }

    private static boolean matches(Request request, State state) {
        return request != null && request.scope() == state.scope() && request.connectionEpoch() == state.connectionEpoch()
                && request.sceneSerial() == state.sceneSerial() && request.actorGeneration() == state.actorGeneration()
                && request.requestSeq() == state.requestSeq() && request.kind() == state.kind();
    }

    private static void fail(Run run, String code) {
        if (run.failure == null) { run.failure = code; }
        run.gate.completeExceptionally(new IllegalStateException(code));
    }

    private static void restoreRules(Run run) {
        run.server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(run.keepInventory, run.server);
        run.server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(run.immediate, run.server);
    }

    private static Map<String, Object> report(Run run, String status) {
        var values = new LinkedHashMap<String, Object>();
        values.put("status", status); values.put("mode", run.mode.name()); values.put("failure", run.failure == null ? "NONE" : run.failure);
        values.put("scenario", P11C4aScenario.MODE.name());
        values.put("fixtureNativeDeathInsideActualReloadApplication", run.uiDeathInsideApplication);
        values.put("actualFopAtXp", run.fopAtXp); values.put("actualQctxAtXp", run.qctxAtXp);
        values.put("xpCallbacks", run.xpCallbacks); values.put("xpBefore", run.xpBefore); values.put("xpDelta", run.amount);
        values.put("tries", run.tries); values.put("nativeBodies", run.bodies); values.put("nativeRespawnFrames", run.frames);
        values.put("managedBlockEntries", run.managedEntries); values.put("managedBlockNormalReturns", run.managedReturns);
        values.put("listenerRegistrations", run.listenerRegistrations); values.put("listenerCalls", run.listenerCalls);
        values.put("listenerRegistrationOnServerThread", run.listenerRegistrationOnServerThread);
        values.put("listenerApplicationOnServerThread", run.listenerApplicationOnServerThread);
        values.put("gateReleasedByActualRefusal", run.gateReleased); values.put("reloadReturned", run.reloadReturned);
        values.put("ownerOuterReturned", run.outerReturned); values.put("wait", run.waitState); values.put("mayTry", run.mayState);
        values.put("completed", run.completed); values.put("fullC4aAcceptance", false);
        return values;
    }

    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }

    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor; final Mode mode; final Path output;
        final Connection connection; final PlayerAdvancements canonical; final String label, role; final int amount;
        final CompletableFuture<Void> gate = new CompletableFuture<>();
        volatile String failure; volatile int listenerCalls, listenerRegistrations;
        volatile boolean reloadActive, listenerRegistrationOnServerThread;
        volatile Request firstRequest, secondRequest; volatile int tries;
        ExecutionContext<?> context;
        State waitState, mayState, completed;
        boolean keepInventory, immediate, xpArmed, reloadReturned, outerReturned, gateReleased, inManagedBlock, listenerWaiting;
        boolean listenerApplicationOnServerThread, uiDeathInsideApplication;
        int xpBefore, xpCallbacks, bodies, frames, managedEntries, managedReturns;
        long fopAtXp, qctxAtXp;
        Run(MinecraftServer server, ServerPlayer actor, Mode mode, String role, Path output) {
            this.server = server; this.actor = actor; this.mode = mode; this.output = output;
            this.role = role;
            this.connection = actor.connection.getConnection(); this.canonical = actor.getAdvancements();
            this.label = mode == Mode.FOP ? "reload-fop" : "reload-qctx"; this.amount = mode == Mode.FOP ? 17 : 19;
        }
    }
}
