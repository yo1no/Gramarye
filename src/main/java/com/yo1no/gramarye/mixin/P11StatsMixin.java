package com.yo1no.gramarye.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DataFixer;
import com.yo1no.gramarye.P11IndependentMaterialWitness;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the canonical stats instance and original FileUtils writer/catch behavior. */
@Mixin(ServerStatsCounter.class)
abstract class P11StatsMixin implements P11IndependentMaterialWitness {
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private File file;
    @Unique private boolean p11$constructed;
    @Unique private boolean p11$inputComplete;
    @Unique private boolean p11$materialFault;
    @Unique private boolean p11$loading;

    @Override
    public boolean p11$materialComplete() {
        return p11$constructed && p11$inputComplete && !p11$materialFault && !p11$loading;
    }

    @Inject(method = "<init>(Lnet/minecraft/server/MinecraftServer;Ljava/io/File;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$constructed(MinecraftServer server, File file, CallbackInfo callback) {
        p11$constructed = true;
    }

    @WrapOperation(method = "<init>(Lnet/minecraft/server/MinecraftServer;Ljava/io/File;)V",
            at = @At(value = "INVOKE", target = "Ljava/io/File;isFile()Z"),
            require = 1, expect = 1, allow = 1)
    private boolean p11$inputExists(File input, Operation<Boolean> original) {
        boolean regular = original.call(input);
        if (!regular) {
            // isFile=false also means unreadable/non-regular. Only a positively observed
            // absence qualifies the original empty initial state; never change its branch.
            try {
                p11$inputComplete = Files.notExists(input.toPath());
            } catch (SecurityException ignored) {
                p11$inputComplete = false;
            }
            if (!p11$inputComplete) { p11$materialFault = true; }
        }
        return regular;
    }

    @Inject(method = "<init>(Lnet/minecraft/server/MinecraftServer;Ljava/io/File;)V",
            at = @At(value = "INVOKE",
                    target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),
            require = 2, expect = 2, allow = 2)
    private void p11$constructorReadFailed(MinecraftServer server, File file, CallbackInfo callback) {
        p11$materialFault = true;
    }

    @WrapMethod(method = "parseLocal(Lcom/mojang/datafixers/DataFixer;Ljava/lang/String;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$load(DataFixer fixer, String input, Operation<Void> original) {
        boolean outerLoading = p11$loading;
        if (outerLoading) { p11$materialFault = true; }
        p11$loading = true;
        boolean normal = false;
        try {
            original.call(fixer, input);
            normal = true;
        } finally {
            p11$loading = outerLoading;
            if (!normal) { p11$materialFault = true; }
            if (normal && !p11$materialFault) { p11$inputComplete = true; }
        }
    }

