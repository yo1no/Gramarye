package com.yo1no.gramarye.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11IndependentMaterialWitness;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the canonical advancement writer without replacing its codec, writer, or catches. */
@Mixin(PlayerAdvancements.class)
abstract class P11AdvancementsMixin implements P11IndependentMaterialWitness {
    @Shadow private ServerPlayer player;
    @Shadow @Final private Path playerSavePath;
    @Unique private boolean p11$inputComplete;
    @Unique private boolean p11$materialFault;
    @Unique private boolean p11$loading;

    @Override
    public boolean p11$materialComplete() {
        return p11$inputComplete && !p11$materialFault && !p11$loading;
    }

    @WrapMethod(method = "load(Lnet/minecraft/server/ServerAdvancementManager;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$load(ServerAdvancementManager manager, Operation<Void> original) {
        boolean outerLoading = p11$loading;
        if (outerLoading) { p11$materialFault = true; }
        p11$loading = true;
        boolean normal = false;
        try {
            original.call(manager);
            normal = true;
        } finally {
            p11$loading = outerLoading;
            if (!normal) { p11$materialFault = true; }
            if (normal && !p11$materialFault) { p11$inputComplete = true; }
        }
    }

    @WrapOperation(method = "load(Lnet/minecraft/server/ServerAdvancementManager;)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/nio/file/Files;isRegularFile(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"),
            require = 1, expect = 1, allow = 1)
    private boolean p11$inputExists(Path input, LinkOption[] options, Operation<Boolean> original) {
        boolean regular = original.call(input, options);
        if (!regular) {
            boolean absent;
            try {
                absent = Files.notExists(input, options);
            } catch (SecurityException ignored) {
                absent = false;
            }
            if (!absent) { p11$materialFault = true; }
        }
        return regular;
    }

    @Inject(method = "load(Lnet/minecraft/server/ServerAdvancementManager;)V",
            at = @At(value = "INVOKE",
                    target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),
            require = 2, expect = 2, allow = 2)
    private void p11$readFailed(ServerAdvancementManager manager, CallbackInfo callback) {
        p11$materialFault = true;
    }

    @Inject(method = "reload(Lnet/minecraft/server/ServerAdvancementManager;)V",
            at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void p11$unqualifiedReload(ServerAdvancementManager manager, CallbackInfo callback) {
        // This slice does not implement the approved canonical-memory reload input. Native
        // clear/reload still runs, but stale JSON must not restore this instance's save grant.
        p11$materialFault = true;
    }

    @WrapMethod(method = {
            "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z",
            "revoke(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z"},
            require = 2, expect = 2, allow = 2)
    private boolean p11$progressChanged(AdvancementHolder advancement, String criterion,
            Operation<Boolean> original) {
        boolean changed = original.call(advancement, criterion);
        if (changed) {
            P11NativeStorageBoundary.advancementsMutated((PlayerAdvancements) (Object) this, player);
        }
        return changed;
    }

    @WrapMethod(method = "save()V", require = 1, expect = 1, allow = 1)
    private void p11$save(Operation<Void> original) {
        var scope = P11NativeStorageBoundary.beginAdvancements(
                (PlayerAdvancements) (Object) this, player, playerSavePath);
        if (!P11NativeStorageBoundary.independentPermitted(scope)) {
            P11NativeStorageBoundary.endIndependent(scope, false);
            return;
        }
        boolean normal = false;
        try {
            original.call();
            normal = true;
        } finally {
            // The native method swallows some write/close failures. Normal return alone is
            // never a success receipt; only the stage observations below provide evidence.
            P11NativeStorageBoundary.endIndependent(scope, normal);
        }
    }

    @WrapOperation(method = "save()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/FileUtil;createDirectoriesSafe(Ljava/nio/file/Path;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$afterEncoding(Path parent, Operation<Void> original) throws IOException {
        // Reaching this first IO call proves the original codec encode/getOrThrow completed.
        // An earlier codec exception never reaches here and leaves encoding UNKNOWN, not
        // an invented failed stage. Directory creation itself is not a write/close receipt.
        P11NativeStorageBoundary.independentEncoded(true);
        original.call(parent);
    }

    @WrapOperation(method = "save()V",
            at = @At(value = "INVOKE",
                    target = "Lcom/google/gson/Gson;toJson(Lcom/google/gson/JsonElement;Lcom/google/gson/stream/JsonWriter;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$write(Gson gson, JsonElement encoded, JsonWriter writer,
            Operation<Void> original) {
        boolean normal = false;
        try {
            original.call(gson, encoded, writer);
            normal = true;
        } finally {
            P11NativeStorageBoundary.independentWritten(normal);
        }
    }

    @WrapOperation(method = "save()V",
            at = @At(value = "INVOKE", target = "Ljava/io/Writer;close()V"),
            require = 2, expect = 2, allow = 2)
    private void p11$close(Writer writer, Operation<Void> original) throws IOException {
        boolean normal = false;
        try {
            original.call(writer);
            normal = true;
        } finally {
            P11NativeStorageBoundary.independentClosed(normal);
        }
    }
}
