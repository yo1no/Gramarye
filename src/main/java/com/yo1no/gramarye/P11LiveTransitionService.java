package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/** One server's bounded control root. All native execution is synchronous on its owning thread. */
final class P11LiveTransitionService {
    private final MinecraftServer server;
    private final P11StartupLimits limits;
    private final P11IdentityOwner identities;
    private final P11QualifiedSourceOwner sources;
    private final P11ControlBudgets.FairDispatcher dispatcher;
    private final P11ControlBudgets.MainWakeup wakeup = new P11ControlBudgets.MainWakeup();
    private final Runnable queuedPump = this::pumpQueued;
    private final long clockOrigin = System.nanoTime();
    // Registry sections only manipulate finite references/scalars. No callbacks under this monitor.
    private final Map<Connection, Entry> entries = new IdentityHashMap<>();
    private final Map<Long, Entry> epochs = new HashMap<>();
    private final Map<Connection, Boolean> starting = new IdentityHashMap<>();
    private final NativeContinuity continuity = new NativeContinuity(this);
    private volatile boolean stopping;
    private int waiting;
    private long tick = -1;
    private Call call;
    private long terminalFailures;

    P11LiveTransitionService(MinecraftServer server, P11StartupLimits limits,
            P11IdentityOwner identities, P11QualifiedSourceOwner sources) {
        this.server = server;
        this.limits = limits;
        this.identities = identities;
        this.sources = sources;
        dispatcher = new P11ControlBudgets.FairDispatcher(
                Math.addExact((long) server.getMaxPlayers(), limits.maxWaitingConnections()), limits.mainQuantaPerTick());
    }

    static final class NativeContinuity {
        private final P11LiveTransitionService owner;
        private NativeContinuity(P11LiveTransitionService owner) { this.owner = owner; }
    }

    NativeContinuity continuity(P11QualifiedSourceOwner expected) {
        return !stopping && server.isSameThread() && sources == expected ? continuity : null;
    }

    /** Call-local, private construction; never returned to payload code or retained for a retry. */
    static final class Ticket {
        private final P11LiveTransitionService owner;
        private final Entry entry;
        private final ServerCommonPacketListenerImpl caller;
        private final P11TransitionControl.Attempt attempt;
        private final Kind kind;
        private final ServerPlayer originalActor;
        private P11QualifiedSourceOwner.ControlCustody custody;
        private ServerPlayer expectedActor;
        private P11IdentityOwner.CapturedIdentity expectedIdentity;
        private Packet<?> expectedFrame;
        private ServerboundClientCommandPacket authorizedRespawnPacket;
        private boolean factoryConsumed;
        private boolean respawnConsumed;
        private boolean nativeEntryConsumed;
        private boolean closed;

        private Ticket(P11LiveTransitionService owner, Entry entry,
                ServerCommonPacketListenerImpl caller, P11TransitionControl.Attempt attempt, Kind kind,
                P11QualifiedSourceOwner.ControlCustody custody) {
            this.owner = owner; this.entry = entry; this.caller = caller; this.attempt = attempt;
            this.kind = kind; this.custody = custody;
            originalActor = caller instanceof ServerGamePacketListenerImpl game ? game.player : null;
        }
    }

    private static final class Call {
        private final ServerCommonPacketListenerImpl caller;
        private final Call previous;
        private Ticket ticket;
        private Call(ServerCommonPacketListenerImpl caller, Call previous) {
            this.caller = caller; this.previous = previous;
        }
    }

    /** Delivery hold only; no transport, actor or admission authority is created by this scalar. */
    private enum TerminalInterval { NONE, TO_CONFIG, TO_PLAY }

    private static final class Entry {
        private final Connection connection;
        private final UUID playerId;
        private final P11TransitionControl control;
        private final P11ControlBudgets.FairDispatcher.Member member;
        private P11TransitionControl.Handoff handoff;
        private boolean waiting;
        private boolean taskPending;
        private boolean configurationStarting;
        private volatile long configurationTerminalPhase;
        private volatile boolean configurationCapacityRejected;
        private volatile boolean blockersChanged;
        private boolean retryCheck;
        private boolean preferRetryCheck = true;
        private String closeReason;
        private TerminalInterval terminalInterval = TerminalInterval.NONE;

        private Entry(Connection connection, UUID playerId, P11TransitionControl control,
                P11ControlBudgets.FairDispatcher.Member member) {
            this.connection = connection; this.playerId = playerId;
            this.control = control; this.member = member;
        }
    }

    boolean owns(Connection connection) {
        synchronized (entries) { return !stopping && entries.containsKey(connection); }
    }

    boolean ownsServer(MinecraftServer exact) { return exact == server; }

    private Entry entry(Connection connection) {
        synchronized (entries) { return entries.get(connection); }
    }

    private long now() {
        long elapsed = System.nanoTime() - clockOrigin;
        return elapsed < 0 ? -1 : elapsed / 1_000_000L;
    }

    void ingress(Request request, Connection connection, ICommonPacketListener listener) {
        var entry = entry(connection);
        if (stopping || entry == null || connection.getPacketListener() != listener
                || listener.getConnection() != connection || !connection.isConnected()) { return; }
        var observed = entry.control.listener();
        var result = entry.control.offer(observed, request, now());
        if (result == P11TransitionControl.Offer.RETAINED
                || result == P11TransitionControl.Offer.COALESCED
                || result == P11TransitionControl.Offer.BUSY
                || result == P11TransitionControl.Offer.RATE_LIMITED) {
            dispatcher.eligible(entry.member);
            requestPump();
        }
    }

