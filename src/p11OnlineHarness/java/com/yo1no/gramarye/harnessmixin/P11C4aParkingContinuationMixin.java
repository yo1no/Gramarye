package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aParkingProbe;
import com.yo1no.gramarye.P11C4aNativeObservations;
import com.yo1no.gramarye.P11C4aNativeObservations.Event;
import com.yo1no.gramarye.P11ConfigurationBoundary;
import com.yo1no.gramarye.P11ParkingPacketListener;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = P11ConfigurationBoundary.class, remap = false)
abstract class P11C4aParkingContinuationMixin {
    @Inject(method = "completeTask(Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;)V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$afterActualEarlyTask(ServerConfigurationPacketListenerImpl listener, CallbackInfo callback) {
        P11C4aParkingProbe.earlyTaskReturned(listener);
    }

    @WrapMethod(method = "resume(Lcom/yo1no/gramarye/P11ParkingPacketListener;)V", require = 1, expect = 1, allow = 1)
    private static void c4a$actualParkedCall(P11ParkingPacketListener parking, Operation<Void> original) {
        var observation = P11C4aNativeObservations.begin(parking, Event.PARKING_RESUME_ENTER);
        boolean normal = false;
        try {
            var scope = P11C4aParkingProbe.beginResume(parking);
            try { original.call(parking); normal = true; }
            finally { P11C4aParkingProbe.endResume(scope, normal); }
        } finally { P11C4aNativeObservations.end(observation, normal); }
    }
}
