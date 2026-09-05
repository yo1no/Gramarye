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
    private final Throwable replyFailure;
    private final PacketFlow packetFlow;
    private final Runnable beforeEnqueue;
    private final ArrayDeque<Runnable> queuedTasks = new ArrayDeque<>();
    private int playerCalls;
    private int enqueueCalls;
    private int replyCalls;
    private int disconnectCalls;
    private CustomPacketPayload replyPayload;
    private Component disconnectReason;

    P7RecordingPayloadContext(Player player) {
        this(player, null, null, PacketFlow.SERVERBOUND);
    }

    P7RecordingPayloadContext(Player player, Throwable enqueueFailure) {
        this(player, enqueueFailure, null, PacketFlow.SERVERBOUND);
    }

    P7RecordingPayloadContext(
            Player player, Throwable enqueueFailure, PacketFlow packetFlow) {
        this(player, enqueueFailure, null, packetFlow);
    }

    P7RecordingPayloadContext(
            Player player,
            Throwable enqueueFailure,
            Throwable replyFailure,
            PacketFlow packetFlow) {
        this(player, enqueueFailure, replyFailure, packetFlow, () -> {});
    }

    P7RecordingPayloadContext(
            Player player,
            Throwable enqueueFailure,
            Throwable replyFailure,
            PacketFlow packetFlow,
            Runnable beforeEnqueue) {
        requireUnchecked(enqueueFailure, "enqueue failure");
        requireUnchecked(replyFailure, "reply failure");
        this.player = player;
        this.enqueueFailure = enqueueFailure;
        this.replyFailure = replyFailure;
        this.packetFlow = Objects.requireNonNull(packetFlow, "packetFlow");
        this.beforeEnqueue = Objects.requireNonNull(beforeEnqueue, "beforeEnqueue");
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
    public void reply(CustomPacketPayload payload) {
        replyCalls++;
        replyPayload = Objects.requireNonNull(payload, "payload");
        throwFailure(replyFailure);
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
        beforeEnqueue.run();
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

    int replyCalls() {
        return replyCalls;
    }

    CustomPacketPayload replyPayload() {
        return replyPayload;
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
        throwFailure(enqueueFailure);
    }

    private static void requireUnchecked(Throwable failure, String label) {
        if (failure != null
                && !(failure instanceof RuntimeException)
                && !(failure instanceof Error)) {
            throw new IllegalArgumentException(label + " must be unchecked");
        }
    }

    private static void throwFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
