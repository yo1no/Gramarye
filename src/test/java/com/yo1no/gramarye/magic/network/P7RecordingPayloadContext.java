package com.yo1no.gramarye.magic.network;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.chat.Component;

final class P7RecordingPayloadContext implements IPayloadContext {
    private final Player player;
    private final Throwable enqueueFailure;
    private final PacketFlow packetFlow;
    private final ArrayDeque<Runnable> queuedTasks = new ArrayDeque<>();
    private int playerCalls;
    private int enqueueCalls;
    private int disconnectCalls;
    private Component disconnectReason;

    P7RecordingPayloadContext(Player player) {
        this(player, null, PacketFlow.SERVERBOUND);
    }

    P7RecordingPayloadContext(Player player, Throwable enqueueFailure) {
        this(player, enqueueFailure, PacketFlow.SERVERBOUND);
    }

    P7RecordingPayloadContext(
            Player player, Throwable enqueueFailure, PacketFlow packetFlow) {
        if (enqueueFailure != null
                && !(enqueueFailure instanceof RuntimeException)
                && !(enqueueFailure instanceof Error)) {
            throw new IllegalArgumentException("enqueue failure must be unchecked");
        }
        this.player = player;
        this.enqueueFailure = enqueueFailure;
        this.packetFlow = Objects.requireNonNull(packetFlow, "packetFlow");
    }

    @Override
    public ICommonPacketListener listener() {
        throw new AssertionError("listener access was not expected");
    }

    @Override
    public Player player() {
        playerCalls++;
        return player;
    }

    @Override
    public void disconnect(Component reason) {
        disconnectCalls++;
        disconnectReason = Objects.requireNonNull(reason, "reason");
    }

    @Override
    public CompletableFuture<Void> enqueueWork(Runnable task) {
        enqueueCalls++;
        Objects.requireNonNull(task, "task");
        throwEnqueueFailure();
        queuedTasks.addLast(task);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
        throw new AssertionError("supplier enqueue was not expected");
    }

    @Override
    public PacketFlow flow() {
        return packetFlow;
    }

    @Override
    public void handle(CustomPacketPayload payload) {
        throw new AssertionError("nested payload handling was not expected");
    }

    @Override
    public void finishCurrentTask(ConfigurationTask.Type type) {
        throw new AssertionError("configuration task completion was not expected");
    }

    int playerCalls() {
        return playerCalls;
    }

    int enqueueCalls() {
        return enqueueCalls;
    }

    int disconnectCalls() {
        return disconnectCalls;
    }

    Component disconnectReason() {
        return disconnectReason;
    }

    int queuedTaskCount() {
        return queuedTasks.size();
    }

    Runnable takeOnlyTask() {
        if (queuedTasks.size() != 1) {
            throw new AssertionError("expected exactly one queued task");
        }
        return queuedTasks.removeFirst();
    }

    private void throwEnqueueFailure() {
        if (enqueueFailure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (enqueueFailure instanceof Error error) {
            throw error;
        }
    }
}
