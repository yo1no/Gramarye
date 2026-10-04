package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.yo1no.gramarye.P11C4aNativeObservations;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Actual installed fixed codec invocations; no fabricated request or state is supplied. */
@Mixin(targets = "com.yo1no.gramarye.P11TransitionWire", remap = false)
abstract class P11C4aWireObservationMixin {
    @Inject(method = {"encodeRequest", "encodeState"}, at = @At("HEAD"), require = 2, expect = 2, allow = 2)
    private static void c4a$encodeStart(FriendlyByteBuf buffer, @Coerce Object value, CallbackInfo callback,
            @Share("c4a$start") LocalIntRef start) { start.set(buffer.writerIndex()); }

    @Inject(method = "encodeRequest", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void c4a$requestEncoded(FriendlyByteBuf buffer, @Coerce Object value, CallbackInfo callback,
            @Share("c4a$start") LocalIntRef start) {
        P11C4aNativeObservations.wire(0, buffer.writerIndex() - start.get());
    }

    @Inject(method = "encodeState", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void c4a$stateEncoded(FriendlyByteBuf buffer, @Coerce Object value, CallbackInfo callback,
            @Share("c4a$start") LocalIntRef start) {
        P11C4aNativeObservations.wire(2, buffer.writerIndex() - start.get());
    }

    @Inject(method = {"decodeRequest", "decodeState"}, at = @At("HEAD"), require = 2, expect = 2, allow = 2)
    private static void c4a$decodeStart(FriendlyByteBuf buffer, CallbackInfoReturnable<Object> callback,
            @Share("c4a$start") LocalIntRef start) { start.set(buffer.readerIndex()); }

    @Inject(method = "decodeRequest", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void c4a$requestDecoded(FriendlyByteBuf buffer, CallbackInfoReturnable<Object> callback,
            @Share("c4a$start") LocalIntRef start) {
        P11C4aNativeObservations.wire(1, buffer.readerIndex() - start.get());
    }

    @Inject(method = "decodeState", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void c4a$stateDecoded(FriendlyByteBuf buffer, CallbackInfoReturnable<Object> callback,
            @Share("c4a$start") LocalIntRef start) {
        P11C4aNativeObservations.wire(3, buffer.readerIndex() - start.get());
    }
}