    void configurationAcknowledged(ServerGamePacketListenerImpl previous,
            ServerConfigurationPacketListenerImpl installed) {
        var connection = previous.getConnection();
        var entry = entry(connection);
        if (stopping || entry == null || installed.getConnection() != connection
                || previous.getMainThreadEventLoop() != server || installed.getMainThreadEventLoop() != server
                || connection.getPacketListener() != installed || !connection.isConnected()
                || !(installed instanceof P11KeepAliveBoundary.CommonAccess access)) { return; }
        var bound = entry.control.binding();
        if (!identities.matchesConfigurationPredecessor(bound, previous, installed)) { return; }
        long phase = access.p11$keepAlivePhase();
        if (phase <= 0) { return; }
        // K measures native CONFIG/PREPLAY occupancy, not an old PLAY listener awaiting ACK.
        // Reserve synchronously at the completed ACK before admitting any actorless service.
        synchronized (entries) {
            if (stopping || entries.get(connection) != entry
                    || connection.getPacketListener() != installed || !connection.isConnected()
                    || !bound.currentBinding()
                    || entry.configurationTerminalPhase != 0 || entry.configurationCapacityRejected) { return; }
            if (!entry.waiting && waiting >= limits.maxWaitingConnections()) {
                entry.configurationCapacityRejected = true;
                entry.configurationTerminalPhase = -1; // One pending capacity close, not an installed phase.
            } else {
                if (!entry.waiting) { entry.waiting = true; waiting++; }
                entry.configurationTerminalPhase = phase;
            }
        }
        dispatcher.eligible(entry.member);
        requestPump();
    }

    P11LiveTransitionBoundary.TaskDecision taskStarted(ServerConfigurationPacketListenerImpl listener) {
        var connection = listener.getConnection();
        if (listener.getMainThreadEventLoop() != server
                || !(listener instanceof P11ConfigurationBoundary.Access)
                || !(listener instanceof P11KeepAliveBoundary.CommonAccess)
                || !(connection instanceof P11KeepAliveBoundary.ConnectionAccess)) {
            listener.disconnect(Component.translatable("gramarye.transition.unavailable"));
            throw new IllegalStateException("P11_REQUIRED_CONTROL_HOOK_UNAVAILABLE");
        }
        if (stopping || connection.getPacketListener() != listener || !connection.isConnected()) {
            return P11LiveTransitionBoundary.TaskDecision.WAIT;
        }
        boolean admitted;
        boolean rejected;
        synchronized (entries) {
            var existing = entries.get(connection);
            rejected = existing != null && existing.configurationCapacityRejected;
            admitted = !rejected && starting.containsKey(connection);
            if (!rejected && !admitted && (existing != null && existing.waiting
                    || waiting < limits.maxWaitingConnections())) {
                boolean reservation = existing == null || !existing.waiting;
                if (reservation) { waiting++; }
                if (existing != null) { existing.waiting = true; }
                starting.put(connection, existing == null && reservation);
                admitted = true;
            }
        }
        if (rejected) {
            requestPump(); // The exact rejected ACK owns its existing bounded close service.
        } else if (!admitted) {
            listener.disconnect(Component.translatable("gramarye.transition.capacity"));
        } else { requestPump(); }
        // Even on main, one global dispatcher owns task service. No recursive task/body drain.
        return P11LiveTransitionBoundary.TaskDecision.WAIT;
    }

    void tick() {
        requireMain();
        if (stopping) { return; }
        long nativeTick = Integer.toUnsignedLong(server.getTickCount());
        if (nativeTick < tick) { stop(); return; }
        boolean newTick = nativeTick != tick;
        tick = nativeTick;
        if (!wakeup.beginTick(newTick)) { return; }
        pump();
    }

    private void requestPump() {
        if (!stopping && wakeup.request()) { submitPump(); }
    }

    private void submitPump() {
        try {
            // tell never invokes inline on main (unlike execute). managedBlock can poll it.
            // The single pre-bound closure contains this server root, never a packet/actor.
            server.tell(new TickTask(server.getTickCount(), queuedPump));
        } catch (RuntimeException | Error failure) {
            stopping = true;
            try {
                wakeup.retire();
                dispatcher.retireSlot();
                synchronized (entries) {
                    for (var entry : entries.values()) { entry.control.observationLost(); }
                }
            } catch (RuntimeException | Error secondary) { recordTerminalFailure(null, secondary); }
            throw failure;
        }
    }

    private void pumpQueued() {
        requireMain();
        if (!wakeup.beginQueued()) { return; }
        long nativeTick = Integer.toUnsignedLong(server.getTickCount());
        if (nativeTick < tick) { stop(); }
        else { tick = nativeTick; }
        pump();
    }

    private void pump() {
        Throwable primary = null;
        try {
            if (!stopping) { prepareAndDispatch(); }
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            // An arrival between poll(empty) and here must not lose its wakeup. Exhaustion
            // waits for the next real tick; reentrant pumps never create a second drain.
            try {
                if (wakeup.complete(!stopping && dispatcher.budgetRemaining(tick))) { submitPump(); }
            } catch (RuntimeException | Error secondary) {
                if (primary == null) { throw secondary; }
                recordTerminalFailure(null, secondary);
            }
        }
    }

