package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import com.yo1no.gramarye.P11NativeWorldAccess;
import java.io.File;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerDataStorage.class)
abstract class P11PlayerDataStorageMixin implements P11NativeWorldAccess.PrimaryReader {
    @Shadow @Final private File playerDir;
    @Shadow private Optional<CompoundTag> load(Player player, String suffix) {
        throw new AssertionError("native shadow");
    }

    @Override
    public void p11$readPrimary(P11NativeStorageBoundary.PrimaryReadRequest request) {
        P11NativeStorageBoundary.readPrimary((PlayerDataStorage) (Object) this, request,
                args -> load((Player) args[0], (String) args[1]));
    }

    @WrapMethod(method = "load(Lnet/minecraft/world/entity/player/Player;)Ljava/util/Optional;")
    private Optional<CompoundTag> p11$load(Player player, Operation<Optional<CompoundTag>> original) {
        return P11NativeStorageBoundary.loadPlayer((PlayerDataStorage) (Object) this, player, original);
    }

    @WrapMethod(method = "load(Lnet/minecraft/world/entity/player/Player;Ljava/lang/String;)Ljava/util/Optional;")
    private Optional<CompoundTag> p11$read(Player player, String suffix,
            Operation<Optional<CompoundTag>> original) {
        return P11NativeStorageBoundary.readPlayer((PlayerDataStorage) (Object) this,
                player, playerDir, suffix, original);
    }

    @WrapOperation(method = "lambda$load$1(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;load(Lnet/minecraft/nbt/CompoundTag;)V"), require = 1, expect = 1)
    private void p11$diskLoad(Player player, CompoundTag input, Operation<Void> original) {
        P11NativeStorageBoundary.diskLoad((PlayerDataStorage) (Object) this, player, input, original);
    }

    @WrapMethod(method = "save(Lnet/minecraft/world/entity/player/Player;)V")
    private void p11$save(Player player, Operation<Void> original) {
        P11NativeStorageBoundary.savePlayer((PlayerDataStorage) (Object) this, player, original);
    }
}
