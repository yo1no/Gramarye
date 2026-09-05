package com.yo1no.gramarye.magic.network;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

final class P7ClientMirror implements P7ClientMirrorDispatchPort {
    private final BooleanSupplier clientThreadCheck;
    private volatile long dispatchGeneration;
    private IntentAcknowledgement lastAcknowledgement;
    private PlayerManaSnapshot.Availability manaAvailability =
            PlayerManaSnapshot.Availability.UNAVAILABLE;
    private long manaBalance;
    private List<CooldownSnapshotEntry> cooldownEntries = List.of();
    private long lastAppliedManaSequence;
    private long lastAppliedCooldownSequence;

    P7ClientMirror(BooleanSupplier clientThreadCheck) {
        this.clientThreadCheck = Objects.requireNonNull(
                clientThreadCheck, "clientThreadCheck");
    }

    void onConnected() {
        requireClientThread();
        advanceGeneration(true);
        clearValues();
    }

    void onDisconnected() {
        requireClientThread();
        advanceGeneration(false);
        clearValues();
    }

    void onClientWorldUnload() {
        requireClientThread();
        advanceGeneration(isConnectedGeneration(dispatchGeneration));
        clearValues();
    }

    @Override
    public long captureDispatchGeneration() {
        return dispatchGeneration;
    }

    @Override
    public void onIntentAcknowledgement(
            long expectedGeneration, IntentAcknowledgement acknowledgement) {
        requireClientThread();
        Objects.requireNonNull(acknowledgement, "acknowledgement");
        if (accepts(expectedGeneration)) {
            lastAcknowledgement = acknowledgement;
        }
    }

    @Override
    public void onPlayerManaSnapshot(
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
    public void onSkillCooldownSnapshot(
            long expectedGeneration, SkillCooldownSnapshot snapshot) {
        requireClientThread();
        Objects.requireNonNull(snapshot, "snapshot");
        if (accepts(expectedGeneration)
                && snapshot.syncSequence() > lastAppliedCooldownSequence) {
            cooldownEntries = snapshot.entries();
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
        return cooldownEntries;
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
                && isConnectedGeneration(expectedGeneration);
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
        lastAcknowledgement = null;
        manaAvailability = PlayerManaSnapshot.Availability.UNAVAILABLE;
        manaBalance = 0L;
        cooldownEntries = List.of();
        lastAppliedManaSequence = 0L;
        lastAppliedCooldownSequence = 0L;
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
}