    private void prepareAndDispatch() {
        List<Connection> newcomers;
        synchronized (entries) { newcomers = List.copyOf(starting.keySet()); }
        for (var connection : newcomers) {
            if (connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl config
                    && connection.isConnected()) {
                var existing = entry(connection);
                if (existing == null) { initializeConfiguration(config); }
                else {
                    existing.configurationStarting = true;
                    dispatcher.eligible(existing.member);
                }
            } else {
                synchronized (entries) {
                    if (Boolean.TRUE.equals(starting.remove(connection))) { waiting--; }
                }
            }
        }
        List<Entry> snapshot;
        synchronized (entries) { snapshot = List.copyOf(entries.values()); }
        for (var entry : snapshot) {
            if (!entry.connection.isConnected()) { retire(entry); continue; }
            var wait = entry.control.observeWait(now());
            if (wait == P11ControlBudgets.WaitResult.EXPIRED
                    || wait == P11ControlBudgets.WaitResult.CLOCK_UNAVAILABLE) {
                entry.closeReason = "gramarye.transition.expired";
            }
            if (entry.blockersChanged) {
                entry.blockersChanged = false;
                entry.retryCheck = true;
            }
            var state = entry.control.state().orElse(null);
            if (state != null && state.outcome() == Outcome.NOT_STARTED
                    && state.availability() == Availability.WAIT_NOTIFY) { entry.retryCheck = true; }
            if (entry.configurationTerminalPhase != 0
                    || entry.configurationStarting || entry.taskPending || entry.retryCheck
                    || entry.closeReason != null || entry.control.hasService(entry.terminalInterval == TerminalInterval.NONE)) {
                dispatcher.eligible(entry.member);
            }
        }
        for (var next = dispatcher.poll(tick); next.isPresent(); next = dispatcher.poll(tick)) {
            var dispatch = next.orElseThrow();
            Entry entry;
            synchronized (entries) { entry = epochs.get(dispatch.connectionId()); }
            boolean more = false;
            Throwable primary = null;
            try {
                if (entry != null && entry.connection.isConnected()) {
                    service(entry);
                    more = entry.configurationTerminalPhase != 0
                            || entry.configurationStarting || entry.taskPending || entry.retryCheck
                            || entry.closeReason != null || entry.control.hasService(entry.terminalInterval == TerminalInterval.NONE);
                }
            } catch (RuntimeException | Error failure) {
                primary = failure;
                throw failure;
            } finally {
                try { dispatcher.complete(dispatch, more); }
                catch (RuntimeException | Error secondary) {
                    // A failed queue publication is never usable progress. These retire calls
                    // only clear bounded scalar/reference state; no native observer is invoked.
                    stopping = true;
                    wakeup.retire();
                    dispatcher.retireSlot();
                    if (primary == null) { throw secondary; }
                    recordTerminalFailure(null, secondary);
                }
            }
        }
    }

    private void initializeConfiguration(ServerConfigurationPacketListenerImpl listener) {
        var connection = listener.getConnection();
        var entry = entry(connection);
        if (entry != null) {
            // A later, real task starts RETURN; it cannot supply the earlier ACK observation.
            var state = entry.control.state().orElseThrow();
            if (state.kind() == Kind.ENTER_CONFIG) {
                if (state.scope() != Scope.CONFIG || state.outcome() != Outcome.COMPLETED
                        || !entry.control.bindActorlessAfterConfiguration(entry.control.binding())
                        || !entry.control.openServerScene(Scope.CONFIG, Kind.RETURN_TO_WORLD)) {
                    entry.control.observationLost(); return;
                }
            }
            entry.taskPending = true;
            synchronized (entries) {
                starting.remove(connection);
                if (!entry.waiting) { throw new IllegalStateException("P11_WAIT_RESERVATION_MISSING"); }
            }
            dispatcher.eligible(entry.member);
            return;
        }
        var captured = identities.bindAuthenticatedActorless(listener);
        if (captured.isEmpty() && !connection.isConnected()) {
            // This exact native transport departed during binding. No identity/member
            // was installed; release only its existing initial waiting reservation.
            synchronized (entries) {
                if (Boolean.TRUE.equals(starting.remove(connection))) { waiting--; }
            }
            return;
        }
        var identity = captured.orElseThrow();
        var control = new P11TransitionControl(identity, limits, now());
        var member = dispatcher.register(identity.connectionEpoch()).orElseThrow();
        entry = new Entry(connection, identity.uuid(), control, member);
        entry.waiting = true;
        entry.taskPending = true;
        if (!control.openServerScene(Scope.CONFIG, Kind.JOIN)) {
            throw new IllegalStateException("P11_INITIAL_SCENE_UNAVAILABLE");
        }
        synchronized (entries) {
            starting.remove(connection);
            entries.put(connection, entry); epochs.put(identity.connectionEpoch(), entry);
        }
        // earlyTask publishes the initial PENDING in a real dispatcher quantum, before RUNNING.
        dispatcher.eligible(member);
    }

