package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observes original compression/write/close calls; it neither creates a writer nor reads back. */
@Mixin(NbtIo.class)
abstract class P11NbtIoMixin {
    @WrapMethod(
            method = "writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V",
            require = 1, expect = 1, allow = 1)
    private static void p11$pathWrite(CompoundTag root, Path path, Operation<Void> original)
            throws IOException {
        var scope = P11NativeStorageBoundary.beginNbtWrite(root, path);
        boolean normal = false;
        try {
            original.call(root, path);
            normal = true;
        } finally {
            P11NativeStorageBoundary.endNbtWrite(scope, normal);
        }
    }

    @WrapMethod(
            method = "writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/OutputStream;)V",
            require = 1, expect = 1, allow = 1)
    private static void p11$streamWrite(CompoundTag root, OutputStream output, Operation<Void> original)
            throws IOException {
        var scope = P11NativeStorageBoundary.beginNbtStream(root);
        boolean normal = false;
        try {
            original.call(root, output);
            normal = true;
        } finally {
            P11NativeStorageBoundary.endNbtStream(scope, normal);
        }
    }

    @WrapOperation(
            method = "writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/OutputStream;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtIo;write(Lnet/minecraft/nbt/CompoundTag;Ljava/io/DataOutput;)V"),
            require = 1, expect = 1, allow = 1)
    private static void p11$writeBody(CompoundTag root, DataOutput output, Operation<Void> original)
            throws IOException {
        boolean normal = false;
        try {
            original.call(root, output);
            normal = true;
        } finally {
            P11NativeStorageBoundary.nbtBodyWritten(normal);
        }
    }

    @WrapOperation(
            method = "writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/OutputStream;)V",
            at = @At(value = "INVOKE", target = "Ljava/io/DataOutputStream;close()V"),
            require = 2, expect = 2, allow = 2)
    private static void p11$closeCompressor(DataOutputStream stream, Operation<Void> original)
            throws IOException {
        boolean normal = false;
        try {
            original.call(stream);
            normal = true;
        } finally {
            P11NativeStorageBoundary.nbtStreamClosed(normal);
        }
    }

    @WrapOperation(
            method = "writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V",
            at = @At(value = "INVOKE", target = "Ljava/io/OutputStream;close()V"),
            require = 4, expect = 4, allow = 4)
    private static void p11$closePathStream(OutputStream stream, Operation<Void> original)
            throws IOException {
        boolean normal = false;
        try {
            original.call(stream);
            normal = true;
        } finally {
            P11NativeStorageBoundary.nbtStreamClosed(normal);
        }
    }
}
