package com.yo1no.gramarye.mixin;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import com.yo1no.gramarye.P11NativeWorldAccess;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PrimaryLevelData.class)
abstract class P11PrimaryLevelDataMixin implements P11NativeWorldAccess.ParsedWorld {
    @Unique private P11NativeWorldAccess.ParsedWitness p11$input;
    @Unique private boolean p11$fresh;
    @Override public P11NativeWorldAccess.ParsedWitness p11$parsedInput() { return p11$input; }
    @Override public boolean p11$freshWorld() { return p11$fresh; }

    // The locked constructor's enum is deprecated upstream; its exact signature is required.
    @SuppressWarnings("deprecation")
    @Inject(method = "<init>(Lnet/minecraft/world/level/LevelSettings;Lnet/minecraft/world/level/levelgen/WorldOptions;Lnet/minecraft/world/level/storage/PrimaryLevelData$SpecialWorldProperty;Lcom/mojang/serialization/Lifecycle;)V",
            at = @At("RETURN"), require = 1, expect = 1)
    private void p11$new(LevelSettings settings, WorldOptions options,
            PrimaryLevelData.SpecialWorldProperty property, Lifecycle lifecycle,
            CallbackInfo callback) { p11$fresh = true; }

    @SuppressWarnings("deprecation")
    @Inject(method = "parse(Lcom/mojang/serialization/Dynamic;Lnet/minecraft/world/level/LevelSettings;Lnet/minecraft/world/level/storage/PrimaryLevelData$SpecialWorldProperty;Lnet/minecraft/world/level/levelgen/WorldOptions;Lcom/mojang/serialization/Lifecycle;)Lnet/minecraft/world/level/storage/PrimaryLevelData;",
            at = @At("RETURN"), require = 1, expect = 1)
    private static <T> void p11$parsed(Dynamic<T> input, LevelSettings settings,
            PrimaryLevelData.SpecialWorldProperty property, WorldOptions options,
            Lifecycle lifecycle, CallbackInfoReturnable<PrimaryLevelData> callback) {
        ((P11PrimaryLevelDataMixin) (Object) callback.getReturnValue()).p11$input = P11NativeWorldAccess.parsedInput(input);
    }
}