    private void service(Entry entry) {
        if (entry.configurationCapacityRejected) {
            // No CONFIG binding, task, retry, or source responsibility is accepted at K-full.
            if (entry.configurationTerminalPhase != 0) {
                entry.configurationTerminalPhase = 0;
                entry.control.retire();
                disconnect(entry, "gramarye.transition.capacity");
            }
            return;
        }
        if (entry.configurationTerminalPhase != 0) {
            completeConfigurationAcknowledgement(entry);
            return;
        }
        var logicalListener = entry.control.listener();
        if (entry.connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl
                && (logicalListener == null || logicalListener.scope() == Scope.PLAY)) {
            return; // The native pointer is published before setupInboundProtocol returns.
        }
        if (entry.closeReason != null) {
            notifyLatest(entry);
            var reason = entry.closeReason;
            entry.closeReason = null;
            disconnect(entry, reason);
            return;
        }
        if (entry.configurationStarting) {
            var state = entry.control.state().orElse(null);
            if (state != null && state.kind() == Kind.ENTER_CONFIG && entry.control.executableHeld()) {
                return; // A fast ACK/task cannot replace the original still-running Java caller.
            }
            entry.configurationStarting = false;
            if (entry.connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl config) {
                initializeConfiguration(config);
            } else { entry.control.observationLost(); }
            return;
        }
        if (entry.taskPending) {
            entry.taskPending = false;
            var drain = entry.control.reserveServerDrain(entry.control.listener()).orElse(null);
            if (drain != null) { earlyTask(entry, drain); }
            return;
        }
        if (entry.retryCheck && (!entry.control.hasService(entry.terminalInterval == TerminalInterval.NONE) || entry.preferRetryCheck)) {
            entry.retryCheck = false;
            entry.preferRetryCheck = false;
            entry.control.refreshRetryAvailability(entry.control.listener(),
                    gate(entry) == P11TransitionControl.Gate.ALLOW, now());
            return;
        }
        entry.preferRetryCheck = true;
        switch (entry.control.nextService(entry.terminalInterval == TerminalInterval.NONE)) {
            case NOTIFICATION -> notifyLatest(entry);
            case INBOX -> {
                var drain = entry.control.reserveDrain(entry.control.listener()).orElse(null);
                if (drain == null) { return; }
                if (drain.request().command() == Command.STATUS) {
                    entry.control.processStatus(drain);
                    entry.control.finishDrain(drain);
                } else if (entry.connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl
                        && !entry.control.configurationTaskPassed()) {
                    earlyTask(entry, drain);
                } else { execute(entry, drain); }
            }
            case NONE -> { }
        }
    }

    private void completeConfigurationAcknowledgement(Entry entry) {
        long phase;
        synchronized (entries) {
            phase = entry.configurationTerminalPhase;
            entry.configurationTerminalPhase = 0;
        }
        if (!(entry.connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl config)
                || config.getMainThreadEventLoop() != server || !entry.connection.isConnected()
                || !(config instanceof P11KeepAliveBoundary.CommonAccess access)
                || access.p11$keepAlivePhase() != phase || !entry.waiting) {
            entry.control.observationLost(); return;
        }
        var previous = entry.control.listener();
        if (previous == null || previous.scope() != Scope.PLAY) {
            entry.control.observationLost(); return;
        }
        var handoff = entry.control.beginHandoff(previous, Scope.CONFIG).orElse(null);
        if (handoff == null) { entry.control.observationLost(); return; }
        var captured = identities.bindAuthenticatedActorless(config);
        if (captured.isEmpty() && !entry.connection.isConnected()) {
            // Retirement is only a closed exact-C terminal, never an admission retry.
            retire(entry);
            return;
        }
        var bound = captured.orElseThrow();
        if (!entry.control.bindActorlessHandoff(handoff, bound)
                || entry.control.completeHandoff(handoff).isEmpty()) {
            entry.control.observationLost(); return;
        }
        // Native outbound CONFIG and this exact actorless handoff are both complete.
        if (entry.terminalInterval == TerminalInterval.TO_CONFIG) { entry.terminalInterval = TerminalInterval.NONE; }
        // ENTER_CONFIG is now independently terminal; no RETURN scene or native task is opened.
        notifyLatest(entry);
    }

    private void earlyTask(Entry entry, P11TransitionControl.Drain drain) {
        try {
            notifyLatest(entry);
            boolean pass = entry.control.passConfigurationTask(drain, gate(entry), now());
            if (!entry.control.finishDrain(drain) && !entry.control.abortAdmission(drain)) {
                entry.control.observationLost();
            }
            notifyLatest(entry);
            if (pass && entry.connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl config) {
                P11ConfigurationBoundary.completeTask(config);
            }
        } catch (RuntimeException | Error primary) {
            try {
                if (!entry.control.abortAdmission(drain)) { entry.control.observationLost(); }
            } catch (RuntimeException | Error secondary) { recordTerminalFailure(null, secondary); }
            throw primary;
        }
    }

    private P11TransitionControl.Gate gate(Entry entry) {
        ServerPlayer expected = entry.connection.getPacketListener() instanceof ServerGamePacketListenerImpl game
                ? game.player : null;
        return switch (sources.controlGate(entry.playerId, expected)) {
            case CLEAR, UNMANAGED -> applicable(entry, expected)
                    ? P11TransitionControl.Gate.ALLOW : P11TransitionControl.Gate.NOT_APPLICABLE;
            case ACTIVE_OPERATION -> P11TransitionControl.Gate.ACTIVE_OPERATION;
            case ACTIVE_CONTEXT -> P11TransitionControl.Gate.ACTIVE_CONTEXT;
            case ACTIVE_TRANSITION -> P11TransitionControl.Gate.ACTIVE_TRANSITION;
            case SOURCE_UNKNOWN -> { entry.control.observationLost(); yield P11TransitionControl.Gate.NOT_APPLICABLE; }
        };
    }

