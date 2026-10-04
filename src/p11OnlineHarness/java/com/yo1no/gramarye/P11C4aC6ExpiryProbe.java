package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/** Actual C6 expiry and native departure. No client receipt is an execution prerequisite. */
public final class P11C4aC6ExpiryProbe {
    private static Run active;
    private P11C4aC6ExpiryProbe() { }

    static void start(MinecraftServer server, Connection connection, Path output) {
        P11C4aEvidence.require(active == null && server.isSameThread(), "C6_EXPIRY_OBSERVER_OWNER");
        active = new Run(server, connection, output);
    }

    static void waited(MinecraftServer server, Connection connection, Object wait, boolean refusal,
            long now, Object result, P11C4aC6NativeProbe.WaitSample sample) {
        var run = active;
        if (run == null || run.server != server || run.connection != connection) { return; }
        try {
            if (!server.isSameThread()) { fail(run, "C6_EXPIRY_WAIT_THREAD"); return; }
            if (refusal && run.firstWait == null) { run.firstWait = wait; run.first = sample; }
            if (result == P11ControlBudgets.WaitResult.EXPIRED && run.terminalWait == null) {
                run.terminalWait = wait; run.terminal = sample; run.expiredAt = now;
            }
        } catch (RuntimeException | Error secondary) { fail(run, "C6_EXPIRY_WAIT_OBSERVER"); }
    }

    /** The exact original Common.disconnect invocation, not merely a close request or elapsed time. */
    public static void disconnecting(MinecraftServer server, Object service, Object suppliedEntry,
            String suppliedReason, ServerCommonPacketListenerImpl listener) {
        var run = active;
        if (run == null || run.server != server) { return; }
        try {
            if (listener.getConnection() != run.connection) { return; }
            run.entries++;
            var entry = (P11C4aC6NativeProbe.EntryView) suppliedEntry;
            var control = (P11TransitionControl) entry.c6$control();
            var state = control.state().orElse(null);
            if (!server.isSameThread() || run.entries != 1 || !run.connection.isConnected()
                    || listener.getMainThreadEventLoop() != server || run.connection.getPacketListener() != listener
                    || entry.c6$connection() != run.connection || !entry.c6$waiting()
                    || !"gramarye.transition.expired".equals(suppliedReason)
                    || !validExpiry(run.firstWait, run.terminalWait, run.first, run.terminal, run.expiredAt, state)
                    || ((P11C4aC6NativeProbe.ControlView) (Object) control).c6$wait() != run.terminalWait) {
                fail(run, "C6_EXPIRY_ORIGINAL_DISCONNECT_NOT_EXACT");
            }
            run.service = service; run.entry = suppliedEntry; run.state = state;
            run.expiryReasonAtOriginalInvocation = "gramarye.transition.expired".equals(suppliedReason);
            write(run, "c6-expiry-disconnect-entry.json", "ORIGINAL_EXPIRY_DISCONNECT_INVOCATION_NOT_TERMINAL");
        } catch (Exception | Error secondary) { fail(run, "C6_EXPIRY_DISCONNECT_ENTRY_OBSERVER"); }
    }

    public static void disconnected(MinecraftServer server, Object suppliedEntry, boolean normal) {
        var run = active;
        if (run == null || run.server != server || suppliedEntry != run.entry) { return; }
        try {
            if (normal) { run.returns++; } else { fail(run, "C6_EXPIRY_ORIGINAL_DISCONNECT_THROW"); }
            seal(run);
        } catch (Exception | Error secondary) { fail(run, "C6_EXPIRY_DISCONNECT_RETURN_OBSERVER"); }
    }

