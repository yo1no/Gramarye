package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11NativeCleanup;
import java.util.List;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
abstract class P11LevelEntityCleanupMixin {
    @WrapOperation(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"),
            require = 1, expect = 1, allow = 1)
    private Event p11$leaveOutcome(IEventBus bus, Event event, Operation<Event> original) {
        return P11NativeCleanup.leave(bus, event, original);
    }

    @WrapOperation(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;remove(Ljava/lang/Object;)Z"),
            require = 1, expect = 1, allow = 1)
    private boolean p11$exactWorldRoster(List<?> players, Object expected, Operation<Boolean> original) {
        return P11NativeCleanup.removeExact(players, expected);
    }
}
