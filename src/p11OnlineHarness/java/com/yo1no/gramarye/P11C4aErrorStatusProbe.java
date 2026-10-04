package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Excluded error-terminal STATUS experiment. Never manufactures a control record or ticket. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aErrorStatusProbe {
    private static volatile Window active;
    private static final ThreadLocal<Ingress> INGRESS = new ThreadLocal<>();
    private P11C4aErrorStatusProbe() { }

    static boolean selected(P11C4aNativeErrorProbe.Mode mode) {
        return mode == P11C4aNativeErrorProbe.Mode.CALLER_TAIL
                || mode == P11C4aNativeErrorProbe.Mode.PRIMARY_SECONDARY;
    }

    /** Called only AFTER existing primary/H0/terminal checks, before the actual native normal return or identical raw primary rethrow. */
    static boolean run(MinecraftServer server, ServerPlayer actor, Path output,
            P11C4aNativeErrorProbe.Mode mode, P11TransitionControl control, Request request,
            State terminal, P11LiveTransitionService service, Throwable primary) {
        Window window = null;
        try {
            require(selected(mode) && active == null && server.isSameThread() && (mode == P11C4aNativeErrorProbe.Mode.CALLER_TAIL
                        ? primary == null : primary instanceof Error)
                    && !Thread.holdsLock(control) && terminal.equals(control.state().orElse(null))
                    && terminal.outcome() == (mode == P11C4aNativeErrorProbe.Mode.CALLER_TAIL ? Outcome.FAULT : Outcome.UNKNOWN)
                    && terminal.availability() == Availability.DISABLED
                    && request.requestSeq() > 0 && request.requestSeq() < Long.MAX_VALUE,
                    "ERROR_STATUS_NOT_REAL_TERMINAL");
            window = new Window(server, actor, output, control, request, terminal, service);
            require(window.source.canCopy(window.body) && window.body.account.nativeCounts[
                    P11ControlBudgets.Root.TRANSITION.ordinal()] == 0, "ERROR_STATUS_CUSTODY_NOT_RELEASED");
            active = window;
            window.reloadActive = true;
            window.gate.orTimeout(30, TimeUnit.SECONDS);
            server.reloadResources(List.copyOf(server.getPackRepository().getSelectedIds())).join();
            window.reloadReturned = true;
            require(window.failure == null && window.released && window.sourceCompared
                    && window.same >= 1 && window.different == 1 && window.registrations == 1
                    && window.applications == 1 && window.managedEntries == 1 && window.managedReturns == 1,
                    "ERROR_STATUS_RELOAD_WITHOUT_REAL_NEGATIVES");
            unchangedControl(window);
            write(window, "ACTUAL_TERMINAL_STATUS_CLOSED_NOT_FULL_ACCEPTANCE");
            return true;
        } catch (IOException | RuntimeException | Error secondary) {
            if (window != null) {
                fail(window, "ERROR_STATUS_OBSERVER_OR_RELOAD_FAILED");
                try { write(window, "FAIL_ORIGINAL_NATIVE_RETURN_OR_RAW_PRIMARY_PRESERVED"); }
                catch (IOException | RuntimeException | Error ignored) { /* Preserve the original normal return or raw primary; this episode remains failed. */ }
            }
            return false;
        } finally {
            if (window != null) { window.reloadActive = false; }
            active = null;
        }
    }

    @SubscribeEvent static void registration(AddReloadListenerEvent event) {
        var w = active;
        if (w == null || !w.reloadActive) { return; }
        synchronized (w) {
            if (active != w || !w.reloadActive) { return; }
            require(++w.registrations == 1, "ERROR_STATUS_DUPLICATE_REGISTRATION");
        }
        // Actual native background registration; no event call or I/O under the scalar monitor.
        event.addListener(new PreparableReloadListener() {
            @Override public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                    ProfilerFiller preparation, ProfilerFiller application,
                    java.util.concurrent.Executor background, java.util.concurrent.Executor game) {
                return barrier.wait(Boolean.TRUE).thenComposeAsync(ignored -> {
                    require(active == w && w.reloadActive && w.server.isSameThread() && w.inManaged
                            && ++w.applications == 1, "ERROR_STATUS_APPLY_OWNER");
                    unchangedControl(w);
                    w.sourceAtGate = sourceFacts(w);
                    w.nativeAtGate = nativeFacts(w);
                    w.waiting = true;
                    try { P11C4aEvidence.cue(w.output, "a-error-status-window.ready"); }
                    catch (IOException failure) { fail(w, "ERROR_STATUS_CUE_FAILED"); w.gate.completeExceptionally(failure); }
                    return w.gate.thenRunAsync(() -> {
                        // Main-thread source observation before this listener's real reload stage completes.
                        require(w.server.isSameThread() && w.released && w.failure == null,
                                "ERROR_STATUS_RELEASE_WITHOUT_INGRESS");
                        unchangedControl(w);
                        w.sourceAfterIngress = sourceFacts(w);
                        w.nativeAfterIngress = nativeFacts(w);
                        require(w.sourceAtGate.equals(w.sourceAfterIngress)
                                && w.nativeAtGate.equals(w.nativeAfterIngress), "ERROR_STATUS_MUTATED_SOURCE_OR_NATIVE_FACTS");
                        w.sourceCompared = true;
                    }, game);
                }, game);
            }
        });
    }

    public static void managed(MinecraftServer server, boolean entering, boolean normal) {
        var w = active;
        if (w == null || w.server != server || !w.reloadActive) { return; }
        if (entering) { w.managedEntries++; w.inManaged = true; }
        else { w.inManaged = false; if (normal) { w.managedReturns++; } }
    }

    public static Object beginIngress(Object value, Connection connection, ICommonPacketListener listener) {
        var w = active;
        if (w == null || !w.waiting || w.released || connection != w.connection || !(value instanceof Request request)) { return null; }
        var scope = new Ingress(w, request, INGRESS.get());
        INGRESS.set(scope);
        if (connection.getPacketListener() != listener || listener.getConnection() != connection
                || !sameScene(w.request, request) || request.command() != Command.STATUS) {
            fail(w, "ERROR_STATUS_WRONG_INGRESS");
        }
        return scope;
    }

    public static Object beforeOffer(Object owner, Object request) {
        var scope = INGRESS.get();
        if (scope == null || scope.request != request) { return null; }
        if (scope.window.control != owner) { fail(scope.window, "ERROR_STATUS_WRONG_CONTROL"); return null; }
        return scope;
    }

    /** Scalar-only observation; no gate completion or I/O while original offer owns its monitor. */
    public static void afterOffer(Object value, Object result) {
        if (!(value instanceof Ingress scope)) { return; }
        var w = scope.window;
        try {
            unchangedControl(w);
            require(result == P11TransitionControl.Offer.CLOSED && !scope.offered,
                    "ERROR_STATUS_NOT_CLOSED_OR_DOUBLE_OFFER");
            scope.offered = true;
            if (scope.request.requestSeq() == w.request.requestSeq()) { require(++w.same <= 40, "ERROR_STATUS_EXCESS_SAME"); }
            else { require(scope.request.requestSeq() == w.request.requestSeq() + 1 && ++w.different == 1,
                    "ERROR_STATUS_WRONG_DIFFERENT_SEQUENCE"); }
        } catch (RuntimeException | Error secondary) { fail(w, "ERROR_STATUS_OFFER_OBSERVER_FAILED"); }
    }

    public static void endIngress(Object value, Throwable primary) {
        if (!(value instanceof Ingress scope)) { return; }
        var w = scope.window;
        try {
            if (scope.previous == null) { INGRESS.remove(); } else { INGRESS.set(scope.previous); }
            require(primary == null && scope.offered && !Thread.holdsLock(w.control),
                    "ERROR_STATUS_ORIGINAL_INGRESS_NOT_NORMAL");
            unchangedControl(w);
            if (w.failure != null) { throw new IllegalStateException(w.failure); }
            if (w.same >= 1 && w.different == 1 && !w.released) {
                w.released = true;
                require(w.gate.complete(null), "ERROR_STATUS_GATE_ALREADY_FINISHED");
            }
        } catch (RuntimeException | Error secondary) {
            fail(w, "ERROR_STATUS_INGRESS_FINALLY_FAILED");
            w.gate.completeExceptionally(secondary);
        }
    }

    private static void unchangedControl(Window w) {
        require(w.terminal.equals(w.control.state().orElse(null)) && w.control.fence() == w.fence
                && w.control.executableHeld() == w.held, "ERROR_STATUS_CHANGED_RECORD_FENCE_OR_HELD");
    }
    private static SourceFacts sourceFacts(Window w) {
        require(w.server.isSameThread() && w.actor.connection.getConnection() == w.connection
                && w.server.getPlayerList().getPlayer(w.actor.getUUID()) == w.actor
                && w.source.body(w.actor) == w.body && w.body.account.current == w.body
                && w.source.canCopy(w.body), "ERROR_STATUS_SOURCE_OWNER_CHANGED");
        return new SourceFacts(w.body.source.epoch(), w.body.source.version(), w.body.complete,
                w.body.account.candidate != null, w.body.fault.name(),
                Arrays.stream(w.body.account.nativeCounts).boxed().toList(), w.service.terminalFailureCount());
    }
    private static List<Long> nativeFacts(Window w) {
        return List.of(P11C4aNativeObservations.tries(w.connection),
                P11C4aNativeObservations.count(w.connection, P11C4aNativeObservations.Event.RESPAWN_TICKET_CHECKED),
                P11C4aNativeObservations.count(w.connection, P11C4aNativeObservations.Event.RESPAWN_CALL_ENTER),
                P11C4aNativeObservations.count(w.connection, P11C4aNativeObservations.Event.RESPAWN_FRAME_PRODUCED),
                P11C4aNativeObservations.count(w.connection, P11C4aNativeObservations.Event.RESPAWN_SEND_RETURN),
                P11C4aNativeObservations.count(w.connection, P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED));
    }
    private static boolean sameScene(Request a, Request b) {
        return a.scope() == b.scope() && a.connectionEpoch() == b.connectionEpoch()
                && a.sceneSerial() == b.sceneSerial() && a.actorGeneration() == b.actorGeneration() && a.kind() == b.kind();
    }
    private static void write(Window w, String status) throws IOException {
        var map = new LinkedHashMap<String,Object>();
        map.put("status", status); map.put("failure", w.failure == null ? "NONE" : w.failure);
        map.put("actualTerminal", w.terminal); map.put("actualRequest", w.request);
        map.put("nativeContext", w.terminal.outcome() == Outcome.FAULT
                ? "SUPPRESSED_EXCEPTION_NORMAL_RETURN" : "RAW_PRIMARY_ESCAPING");
        map.put("sameSequenceClosed", w.same); map.put("differentSequenceClosed", w.different);
        map.put("fenceBefore", w.fence); map.put("fenceAfter", w.control.fence());
        map.put("heldBefore", w.held); map.put("heldAfter", w.control.executableHeld());
        map.put("sourceBeforeStatus", w.sourceAtGate); map.put("sourceAfterStatus", w.sourceAfterIngress);
        map.put("nativeBeforeStatus", w.nativeAtGate); map.put("nativeAfterStatus", w.nativeAfterIngress);
        map.put("nativeCounterOrder", List.of("TRY", "RESPAWN_TICKET", "RESPAWN_CALL", "FRAME_PRODUCED", "FRAME_SEND", "FACTORY_TICKET"));
        map.put("managedEntries", w.managedEntries); map.put("managedNormalReturns", w.managedReturns);
        map.put("reloadRegistrations", w.registrations); map.put("reloadApplications", w.applications);
        map.put("gateReleasedAfterOriginalNetworkIngress", w.released); map.put("reloadReturned", w.reloadReturned);
        map.put("sourceInterval", "REAL_APPLY_GATE_TO_LISTENER_CONTINUATION_BEFORE_RELOAD_FINISH");
        map.put("outerNativePrimaryReceiverProvedHere", false); map.put("fullC4aAcceptance", false);
        P11C4aEvidence.write(w.output, "native-error-status-server.json", map);
    }
    private static void fail(Window w, String code) { if (w.failure == null) { w.failure = code; } }
    private static void require(boolean ok, String code) { P11C4aEvidence.require(ok, code); }
    private record SourceFacts(long epoch, long version, boolean complete, boolean candidate,
            String fault, List<Long> nativeRoots, long terminalFailures) { }
    private static final class Ingress {
        final Window window; final Request request; final Ingress previous; boolean offered;
        Ingress(Window w, Request r, Ingress p) { window=w; request=r; previous=p; }
    }
    private static final class Window {
        final MinecraftServer server; final ServerPlayer actor; final Connection connection; final Path output;
        final P11TransitionControl control; final Request request; final State terminal; final long fence;
        final boolean held; final P11LiveTransitionService service; final P11QualifiedSourceOwner source;
        final P11QualifiedSourceOwner.Body body; final CompletableFuture<Void> gate = new CompletableFuture<>();
        volatile String failure; volatile boolean reloadActive, waiting, released;
        volatile int same, different, registrations;
        boolean inManaged, reloadReturned, sourceCompared; int managedEntries, managedReturns, applications;
        SourceFacts sourceAtGate, sourceAfterIngress; List<Long> nativeAtGate, nativeAfterIngress;
        Window(MinecraftServer s, ServerPlayer a, Path o, P11TransitionControl c, Request r,
                State t, P11LiveTransitionService live) {
            server=s; actor=a; output=o; connection=a.connection.getConnection(); control=c; request=r; terminal=t;
            fence=c.fence(); held=c.executableHeld(); service=live;
            source=P11NativeStorageBoundary.nativeSourceOwner(a); body=source == null ? null : source.body(a);
            require(body != null, "ERROR_STATUS_SOURCE_ABSENT");
        }
    }
}
