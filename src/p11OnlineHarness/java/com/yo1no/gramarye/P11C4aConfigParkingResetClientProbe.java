package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.util.thread.BlockableEventLoop;

/** One opaque original Finish task, delayed on owning main only; never a Netty gate or new sender. */
public final class P11C4aConfigParkingResetClientProbe {
    private static volatile Run active;
    private P11C4aConfigParkingResetClientProbe() { }
    static void start(Minecraft minecraft, Connection connection, String role, Path output, Path serverOutput) throws IOException {
        require(active == null && minecraft.isSameThread(), "CP_RESET_CLIENT_START");
        active = new Run(connection, role, output, serverOutput);
        P11C4aConfigPrimaryProbe.armParkingReset(connection, output, false);
        P11C4aConfigResetClientProbe.startParking(minecraft, connection, output);
        P11C4aParkingClientProbe.start(minecraft, connection, role, output, serverOutput);
    }
    public static Runnable scheduled(Packet<?> packet, PacketListener listener, BlockableEventLoop<?> executor, Runnable original) {
        var run = active;
        if (run == null || !(packet instanceof ClientboundFinishConfigurationPacket)
                || !(listener instanceof ClientConfigurationPacketListenerImpl configuration)
                || configuration.getConnection() != run.connection || executor != Minecraft.getInstance()) { return original; }
        return () -> {
            require(active == run && executor.isSameThread() && run.connection.getPacketListener() == listener
                    && run.captured == 0 && run.held == null && P11C4aConfigResetClientProbe.pendingParkingAck(),
                    "CP_RESET_FINISH_CAPTURE_NOT_NATIVE_CURRENT_TASK");
            run.captured++; run.held = original; run.listener = listener; run.started = System.nanoTime();
            try { P11C4aEvidence.write(run.output, "config-parking-finish-captured.json", Map.of(
                    "status", "ACTUAL_ORIGINAL_FINISH_RUNNABLE_HELD_ON_MAIN_NOT_ACCEPTANCE", "captured", 1)); }
            catch (IOException failure) { throw new IllegalStateException("CP_RESET_CAPTURE_IO", failure); }
        };
    }
    static boolean tick(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && ++run.ticks <= 2400, "CP_RESET_CLIENT_TICK");
        if (run.held != null) {
            require(System.nanoTime() - run.started < TimeUnit.SECONDS.toNanos(5)
                    && run.connection.getPacketListener() == run.listener, "CP_RESET_FINISH_HOLD_BOUND");
            if (P11C4aEvidence.cuePresent(run.serverOutput, run.role + "-config-parking-finish-release.ready")) {
                Runnable original = run.held; run.held = null;
                require(++run.released == 1, "CP_RESET_FINISH_RELEASE_ONCE");
                minecraft.executeIfPossible(original);
            }
        }
        if (!run.resetDone) { run.resetDone = P11C4aConfigResetClientProbe.tick(minecraft); }
        if (!run.parkingDone) { run.parkingDone = P11C4aParkingClientProbe.tick(minecraft); }
        if (!run.resetDone || !run.parkingDone) { return false; }
        require(run.captured == 1 && run.released == 1 && run.held == null,
                "CP_RESET_CLIENT_ORIGINAL_TASK_TERMINAL");
        P11C4aEvidence.write(run.output, "config-parking-reset.json", Map.of(
                "status", "ACTUAL_CONFIG_ACK_RETIRED_NEW_PARKING_ACK_AND_MANUAL_RETRY", "originalTasksCaptured", 1,
                "originalTasksReleased", 1, "nettyBlocked", false, "oldAckReplayed", false, "fullC4aAcceptance", false));
        P11C4aConfigPrimaryProbe.finish(run.connection);
        active = null; return true;
    }
    static void release() { active = null; }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final Connection connection; final String role; final Path output, serverOutput;
        PacketListener listener; Runnable held; long started;
        int captured, released, ticks; boolean resetDone, parkingDone;
        Run(Connection connection, String role, Path output, Path serverOutput) {
            this.connection = connection; this.role = role; this.output = output; this.serverOutput = serverOutput;
        }
    }
}