    private static boolean applicable(Entry entry, ServerPlayer actor) {
        var state = entry.control.state().orElse(null);
        if (state == null) { return false; }
        return switch (state.kind()) {
            case DEATH -> actor != null && actor.getHealth() <= 0 && !actor.wonGame;
            case END -> actor != null && actor.wonGame;
            case ENTER_CONFIG -> actor != null;
            case JOIN, RETURN_TO_WORLD -> actor == null;
        };
    }

    private Ticket admit(Entry entry, P11TransitionControl.Drain drain,
            ServerCommonPacketListenerImpl caller) {
        P11QualifiedSourceOwner.ControlCustody custody = null;
        try {
            var gate = gate(entry);
            var attempt = entry.control.begin(drain, gate, now()).orElse(null);
            if (attempt == null) {
                if (!entry.control.finishDrain(drain) && !entry.control.abortAdmission(drain)) {
                    entry.control.observationLost();
                }
                notifyLatest(entry); return null;
            }
            custody = sources.beginControlCustody(entry.playerId);
            return new Ticket(this, entry, caller, attempt, drain.request().kind(), custody);
        } catch (RuntimeException | Error primary) {
            try {
                if (!entry.control.abortAdmission(drain)) { entry.control.observationLost(); }
            } catch (RuntimeException | Error secondary) { recordTerminalFailure(null, secondary); }
            try { sources.closeControlCustody(custody); }
            catch (RuntimeException | Error secondary) { recordTerminalFailure(null, secondary); }
            throw primary;
        }
    }