    /** Called after the real retire body and after releasing its entries monitor. */
    public static void retired(MinecraftServer server, Object service, Object suppliedEntry,
            int waiting, boolean exactEntryPresent) {
        var run = active;
        if (run == null || run.server != server || suppliedEntry != run.entry) { return; }
        try {
            run.retireReturns++;
            run.waitingAfter = waiting; run.entryPresentAfter = exactEntryPresent;
            run.closedAtRetire = !run.connection.isConnected();
            if (!server.isSameThread() || service != run.service || run.retireReturns != 1
                    || !run.closedAtRetire || exactEntryPresent || waiting != 0
                    || ((P11C4aC6NativeProbe.EntryView) suppliedEntry).c6$waiting()) {
                fail(run, "C6_EXPIRY_RETIRE_NOT_EXACT_CLOSED_K_RELEASE");
            }
            seal(run);
        } catch (Exception | Error secondary) { fail(run, "C6_EXPIRY_RETIRE_OBSERVER"); }
    }

    static boolean terminal() { var run = active; return run != null && run.sealed && run.failure == null; }
    static void release() { active = null; }

    // Direct fixture tests exercise only this immutable predicate; they cannot mint runtime evidence.
    static boolean validExpiry(Object firstWait, Object terminalWait, P11C4aC6NativeProbe.WaitSample first,
            P11C4aC6NativeProbe.WaitSample terminal, long observedAt, State state) {
        return firstWait != null && firstWait == terminalWait && first != null && terminal != null
                && first.started() && !first.expired() && !first.unavailable()
                && terminal.started() && terminal.expired() && !terminal.unavailable()
                && first.start() >= 0 && first.start() == terminal.start() && first.timeout() == 45_000 && terminal.timeout() == 45_000
                && observedAt >= first.start() && observedAt - first.start() >= 45_000
                && terminal.last() == observedAt && state != null && state.scope() == Scope.PREPLAY
                && state.kind() == Kind.RETURN_TO_WORLD && state.actorGeneration() == 0
                && state.outcome() == Outcome.EXPIRED && state.reason() == Reason.WAIT_EXPIRED
                && state.availability() == Availability.DISABLED && state.targetActorGeneration() == 0;
    }

    private static void seal(Run run) throws java.io.IOException {
        if (run.sealed || run.returns != 1 || run.retireReturns != 1) { return; }
        write(run, "c6-expiry-server-terminal.json", run.failure == null
                ? "ACTUAL_ORIGINAL_EXPIRY_DISCONNECT_CLOSED_AND_K_RETIRED"
                : "INCOMPLETE_EXPIRY_TERMINAL_OBSERVATIONS");
        run.sealed = true;
    }
    private static void write(Run run, String leaf, String status) throws java.io.IOException {
        var values = new LinkedHashMap<String, Object>();
        values.put("status", status); values.put("failure", run.failure == null ? "NONE" : run.failure);
        values.put("firstRefusal", run.first); values.put("actualExpiredWait", run.terminal);
        values.put("sameOriginalWaitObject", run.firstWait != null && run.firstWait == run.terminalWait);
        values.put("actualExpiredAt", run.expiredAt); values.put("actualControlState", run.state);
        values.put("originalReasonWasAdmissionExpiry", run.expiryReasonAtOriginalInvocation);
        values.put("originalDisconnectEntries", run.entries); values.put("originalDisconnectReturns", run.returns);
        values.put("originalRetireReturns", run.retireReturns); values.put("exactConnectionClosedAtRetire", run.closedAtRetire);
        values.put("exactEntryPresentAfterRetire", run.entryPresentAfter); values.put("actualKAfterRetire", run.waitingAfter);
        values.put("clientReceiptUsedAsServerProof", false); values.put("dataRootsReleasedClaimed", false);
        values.put("fullC4aAcceptance", false);
        P11C4aEvidence.write(run.output, leaf, values);
    }
    private static void fail(Run run, String code) { if (run.failure == null) { run.failure = code; } }
    private static final class Run {
        final MinecraftServer server; final Connection connection; final Path output;
        Object service, entry, firstWait, terminalWait;
        P11C4aC6NativeProbe.WaitSample first, terminal; State state;
        long expiredAt = -1; int entries, returns, retireReturns, waitingAfter = -1;
        boolean expiryReasonAtOriginalInvocation, closedAtRetire, entryPresentAfter = true, sealed;
        String failure;
        Run(MinecraftServer server, Connection connection, Path output) { this.server = server; this.connection = connection; this.output = output; }
    }
}
