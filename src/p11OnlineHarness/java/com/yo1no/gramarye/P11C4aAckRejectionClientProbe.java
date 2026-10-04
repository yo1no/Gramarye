package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;

/** Client-only fixture: explicit ACK-ID perturbation or bounded original-ACK deferral. */
public final class P11C4aAckRejectionClientProbe {
    private static volatile Run active;
    private P11C4aAckRejectionClientProbe() { }

    static void start(Minecraft minecraft, Connection connection, P11C4aAckRejectionProbe.Mode mode, Path output) {
        require(P11C4aEvidence.enabled() && active == null && minecraft.isSameThread()
                && minecraft.player != null && minecraft.player.connection.getConnection() == connection
                && connection.isConnected() && connection.isEncrypted() && !connection.isMemoryConnection(),
                "ACK_CLIENT_REAL_REMOTE_PLAY_REQUIRED");
        active = new Run(connection, mode, output);
    }

    static boolean tick(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && ++run.ticks <= 1200 && run.failure == null,
                "ACK_CLIENT_DEADLINE_OR_OBSERVER_FAILURE");
        if (run.disconnectReturns == 0 || run.connection.isConnected() || minecraft.player != null
                || minecraft.level != null || !(minecraft.screen instanceof DisconnectedScreen)) { return false; }
        require(run.disconnectReturns == 1 && run.timeoutReason && run.selectedChallenge != 0
                && !run.boundReleased, "ACK_CLIENT_TERMINAL_NOT_ORIGINAL_TIMEOUT_PACKET");
        if (run.mode == P11C4aAckRejectionProbe.Mode.NATIVE_TIMEOUT) {
            require(run.perturbations == 0 && run.nativeQueueAdds == 1 && run.selectedSendReturns == 0
                    && run.originalReadyObserved,
                    "ACK_CLIENT_TIMEOUT_NOT_ORIGINAL_UNSENT_ACK");
        } else {
            require(run.perturbations == 1 && run.selectedSendReturns == 1
                    && run.submittedId != run.selectedChallenge, "ACK_CLIENT_PERTURBATION_NOT_EXACTLY_ONCE");
            if (run.mode == P11C4aAckRejectionProbe.Mode.STALE_ID) {
                require(run.originalSendReturns == 1 && run.submittedId == run.previousId,
                        "ACK_CLIENT_STALE_ID_NOT_PREVIOUS_ORIGINAL_SEND");
            }
        }
        var out = new LinkedHashMap<String, Object>();
        out.put("status", "ACTUAL_ORIGINAL_DISCONNECT_AFTER_LABELLED_FIXTURE_INPUT_NOT_FULL_C4A");
        out.put("mode", run.mode.name()); out.put("observedOriginalChallenge", run.selectedChallenge);
        out.put("perturbedSubmittedId", run.mode == P11C4aAckRejectionProbe.Mode.NATIVE_TIMEOUT ? null : run.submittedId);
        out.put("previousObservedAndOriginallyAcknowledgedId", run.previousId);
        out.put("packetIdPerturbations", run.perturbations); out.put("originalPriorAckSendReturns", run.originalSendReturns);
        out.put("selectedAckSendReturns", run.selectedSendReturns); out.put("nativeQueueAdds", run.nativeQueueAdds);
        out.put("boundedDeferralReleased", run.boundReleased); out.put("actualDisconnectPacketReturns", run.disconnectReturns);
        out.put("originalNativeAckPredicateWasReady", run.originalReadyObserved);
        out.put("exactNativeTimeoutTranslationKey", run.timeoutReason); out.put("actualDisconnectedScreen", true);
        out.put("actualNullPlayerAndLevel", true); out.put("oldPhaseCapturedCallbackQualified", false);
        out.put("fullC4aAcceptance", false);
        P11C4aEvidence.write(run.output, "ack-rejection.json", out);
        active = null;
        return true;
    }

    public static Packet<?> packet(ClientCommonPacketListenerImpl listener, Packet<?> original) {
        var run = matching(listener);
        if (run == null || !(original instanceof ServerboundKeepAlivePacket ack)) { return original; }
        require(run.connection.getPacketListener() == listener && run.selectedChallenge == 0,
                "ACK_CLIENT_DUPLICATE_OR_STALE_NATIVE_CHALLENGE");
        if (run.mode == P11C4aAckRejectionProbe.Mode.STALE_ID && run.previousId == 0) {
            run.previousId = ack.getId();
            return original;
        }
        run.selectedChallenge = ack.getId();
        if (run.mode == P11C4aAckRejectionProbe.Mode.NATIVE_TIMEOUT) { return original; }
        require(run.mode != P11C4aAckRejectionProbe.Mode.STALE_ID || run.originalSendReturns == 1,
                "ACK_CLIENT_SECOND_CHALLENGE_BEFORE_ORIGINAL_ACK");
        run.submittedId = run.mode == P11C4aAckRejectionProbe.Mode.WRONG_ID ? ack.getId() ^ 1L : run.previousId;
        require(run.submittedId != ack.getId() && ++run.perturbations == 1, "ACK_CLIENT_NONWRONG_PERTURBATION");
        return new ServerboundKeepAlivePacket(run.submittedId);
    }

    public static BooleanSupplier condition(ClientCommonPacketListenerImpl listener, BooleanSupplier original) {
        var run = matching(listener);
        if (run == null || run.mode != P11C4aAckRejectionProbe.Mode.NATIVE_TIMEOUT || run.selectedChallenge == 0) { return original; }
        final long started = System.nanoTime();
        return () -> {
            boolean ready = original.getAsBoolean();
            if (ready) { run.originalReadyObserved = true; }
            if (active != run) { return ready; }
            // Connection.tick can tick this listener once before native onDisconnect tears it down.
            if (!run.connection.isConnected()) { return false; }
            boolean released = System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(20);
            if (released) { run.boundReleased = true; run.failure = "ACK_CLIENT_NATIVE_TIMEOUT_DID_NOT_PRECEDE_FIXTURE_BOUND"; }
            return ready && released;
        };
    }

    public static void queued(ClientCommonPacketListenerImpl listener, Packet<?> packet, boolean added) {
        var run = matching(listener);
        if (run == null || run.mode != P11C4aAckRejectionProbe.Mode.NATIVE_TIMEOUT
                || !(packet instanceof ServerboundKeepAlivePacket ack) || ack.getId() != run.selectedChallenge) { return; }
        require(added && ++run.nativeQueueAdds == 1, "ACK_CLIENT_NATIVE_QUEUE_MISSING");
    }
    public static void sent(ClientCommonPacketListenerImpl listener, Packet<?> packet) {
        var run = matching(listener);
        if (run == null || !(packet instanceof ServerboundKeepAlivePacket ack)) { return; }
        if (run.selectedChallenge == 0 && ack.getId() == run.previousId) { run.originalSendReturns++; }
        else if (run.selectedChallenge != 0 && ack.getId() == (run.mode == P11C4aAckRejectionProbe.Mode.NATIVE_TIMEOUT
                ? run.selectedChallenge : run.submittedId)) { run.selectedSendReturns++; }
    }
    public static void disconnected(ClientCommonPacketListenerImpl listener, ClientboundDisconnectPacket packet) {
        var run = matching(listener);
        if (run == null) { return; }
        require(packet.reason().getContents() instanceof TranslatableContents translated
                && translated.getKey().equals("disconnect.timeout") && ++run.disconnectReturns == 1,
                "ACK_CLIENT_UNEXPECTED_NATIVE_DISCONNECT_REASON");
        run.timeoutReason = true;
    }
    static void release() { active = null; }
    private static Run matching(ClientCommonPacketListenerImpl listener) {
        var run = active; return run != null && listener.getConnection() == run.connection ? run : null;
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final Connection connection; final P11C4aAckRejectionProbe.Mode mode; final Path output;
        volatile String failure;
        volatile long previousId, selectedChallenge, submittedId;
        volatile int perturbations, originalSendReturns, selectedSendReturns, nativeQueueAdds, disconnectReturns;
        volatile boolean timeoutReason, boundReleased, originalReadyObserved;
        int ticks;
        Run(Connection connection, P11C4aAckRejectionProbe.Mode mode, Path output) {
            this.connection = connection; this.mode = mode; this.output = output;
        }
    }
}
