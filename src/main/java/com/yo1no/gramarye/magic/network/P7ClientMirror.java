package com.yo1no.gramarye.magic.network;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.BiPredicate;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

final class P7ClientMirror implements P7ClientMirrorDispatchPort {
    private final BooleanSupplier clientThreadCheck;
    private final BiPredicate<Connection, ICommonPacketListener> currentTransport;
    private volatile long dispatchGeneration;
    private Connection connection;
    private ICommonPacketListener listener;
    private boolean worldAvailable;
    private IntentAcknowledgement lastAcknowledgement;
    private PlayerManaSnapshot.Availability manaAvailability =
            PlayerManaSnapshot.Availability.UNAVAILABLE;
    private long manaBalance;
    private SkillCooldownSnapshot cooldownSnapshot;
    private long lastAppliedManaSequence;
    private long lastAppliedCooldownSequence;

    P7ClientMirror(BooleanSupplier clientThreadCheck) {
        this(clientThreadCheck, P7ClientMirror::nativeTransportCurrent);
    }

    P7ClientMirror(BooleanSupplier clientThreadCheck,
            BiPredicate<Connection, ICommonPacketListener> currentTransport) {
        this.clientThreadCheck = Objects.requireNonNull(
                clientThreadCheck, "clientThreadCheck");
        this.currentTransport = Objects.requireNonNull(currentTransport, "currentTransport");
    }

    synchronized void onConnected(Connection connection, ICommonPacketListener listener) {
        requireClientThread();
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(listener, "listener");
        if (!currentTransport.test(connection, listener)) { return; }
        if (this.connection == connection && this.listener == listener
                && isConnectedGeneration(dispatchGeneration)) { return; }
        advanceGeneration(true);
        this.connection = connection;
        this.listener = listener;
        worldAvailable = true;
        clearValues();
    }

    synchronized void onDisconnected() {
        requireClientThread();
        advanceGeneration(false);
        connection = null;
        listener = null;
        worldAvailable = false;
        clearValues();
    }

    synchronized void onClientWorldUnload() {
        requireClientThread();
        advanceGeneration(isConnectedGeneration(dispatchGeneration));
        worldAvailable = false;
        clearPresentation();
    }

    synchronized void onPlayerContextReplaced(Connection sourceConnection, ICommonPacketListener sourceListener) {
        requireClientThread();
        if (connection != sourceConnection || listener != sourceListener
                || !isConnectedGeneration(dispatchGeneration) || !currentTransport.test(connection, listener)) { return; }
        advanceGeneration(true);
        worldAvailable = true;
        clearPresentation();
    }

    @Override
    public synchronized long captureDispatchGeneration(Connection sourceConnection, ICommonPacketListener sourceListener) {
        return sourceConnection != null && sourceListener != null && sourceConnection == connection
                && sourceListener == listener && worldAvailable && isConnectedGeneration(dispatchGeneration)
                && currentTransport.test(connection, listener) ? dispatchGeneration : 0;
    }

    long currentDispatchGeneration() { requireClientThread(); return dispatchGeneration; }

    @Override
    public synchronized void onIntentAcknowledgement(
            long expectedGeneration, IntentAcknowledgement acknowledgement) {
        requireClientThread();
        Objects.requireNonNull(acknowledgement, "acknowledgement");
        if (accepts(expectedGeneration)) {
            lastAcknowledgement = acknowledgement;
        }
    }

    @Override
    public synchronized void onPlayerManaSnapshot(
            long expectedGeneration, PlayerManaSnapshot snapshot) {
        requireClientThread();
        Objects.requireNonNull(snapshot, "snapshot");
        if (accepts(expectedGeneration)
                && snapshot.syncSequence() > lastAppliedManaSequence) {
            manaAvailability = snapshot.availability();
            manaBalance = snapshot.balance();
            lastAppliedManaSequence = snapshot.syncSequence();
        }
    }

    @Override
    public synchronized void onSkillCooldownSnapshot(
            long expectedGeneration, SkillCooldownSnapshot snapshot) {
        requireClientThread();
        Objects.requireNonNull(snapshot, "snapshot");
        if (accepts(expectedGeneration)
                && snapshot.syncSequence() > lastAppliedCooldownSequence) {
            cooldownSnapshot = snapshot;
            lastAppliedCooldownSequence = snapshot.syncSequence();
        }
    }

    Optional<IntentAcknowledgement> lastAcknowledgement() {
        requireClientThread();
        return Optional.ofNullable(lastAcknowledgement);
    }

    PlayerManaSnapshot.Availability manaAvailability() {
        requireClientThread();
        return manaAvailability;
    }

    long manaBalance() {
        requireClientThread();
        return manaBalance;
    }

    List<CooldownSnapshotEntry> cooldownEntries() {
        requireClientThread();
        return cooldownSnapshot == null ? List.of() : cooldownSnapshot.entries();
    }

    Optional<SkillCooldownSnapshot> cooldownSnapshot() {
        requireClientThread();
        return Optional.ofNullable(cooldownSnapshot);
    }

    long lastAppliedManaSequence() {
        requireClientThread();
        return lastAppliedManaSequence;
    }

    long lastAppliedCooldownSequence() {
        requireClientThread();
        return lastAppliedCooldownSequence;
    }

    private boolean accepts(long expectedGeneration) {
        return expectedGeneration > 0
                && expectedGeneration == dispatchGeneration
                && isConnectedGeneration(expectedGeneration)
                && worldAvailable
                && connection != null && listener != null && currentTransport.test(connection, listener);
    }

    private void advanceGeneration(boolean connected) {
        var current = dispatchGeneration;
        var currentConnected = isConnectedGeneration(current);
        var increment = currentConnected == connected ? 2L : 1L;
        if (current > Long.MAX_VALUE - increment) {
            throw new P7SemanticInvariantException(
                    "client dispatch generation is exhausted");
        }
        dispatchGeneration = current + increment;
    }

    private void clearValues() {
        clearPresentation();
        lastAppliedManaSequence = 0L;
        lastAppliedCooldownSequence = 0L;
    }

    private void clearPresentation() {
        lastAcknowledgement = null;
        manaAvailability = PlayerManaSnapshot.Availability.UNAVAILABLE;
        manaBalance = 0L;
        cooldownSnapshot = null;
    }

    private void requireClientThread() {
        if (!clientThreadCheck.getAsBoolean()) {
            throw new P7SemanticInvariantException(
                    "client mirror mutation is off the client thread");
        }
    }

    private static boolean isConnectedGeneration(long generation) {
        return (generation & 1L) != 0L;
    }

    private static boolean nativeTransportCurrent(Connection connection, ICommonPacketListener listener) {
        return connection != null && listener != null && connection.isConnected()
                && connection.getPacketListener() == listener && listener.getConnection() == connection
                && listener.protocol() == ConnectionProtocol.PLAY && listener.flow() == PacketFlow.CLIENTBOUND;
    }
}