    @WrapOperation(method = "parseLocal(Lcom/mojang/datafixers/DataFixer;Ljava/lang/String;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/stats/ServerStatsCounter;fromJson(Lcom/google/gson/JsonObject;)Lnet/minecraft/nbt/CompoundTag;"),
            require = 1, expect = 1, allow = 1)
    private CompoundTag p11$conversion(JsonObject input, Operation<CompoundTag> original) {
        // The original converter silently drops non-number primitives, nulls, and arrays.
        // Inspect the already parsed input without copying or reparsing it. Traversal state
        // is call-local; the witness retains only booleans, not JSON, NBT, or exception graphs.
        var pending = new ArrayDeque<Iterator<Map.Entry<String, JsonElement>>>();
        pending.push(input.entrySet().iterator());
        while (!pending.isEmpty()) {
            var entries = pending.peek();
            if (!entries.hasNext()) { pending.pop(); continue; }
            var value = entries.next().getValue();
            if (value.isJsonObject()) {
                pending.push(value.getAsJsonObject().entrySet().iterator());
            } else if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                p11$materialFault = true;
                break;
            }
        }
        return original.call(input);
    }

    @WrapOperation(method = "parseLocal(Lcom/mojang/datafixers/DataFixer;Ljava/lang/String;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/nbt/CompoundTag;contains(Ljava/lang/String;I)Z"),
            require = 2, expect = 2, allow = 2)
    private boolean p11$requiredCompound(CompoundTag input, String key, int type,
            Operation<Boolean> original) {
        boolean present = original.call(input, key, type);
        if (!present) { p11$materialFault = true; }
        return present;
    }

    @Inject(method = "parseLocal(Lcom/mojang/datafixers/DataFixer;Ljava/lang/String;)V",
            at = @At(value = "INVOKE",
                    target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$nullInput(DataFixer fixer, String input, CallbackInfo callback) {
        p11$materialFault = true;
    }

    @Inject(method = "parseLocal(Lcom/mojang/datafixers/DataFixer;Ljava/lang/String;)V",
            at = @At(value = "INVOKE",
                    target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$parseFailed(DataFixer fixer, String input, CallbackInfo callback) {
        p11$materialFault = true;
    }

    @Inject(method = {"lambda$parseLocal$1(Ljava/lang/String;)V", "lambda$parseLocal$3(Ljava/lang/String;)V"},
            at = @At(value = "INVOKE",
                    target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),
            require = 2, expect = 2, allow = 2)
    private void p11$unknownStatistic(String key, CallbackInfo callback) {
        p11$materialFault = true;
    }

    @Inject(method = "lambda$parseLocal$2(Lnet/minecraft/nbt/CompoundTag;Ljava/lang/String;Lnet/minecraft/stats/StatType;)V",
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;[Ljava/lang/Object;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$invalidValue(CompoundTag input, String key, StatType<?> type, CallbackInfo callback) {
        p11$materialFault = true;
    }

    @WrapMethod(method = "setValue(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/stats/Stat;I)V",
            require = 1, expect = 1, allow = 1)
    private void p11$setValue(Player player, Stat<?> stat, int value, Operation<Void> original) {
        original.call(player, stat, value);
        P11NativeStorageBoundary.statsMutated((ServerStatsCounter) (Object) this, server);
    }

    @WrapMethod(method = "save()V", require = 1, expect = 1, allow = 1)
    private void p11$save(Operation<Void> original) {
        var scope = P11NativeStorageBoundary.beginStats((ServerStatsCounter) (Object) this, server, file);
        if (!P11NativeStorageBoundary.independentPermitted(scope)) {
            P11NativeStorageBoundary.endIndependent(scope, false);
            return;
        }
        boolean normal = false;
        try {
            original.call();
            normal = true;
        } finally {
            // A swallowed IOException also reaches normal return. Only the inner observations
            // establish outcomes; this flag merely closes the native call scope.
            P11NativeStorageBoundary.endIndependent(scope, normal);
        }
    }

    @WrapOperation(method = "save()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/stats/ServerStatsCounter;toJson()Ljava/lang/String;"),
            require = 1, expect = 1, allow = 1)
    private String p11$encode(ServerStatsCounter owner, Operation<String> original) {
        boolean normal = false;
        try {
            var encoded = original.call(owner);
            normal = true;
            return encoded;
        } finally {
            P11NativeStorageBoundary.independentEncoded(normal);
        }
    }

    @WrapOperation(method = "save()V",
            at = @At(value = "INVOKE",
                    target = "Lorg/apache/commons/io/FileUtils;writeStringToFile(Ljava/io/File;Ljava/lang/String;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$writeAndClose(File target, String encoded, Operation<Void> original)
            throws IOException {
        boolean normal = false;
        try {
            original.call(target, encoded);
            normal = true;
        } finally {
            // Normal helper return proves both original WRITE and TWR CLOSE. Failure does not
            // reveal which stage failed, so the boundary must retain UNKNOWN for both stages.
            P11NativeStorageBoundary.independentWritten(normal);
            P11NativeStorageBoundary.independentClosed(normal);
        }
    }
}
