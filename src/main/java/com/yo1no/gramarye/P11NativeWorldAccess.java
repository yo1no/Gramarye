package com.yo1no.gramarye;

import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;

/** Internal, version-locked native witness fields; not a world mutation API. */
public final class P11NativeWorldAccess {
    private P11NativeWorldAccess() {}

    public interface ServerStorage {
        boolean p11$ownsWorldStorage(LevelStorageSource.LevelStorageAccess candidate);
        ReadWitness p11$worldReadWitness();
    }

    /** Exact comparison only; never exposes the native storage owner to callers. */
    public interface PlayerStorage {
        boolean p11$ownsPlayerStorage(PlayerDataStorage candidate);
        boolean p11$independentOwnersMatch(ServerPlayer player);
        void p11$preparePrimary(P11NativeStorageBoundary.PrimaryReadRequest request);
        void p11$loadPreparedPrimary(P11NativeStorageBoundary.PrimaryReadRequest request);
        void p11$saveDetachedAtStop(P11NativeStorageBoundary.DetachedStopSaveRequest request);
    }

    /** A private-ctor, call-local request is required; no raw NBT or native owner is exposed. */
    public interface PrimaryReader {
        void p11$readPrimary(P11NativeStorageBoundary.PrimaryReadRequest request);
    }

    public interface ReadStorage {
        ReadWitness p11$readWitness();
        void p11$readWitness(ReadWitness witness);
    }

    public interface ParsedWorld {
        ParsedWitness p11$parsedInput();
        boolean p11$freshWorld();
    }

    /** Opaque native parse custody, not a public raw-NBT view or a readiness grant. */
    public static final class ParsedWitness {
        final Dynamic<?> input;
        private ParsedWitness(Dynamic<?> input) { this.input = input; }
    }

    public static ParsedWitness parsedInput(Dynamic<?> input) {
        return new ParsedWitness(input);
    }

    public static final class ReadWitness {
        final Dynamic<?> input;
        final boolean primary;
        final boolean validEnvelope;
        final P11NativeReadFingerprint playerFingerprint;
        final boolean playerPresent;

        ReadWitness(Dynamic<?> input, boolean primary, boolean validEnvelope) {
            this.input = input;
            this.primary = primary;
            this.validEnvelope = validEnvelope;
            playerPresent = input.getValue() instanceof CompoundTag data && data.contains("Player");
            playerFingerprint = input.getValue() instanceof CompoundTag data
                    && data.contains("Player", Tag.TAG_COMPOUND)
                    ? P11NativeReadFingerprint.capture(data.getCompound("Player")) : null;
        }
    }
}
