package com.yo1no.gramarye;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.Util;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Excluded, one-run scheduling variant of the existing real parking producer.
 * No keepalive field, challenge, clock, protocol or listener is created or written here.
 */
public final class P11C4aParkingTransferProbe {
    private static final long GATE_SECONDS = 5;
    private static volatile Run active;
    private P11C4aParkingTransferProbe() { }

    /** Read-only native fields; implemented only by the excluded Common accessor mixin. */
    public interface Fields {
        long c4a$time();
        boolean c4a$pending();
        long c4a$challenge();
        int c4a$latency();
    }
    /** Read-only actual channel state, not a transport setter. */
    public interface Transport {
        boolean c4a$autoRead();
        Object c4a$decoder();
    }
    public interface LockObservation { boolean c4a$heldByCurrentThread(); }

    private record Frame(long time, boolean pending, long challenge, int latency, long phase) { }

    static void start(MinecraftServer server, Connection connection) {
        require(P11C4aEvidence.enabled() && active == null && server.isSameThread()
                && connection.isConnected() && connection.isEncrypted() && !connection.isMemoryConnection(),
                "TRANSFER_START_OWNER");
        active = new Run(server, connection);
    }

    /** Original native keepalive send HEAD; its four fields were already assigned by vanilla. */
    public static void challenge(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection
                || !(listener instanceof P11ParkingPacketListener parking)
                || !(packet instanceof ClientboundKeepAlivePacket keepAlive)) { return; }
        require(run.server.isSameThread() && run.connection.getPacketListener() == parking
                && unlocked(run), "TRANSFER_CHALLENGE_NOT_CURRENT_OR_LOCKED");
        Frame frame = frame(parking);
        require(run.captures == 0 && !run.captureArmed && frame.pending && frame.phase > 0 && frame.challenge == keepAlive.getId()
                && frame.time == frame.challenge && ((Transport) run.connection).c4a$autoRead(),
                "TRANSFER_NOT_FIRST_REAL_PENDING_CHALLENGE");
        run.parking = parking;
        run.challenge = frame;
        run.challengeAtNanos = System.nanoTime();
    }

    static boolean readyForRetry() {
        var run = active;
        if (run == null || run.failure != null || run.challenge == null
                || run.connection.getPacketListener() != run.parking || !frame(run.parking).equals(run.challenge)) { return false; }
        // Caller already observed the client's exact native-deferred-challenge receipt.
        run.captureArmed = true;
        return true;
    }

    /**
     * Existing beginResume calls this on main, after the real TRY is admitted.
     * Client TCP order is TRY first, original deferred ACK second. Main waits only
     * for that ACK to be captured by Netty, never while the production field lock is held.
     */
    static void beforeResume(P11ParkingPacketListener parking) {
        var run = active;
        if (run == null) { return; }
        require(run.server.isSameThread() && parking == run.parking && unlocked(run),
                "TRANSFER_RESUME_OWNER_OR_LOCK");
        await(run, run.captured, "TRANSFER_ACK_NOT_CAPTURED");
        require(run.failure == null && run.captures == 1 && run.connection.getPacketListener() == parking,
                "TRANSFER_RESUME_WITHOUT_CAPTURE");
    }

    /** Called before original native handleKeepAlive, not after any field-region read. */
    public static boolean beforeAck(ServerCommonPacketListenerImpl listener, ServerboundKeepAlivePacket packet) {
        var run = active;
        if (run == null || !run.captureArmed || listener != run.parking || run.challenge == null
                || packet.getId() != run.challenge.challenge) { return false; }
        require(!run.server.isSameThread() && run.connection.getPacketListener() == listener
                && unlocked(run) && frame(listener).equals(run.challenge) && ++run.captures == 1,
                "TRANSFER_ACK_CAPTURE_NOT_ORIGINAL_PARKING");
        run.ackThread = Thread.currentThread();
        run.captureAtNanos = System.nanoTime();
        run.captured.countDown();
        await(run, run.swapped, "TRANSFER_POINTER_NOT_SWAPPED");
        require(run.failure == null && run.next != null && run.connection.getPacketListener() == run.next
                && unlocked(run), "TRANSFER_ACK_RELEASE_WITHOUT_EXACT_SWAP");
        return true;
    }

    /** Unique same-PLAY operation; initial/config protocol installation is not replaced. */
    public static void beforeInstall(Connection connection, P11ParkingPacketListener parking,
            ServerGamePacketListenerImpl next) {
        var run = active;
        if (run == null || connection != run.connection) { return; }
        require(run.server.isSameThread() && parking == run.parking && run.captures == 1
                && connection.getPacketListener() == parking && unlocked(run), "TRANSFER_INSTALL_OWNER");
        run.before = frame(parking);
        require(run.before.equals(run.challenge) && run.before.pending, "TRANSFER_PENDING_CHANGED_BEFORE_SWAP");
        run.decoder = ((Transport) connection).c4a$decoder();
        run.autoReadBefore = ((Transport) connection).c4a$autoRead();
        run.protocolInstallsBefore = run.protocolInstalls;
        require(run.decoder != null && run.autoReadBefore, "TRANSFER_PARKING_TRANSPORT_NOT_READY");
    }

    /** Runs only after original installGame returned/unlocked; always releases the owned gate. */
    public static void afterInstall(Connection connection, P11ParkingPacketListener parking,
            ServerGamePacketListenerImpl next, boolean normal) {
        var run = active;
        if (run == null || connection != run.connection) { return; }
        try {
            require(normal && run.before != null && run.server.isSameThread() && parking == run.parking
                    && connection.getPacketListener() == next && next.getConnection() == connection
                    && unlocked(run), "TRANSFER_ORIGINAL_SWAP_DID_NOT_RETURN");
            run.after = frame(next);
            run.autoReadAfter = ((Transport) connection).c4a$autoRead();
            run.decoderPreserved = run.decoder == ((Transport) connection).c4a$decoder();
            require(run.after.equals(run.before) && run.autoReadAfter && run.decoderPreserved
                    && run.protocolInstalls == run.protocolInstallsBefore,
                    "TRANSFER_FIELDS_OR_DECODER_CHANGED");
            run.next = next;
            run.swapAtNanos = System.nanoTime();
            require(Util.getMillis() - run.after.time >= 0 && Util.getMillis() - run.after.time < 15000,
                    "TRANSFER_ORIGINAL_DEADLINE_ALREADY_REACHED");
        } catch (RuntimeException | Error failure) {
            fail(run, "TRANSFER_SWAP_OBSERVER_FAILED");
            if (normal) { throw failure; }
        } finally {
            run.swapped.countDown();
        }
    }

    public static void afterAck(ServerCommonPacketListenerImpl captured, boolean normal) {
        var run = active;
        if (run == null || captured != run.parking || Thread.currentThread() != run.ackThread) { return; }
        try {
            require(normal && run.next != null && run.connection.getPacketListener() == run.next
                    && unlocked(run), "TRANSFER_ACK_ORIGINAL_DID_NOT_RETURN");
            run.consumed = frame(run.next);
            require(!run.consumed.pending && run.consumed.time == run.before.time
                    && run.consumed.challenge == run.before.challenge && run.consumed.phase == run.before.phase
                    && frame(run.parking).equals(run.before), "TRANSFER_ACK_DID_NOT_CONSUME_CURRENT_FIELDS");
            run.ackAtNanos = System.nanoTime();
            require(run.captureAtNanos <= run.swapAtNanos && run.swapAtNanos <= run.ackAtNanos
                    && run.ackAtNanos - run.captureAtNanos < TimeUnit.SECONDS.toNanos(GATE_SECONDS),
                    "TRANSFER_ACK_ORDER_OR_BOUND");
            run.ackReturns++;
        } catch (RuntimeException | Error failure) {
            fail(run, "TRANSFER_ACK_OBSERVER_FAILED");
            if (normal) { throw failure; }
        }
    }

    public static void protocolInstall(Connection connection) {
        var run = active;
        if (run != null && connection == run.connection) { run.protocolInstalls++; }
    }

    static boolean nativeAckReturned() { var run = active; return run != null && run.ackReturns == 1; }

    static void verifyComplete() {
        var run = active;
        require(run != null && run.failure == null && run.captures == 1 && run.ackReturns == 1
                && run.next != null && run.connection.getPacketListener() == run.next
                && run.protocolInstalls == run.protocolInstallsBefore && run.connection.isConnected(),
                "TRANSFER_FINAL_NOT_PROVED");
    }

    static Map<String, Object> report() {
        var run = active;
        if (run == null) { return Map.of("active", false); }
        var out = new LinkedHashMap<String, Object>();
        out.put("active", true); out.put("failure", run.failure == null ? "NONE" : run.failure);
        out.put("capturedOriginalAcks", run.captures); out.put("originalAckReturns", run.ackReturns);
        out.put("fourFieldsBeforeSwap", value(run.before)); out.put("fourFieldsAfterSwap", value(run.after));
        out.put("fourFieldsAfterOriginalAck", value(run.consumed));
        out.put("sameDecoderObject", run.decoderPreserved); out.put("autoReadBefore", run.autoReadBefore);
        out.put("autoReadAfter", run.autoReadAfter);
        out.put("protocolInstallDeltaDuringSwap", run.before == null ? -1 : run.protocolInstalls - run.protocolInstallsBefore);
        out.put("capturedAckWaitNanos", run.ackAtNanos == 0 ? -1 : run.ackAtNanos - run.captureAtNanos);
        out.put("originalDeadlineUnchanged", run.after != null && run.before.time == run.after.time);
        out.put("nativeTimeoutExecuted", false); out.put("configResetQualified", false);
        out.put("hostExemptionQualified", false); out.put("fullC4aAcceptance", false);
        return out;
    }

    static void release() {
        var run = active;
        if (run != null) {
            if (run.ackReturns != 1) { fail(run, "TRANSFER_RELEASE_BEFORE_NATIVE_ACK"); }
            run.captured.countDown(); run.swapped.countDown(); active = null;
        }
    }

    private static Frame frame(ServerCommonPacketListenerImpl listener) {
        var value = (Fields) listener;
        return new Frame(value.c4a$time(), value.c4a$pending(), value.c4a$challenge(), value.c4a$latency(),
                ((P11KeepAliveBoundary.CommonAccess) listener).p11$keepAlivePhase());
    }
    private static Map<String, Object> value(Frame frame) {
        return frame == null ? Map.of("observed", false) : Map.of("observed", true,
                "time", frame.time, "pending", frame.pending, "challenge", frame.challenge,
                "latency", frame.latency, "phase", frame.phase);
    }
    private static boolean unlocked(Run run) {
        return !((LockObservation) (Object) ((P11KeepAliveBoundary.ConnectionAccess) run.connection).p11$keepAliveGuard())
                .c4a$heldByCurrentThread();
    }
    private static void await(Run run, CountDownLatch latch, String code) {
        try { require(latch.await(GATE_SECONDS, TimeUnit.SECONDS), code); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); fail(run, code); throw new IllegalStateException(code);
        } catch (RuntimeException | Error failure) { fail(run, code); throw failure; }
    }
    private static void fail(Run run, String code) {
        if (run.failure == null) { run.failure = code; }
        run.captured.countDown(); run.swapped.countDown();
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }

    private static final class Run {
        final MinecraftServer server; final Connection connection;
        final CountDownLatch captured = new CountDownLatch(1), swapped = new CountDownLatch(1);
        volatile String failure;
        volatile boolean captureArmed;
        volatile P11ParkingPacketListener parking;
        volatile ServerGamePacketListenerImpl next;
        volatile Frame challenge, before, after, consumed;
        volatile Thread ackThread;
        volatile int captures, ackReturns, protocolInstalls, protocolInstallsBefore;
        volatile long challengeAtNanos, captureAtNanos, swapAtNanos, ackAtNanos;
        Object decoder; boolean autoReadBefore, autoReadAfter, decoderPreserved;
        Run(MinecraftServer server, Connection connection) { this.server = server; this.connection = connection; }
    }
}