    private void execute(Entry entry, P11TransitionControl.Drain drain) {
        if (!(entry.connection.getPacketListener() instanceof ServerCommonPacketListenerImpl listener)) {
            entry.control.submissionFailed(drain); return;
        }
        var ticket = admit(entry, drain, listener);
        if (ticket == null) { return; }
        var previous = call;
        boolean normal = false;
        Throwable primary = null;
        try {
            var scope = new Call(listener, previous);
            scope.ticket = ticket; call = scope;
            notifyLatest(entry);
            if (listener instanceof P11ParkingPacketListener parking) {
                P11ConfigurationBoundary.resume(parking);
                normal = true;
            } else if (listener instanceof ServerGamePacketListenerImpl game) {
                if (ticket.kind == Kind.ENTER_CONFIG) {
                    ((P11LivePlayAccess) game).p11$switchToConfig();
                    normal = true;
                } else {
                    var packet = new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN);
                    ticket.authorizedRespawnPacket = packet;
                    normal = runPacketBody(packet, game,
                            () -> ((P11LivePlayAccess) game).p11$performRespawn(packet));
                }
            } else { throw new IllegalStateException("P11_WRONG_NATIVE_CALLER"); }
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try { finish(ticket, normal, primary); }
            finally { call = previous; }
        }
    }

    void configurationFinished(ServerConfigurationPacketListenerImpl listener, Operation<Void> original,
            Object packet) {
        requireMain();
        var scope = new Call(listener, call); call = scope;
        boolean normal = false;
        Throwable primary = null;
        try { original.call(packet); normal = true; }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally {
            try { if (scope.ticket != null) { finish(scope.ticket, normal, primary); } }
            finally { call = scope.previous; }
        }
    }

    boolean beforeConfigurationFactory(ServerConfigurationPacketListenerImpl listener) {
        requireMain();
        var entry = requiredEntry(listener);
        if (call == null || call.caller != listener || call.ticket != null
                || !entry.control.configurationTaskPassed()) {
            throw new IllegalStateException("P11_CONFIGURATION_CALL_SCOPE_MISSING");
        }
        // Native ACK processing has already switched outbound to PLAY. Reserve PREPLAY now.
        entry.handoff = entry.control.beginHandoff(entry.control.listener(), Scope.PREPLAY).orElseThrow();
        entry.control.completeHandoff(entry.handoff).orElseThrow();
        entry.handoff = null;
        // Original outbound PLAY setup and platform/login checks precede this exact factory seam.
        if (entry.terminalInterval == TerminalInterval.TO_PLAY) { entry.terminalInterval = TerminalInterval.NONE; }
        var drain = entry.control.reserveServerDrain(entry.control.listener()).orElseThrow();
        call.ticket = admit(entry, drain, listener);
        return call.ticket != null;
    }

    void prepareParking(ServerConfigurationPacketListenerImpl previous, P11ParkingPacketListener next) {
        requireMain();
        var entry = requiredEntry(previous);
        if (next.getConnection() != previous.getConnection()
                || entry.control.listener().scope() != Scope.PREPLAY || call.ticket != null) {
            throw new IllegalStateException("P11_PARKING_RESERVATION_MISMATCH");
        }
    }

    void parkingInstalled(P11ParkingPacketListener parking) {
        requireMain();
        var entry = requiredEntry(parking);
        var identity = identities.bindParked(parking).orElseThrow();
        if (!entry.control.rebindParked(identity)) { throw new IllegalStateException("P11_PARKING_BINDING_MISMATCH"); }
        notifyLatest(entry);
    }

    void performRespawn(ServerGamePacketListenerImpl listener, ServerboundClientCommandPacket packet,
            Operation<Void> original) {
        requireMain();
        var ticket = current(listener);
        if (ticket == null || packet != ticket.authorizedRespawnPacket) { return; }
        // Only the named dispatcher invocation carries this exact private packet identity.
        if (ticket.nativeEntryConsumed || ticket.originalActor != listener.player
                || (ticket.kind != Kind.DEATH && ticket.kind != Kind.END)) {
            throw new IllegalStateException("P11_NATIVE_ENTRY_MISMATCH");
        }
        ticket.nativeEntryConsumed = true;
        original.call(packet);
    }

    void switchToConfig(ServerGamePacketListenerImpl listener, Operation<Void> original) {
        requireMain();
        var ticket = current(listener);
        if (ticket != null) {
            if (ticket.kind != Kind.ENTER_CONFIG || ticket.nativeEntryConsumed) {
                throw new IllegalStateException("P11_CONFIGURATION_ENTRY_MISMATCH");
            }
            ticket.nativeEntryConsumed = true; original.call(); return;
        }
        // Only an actual original server caller can establish this permission-preserving scene.
        var entry = requiredEntry(listener);
        if (!entry.control.openServerScene(Scope.PLAY, Kind.ENTER_CONFIG)) { return; }
        notifyLatest(entry); // Publish this original server request's PENDING before any RUNNING.
        var drain = entry.control.reserveServerDrain(entry.control.listener()).orElseThrow();
        ticket = admit(entry, drain, listener);
        if (ticket == null) { return; }
        var previous = call;
        boolean normal = false;
        Throwable primary = null;
        try {
            var scope = new Call(listener, previous); scope.ticket = ticket; call = scope;
            notifyLatest(entry);
            ticket.nativeEntryConsumed = true; original.call(); normal = true;
        } catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { try { finish(ticket, normal, primary); } finally { call = previous; } }
    }

    /** Read-only proof at the root's actual original ENTER_CONFIG body entry, never an admission grant. */
    boolean configurationTickRetirementAuthorized(ServerGamePacketListenerImpl listener) {
        if (stopping || !server.isSameThread() || listener.getMainThreadEventLoop() != server) { return false; }
        var ticket = current(listener);
        return ticket != null && ticket.owner == this && ticket.kind == Kind.ENTER_CONFIG
                && ticket.nativeEntryConsumed && ticket.originalActor == listener.player
                && listener.player.connection == listener && listener.player.getServer() == server
                && ticket.entry.connection == listener.getConnection()
                && entry(ticket.entry.connection) == ticket.entry
                && ticket.entry.connection.getPacketListener() == listener
                && ticket.entry.connection.isConnected();
    }

    Ticket current(ServerCommonPacketListenerImpl caller) {
        return server.isSameThread() && call != null && call.caller == caller
                && call.ticket != null && !call.ticket.closed ? call.ticket : null;
    }

    private Ticket active() {
        requireMain();
        if (call == null || call.ticket == null || call.ticket.closed) {
            throw new IllegalStateException("P11_NATIVE_TICKET_REQUIRED");
        }
        return call.ticket;
    }

    void requireFactory(PlayerList list, GameProfile profile, ClientInformation information) {
        var ticket = active();
        if (list != server.getPlayerList() || ticket.factoryConsumed || ticket.expectedActor != null
                || !profile.getId().equals(ticket.entry.playerId)
                || (ticket.kind != Kind.JOIN && ticket.kind != Kind.RETURN_TO_WORLD)) {
            throw new IllegalStateException("P11_FACTORY_TICKET_MISMATCH");
        }
        ticket.factoryConsumed = true;
    }

    void requireRespawn(PlayerList list, ServerPlayer actor, boolean keepEverything) {
        var ticket = active();
        if (list != server.getPlayerList() || ticket.respawnConsumed || actor != ticket.originalActor
                || (ticket.kind != Kind.DEATH && ticket.kind != Kind.END)
                || keepEverything != (ticket.kind == Kind.END)) {
            throw new IllegalStateException("P11_RESPAWN_TICKET_MISMATCH");
        }
        ticket.respawnConsumed = true;
    }

    void expectedActor(ServerPlayer actor) {
        var ticket = active();
        if ((!ticket.factoryConsumed && !ticket.respawnConsumed)
                || ticket.expectedActor != null || actor == ticket.originalActor || actor.getServer() != server
                || !actor.getUUID().equals(ticket.entry.playerId)) {
            throw new IllegalStateException("P11_EXPECTED_ACTOR_MISMATCH");
        }
        ticket.expectedActor = actor;
    }

    void expectedNativeFrame(Packet<?> packet) {
        var ticket = active();
        boolean valid = packet instanceof ClientboundLoginPacket
                ? ticket.factoryConsumed && (ticket.kind == Kind.JOIN || ticket.kind == Kind.RETURN_TO_WORLD)
                : packet instanceof ClientboundRespawnPacket && ticket.respawnConsumed
                    && (ticket.kind == Kind.DEATH || ticket.kind == Kind.END);
        if (!valid || ticket.expectedFrame != null || ticket.expectedActor == null) {
            throw new IllegalStateException("P11_FRAME_PRODUCER_MISMATCH");
        }
        ticket.expectedFrame = packet;
    }

    boolean adoptSourceBody(P11QualifiedSourceOwner.Body body, P11QualifiedSourceOwner owner) {
        var ticket = active();
        if (owner != sources || body.actor != ticket.expectedActor) { return false; }
        if (ticket.custody == null) { ticket.custody = sources.attachControlCustody(body); }
        return true;
    }

    void nativeSend(ServerCommonPacketListenerImpl listener, Packet<?> packet, Operation<Void> original) {
        // JoinWorld's original terminal send has no admitted body ticket. Mark before scheduling
        // its native write, not by sampling the encoder while that write may still be queued.
        if (!stopping && server.isSameThread() && packet instanceof ClientboundFinishConfigurationPacket
                && listener instanceof ServerConfigurationPacketListenerImpl
                && listener.getMainThreadEventLoop() == server
                && listener.getConnection().getPacketListener() == listener
                && listener instanceof P11ConfigurationBoundary.Access access
                && net.minecraft.server.network.config.JoinWorldTask.TYPE.equals(access.p11$currentConfigurationTask())) {
            var entry = entry(listener.getConnection());
            if (entry != null && entry.control.configurationTaskPassed()
                    && entry.control.listener() != null && entry.control.listener().scope() == Scope.CONFIG) {
                entry.terminalInterval = TerminalInterval.TO_PLAY;
            }
        }
        if (!server.isSameThread() || call == null || call.ticket == null || call.ticket.closed) {
            original.call(packet); return;
        }
        var ticket = call.ticket;
        if (listener.getConnection() != ticket.entry.connection) { original.call(packet); return; }
        if (packet instanceof ClientboundStartConfigurationPacket && ticket.kind == Kind.ENTER_CONFIG) {
            if (listener == ticket.caller && ticket.entry.connection.getPacketListener() == listener) {
                ticket.entry.terminalInterval = TerminalInterval.TO_CONFIG;
            }
            original.call(packet);
            ticket.entry.control.startConfigurationObserved(ticket.attempt);
            return;
        }
        if (packet != ticket.expectedFrame) { original.call(packet); return; }
        if (ticket.expectedActor == null || ticket.expectedIdentity != null) {
            throw new IllegalStateException("P11_NATIVE_FRAME_MISMATCH");
        }
        var identity = identities.bindExpectedActor(ticket.entry.control.binding(), ticket.expectedActor).orElseThrow();
        ticket.expectedIdentity = identity;
        if (!ticket.entry.control.nativeFrame(ticket.attempt, identity)) {
            throw new IllegalStateException("P11_NATIVE_FRAME_UNOBSERVED");
        }
        var marker = ticket.entry.control.takeNotification(ticket.entry.control.listener()).orElseThrow();
        @SuppressWarnings("unchecked")
        var nativePacket = (Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>) packet;
        original.call(new ClientboundBundlePacket(List.of(
                new ClientboundCustomPayloadPacket(new P11TransitionStatePayload(marker)), nativePacket)));
    }

    private void finish(Ticket ticket, boolean normal, Throwable primary) {
        if (ticket.closed) { return; }
        Throwable terminalFailure = null;
        try {
            boolean complete = normal && ticket.entry.connection.isConnected();
            if (ticket.kind == Kind.ENTER_CONFIG) {
                complete &= ticket.nativeEntryConsumed;
            } else {
                var actor = ticket.expectedActor;
                complete &= actor != null && ticket.expectedIdentity != null
                        && identities.liveCurrent(ticket.expectedIdentity)
                        && server.getPlayerList().getPlayer(ticket.entry.playerId) == actor;
                if (complete && sources.hasAccount(ticket.entry.playerId)) {
                    var body = sources.body(actor);
                    complete = body != null && sources.canCopy(body);
                }
            }
            if (complete) {
                boolean recorded = ticket.entry.control.callerCompleted(ticket.attempt,
                        ticket.expectedIdentity == null ? 0 : ticket.expectedIdentity.actorGeneration());
                if (!recorded) {
                    ticket.entry.control.observationLost();
                } else if (ticket.kind != Kind.ENTER_CONFIG) {
                    var state = ticket.entry.control.state().orElseThrow();
                    if (state.outcome() != Outcome.COMPLETED
                            || state.targetActorGeneration() != ticket.expectedIdentity.actorGeneration()) {
                        throw new IllegalStateException("P11_CALLER_TERMINAL_NOT_COMPLETED");
                    }
                    if (ticket.entry.control.listener().scope() == Scope.PREPLAY) {
                        var handoff = ticket.entry.control.beginHandoff(ticket.entry.control.listener(), Scope.PLAY).orElseThrow();
                        ticket.entry.control.completeHandoff(handoff).orElseThrow();
                    }
                    synchronized (entries) {
                        if (ticket.entry.waiting) { ticket.entry.waiting = false; waiting--; }
                    }
                }
            } else { ticket.entry.control.fault(ticket.attempt, Reason.NATIVE_FAILURE); }
        } catch (RuntimeException | Error failure) {
            terminalFailure = recordTerminalFailure(terminalFailure, failure);
            try { ticket.entry.control.observationLost(); }
            catch (RuntimeException | Error secondary) {
                terminalFailure = recordTerminalFailure(terminalFailure, secondary);
            }
        } finally {
            ticket.closed = true;
            try { sources.closeControlCustody(ticket.custody); }
            catch (RuntimeException | Error failure) {
                terminalFailure = recordTerminalFailure(terminalFailure, failure);
            }
            try {
                if (!ticket.entry.control.releaseCompletedDrain()) { ticket.entry.control.observationLost(); }
            } catch (RuntimeException | Error failure) {
                terminalFailure = recordTerminalFailure(terminalFailure, failure);
            }
            try { dispatcher.eligible(ticket.entry.member); requestPump(); }
            catch (RuntimeException | Error failure) {
                terminalFailure = recordTerminalFailure(terminalFailure, failure);
            }
        }
        // Secondary observers never replace a native primary. With no primary, the first
        // terminal failure is propagated; all later failures still increment the bounded count.
        if (terminalFailure != null) {
            try { ticket.entry.control.observationLost(); }
            catch (RuntimeException | Error failure) {
                terminalFailure = recordTerminalFailure(terminalFailure, failure);
            }
        }
        if (primary == null && terminalFailure != null) {
            if (terminalFailure instanceof RuntimeException runtime) { throw runtime; }
            throw (Error) terminalFailure;
        }
    }

    private Throwable recordTerminalFailure(Throwable first, Throwable failure) {
        if (terminalFailures != Long.MAX_VALUE) { terminalFailures++; }
        return first == null ? failure : first;
    }

    long terminalFailureCount() { return terminalFailures; }

    /** Pinned PacketUtils scheduled-handler policy; raw Error deliberately is not caught. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean runPacketBody(Packet packet, net.minecraft.network.PacketListener listener, Runnable body) {
        try { body.run(); return true; }
        catch (Exception failure) {
            if (failure instanceof net.minecraft.ReportedException reported
                    && reported.getCause() instanceof OutOfMemoryError) {
                throw net.minecraft.network.protocol.PacketUtils.makeReportedException(failure, packet, listener);
            }
            listener.onPacketError(packet, failure);
            return false;
        }
    }

    void publish(ServerPlayer actor, Kind kind) {
        requireMain();
        var entry = actor.connection == null ? null : entry(actor.connection.getConnection());
        if (entry == null || stopping) { return; }
        var identity = identities.exactControlActor(actor, entry.connection).orElse(null);
        if (identity == null) { entry.control.observationLost(); return; }
        if (!entry.control.openPlayScene(identity, kind)) {
            if (entry.control.retainSuccessor(identity, kind)) {
                var binding = entry.control.successorBinding().orElseThrow();
                actor.connection.send(new ClientboundCustomPayloadPacket(new P11TransitionStatePayload(binding)));
                return; // The old current receipt remains held; this scene is non-executable.
            } else { entry.control.observationLost(); }
        }
        notifyLatest(entry); // Original CombatKill / WIN_GAME send follows this call.
    }

    void blockersChanged(UUID playerId) {
        if (!server.isSameThread() || stopping) { return; }
        boolean changed = false;
        synchronized (entries) {
            for (var entry : entries.values()) {
                if (entry.playerId.equals(playerId)) { entry.blockersChanged = true; changed = true; }
            }
        }
        if (changed) { requestPump(); }
    }

    private void notifyLatest(Entry entry) {
        // Keep the latest notification for the validated next phase; never send across a terminal gap.
        if (entry.terminalInterval != TerminalInterval.NONE) { return; }
        if (!(entry.connection.getPacketListener() instanceof ServerCommonPacketListenerImpl listener)) { return; }
        var logicalListener = entry.control.listener();
        if (listener instanceof ServerConfigurationPacketListenerImpl
                && (logicalListener == null || logicalListener.scope() == Scope.PLAY)) { return; }
        var state = entry.control.takeNotification(logicalListener);
        state.ifPresent(value -> {
            listener.send(new ClientboundCustomPayloadPacket(new P11TransitionStatePayload(value)));
            if (value.outcome() == Outcome.COMPLETED && entry.control.activateSuccessor()) {
                dispatcher.eligible(entry.member);
                requestPump();
            }
        });
    }

    private Entry requiredEntry(ServerCommonPacketListenerImpl listener) {
        var entry = entry(listener.getConnection());
        if (entry == null || listener.getMainThreadEventLoop() != server
                || listener.getConnection().getPacketListener() != listener) {
            throw new IllegalStateException("P11_EXACT_CONTROL_BINDING_REQUIRED");
        }
        return entry;
    }

    private void disconnect(Entry entry, String reason) {
        if (entry.connection.getPacketListener() instanceof ServerCommonPacketListenerImpl listener) {
            listener.disconnect(Component.translatable(reason));
        }
    }

    private void retire(Entry entry) {
        var identity = entry.control.binding();
        entry.control.retire();
        dispatcher.retire(entry.member);
        if (identity != null) { identities.retireConnection(identity); }
        synchronized (entries) {
            entries.remove(entry.connection); epochs.remove(entry.member.connectionId());
            if (entry.waiting) { entry.waiting = false; waiting--; }
        }
    }

    void stop() {
        requireMain(); stopping = true;
        wakeup.retire();
        List<Entry> snapshot;
        synchronized (entries) { starting.clear(); snapshot = List.copyOf(entries.values()); }
        for (var entry : snapshot) { entry.control.retire(); }
        dispatcher.retireSlot();
        // Identity/data custody is retired by the existing foundation after original native saves.
    }

    private void requireMain() {
        if (!server.isSameThread()) { throw new IllegalStateException("P11_CONTROL_WRONG_THREAD"); }
    }
}
