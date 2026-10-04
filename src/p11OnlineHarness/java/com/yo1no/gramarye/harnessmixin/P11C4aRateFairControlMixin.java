package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aRateFairProbe;
import com.yo1no.gramarye.P11C4aC6NativeProbe;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
@Mixin(targets = "com.yo1no.gramarye.P11TransitionControl", remap = false)
abstract class P11C4aRateFairControlMixin {
    @Shadow private boolean notification;
    @Shadow private boolean preferNotification;
    @Unique private boolean rf$hadInbox;
    @ModifyExpressionValue(method = "nextService(Z)Lcom/yo1no/gramarye/P11TransitionControl$Service;", at = @At(value = "FIELD", target = "Lcom/yo1no/gramarye/P11TransitionControl;inbox:Lcom/yo1no/gramarye/P11TransitionProtocol$Request;"), require = 1, expect = 1, allow = 1)
    @Coerce private Object rf$inboxRead(@Coerce Object original) {
        rf$hadInbox = original != null;
        return original;
    }
    @WrapMethod(method = "offer(Lcom/yo1no/gramarye/P11TransitionControl$Listener;Lcom/yo1no/gramarye/P11TransitionProtocol$Request;J)Lcom/yo1no/gramarye/P11TransitionControl$Offer;", require = 1, expect = 1, allow = 1)
    @Coerce private Object rf$offer(@Coerce Object listener, @Coerce Object request, long now, Operation<Object> original) {
        Object call = null;
        try {
            var access = (P11C4aC6NativeProbe.ControlView) (Object) this;
            call = P11C4aRateFairProbe.offering(this, request, access.c6$tryRate(), access.c6$statusRate());
        }
        catch (RuntimeException | Error failure) { P11C4aRateFairProbe.observerFailed(); }
        Object result = null; boolean normal = false;
        try { result = original.call(listener, request, now); normal = true; return result; }
        finally {
            try { P11C4aRateFairProbe.offered(call, result, normal); }
            catch (RuntimeException | Error failure) { P11C4aRateFairProbe.observerFailed(); }
        }
    }
    @WrapMethod(method = "nextService(Z)Lcom/yo1no/gramarye/P11TransitionControl$Service;", require = 1, expect = 1, allow = 1)
    @Coerce private Object rf$service(boolean notificationsAllowed, Operation<Object> original) {
        boolean hadInbox, hadNotification, preferred;
        Object result;
        // The original synchronized body only reads/changes control fields, with no callbacks.
        synchronized (this) {
            rf$hadInbox = false;
            hadNotification = notificationsAllowed && notification; preferred = preferNotification;
            result = original.call(notificationsAllowed);
            hadInbox = rf$hadInbox;
        }
        try { P11C4aRateFairProbe.service(this, hadInbox, hadNotification, preferred, result); }
        catch (RuntimeException | Error failure) { P11C4aRateFairProbe.observerFailed(); }
        return result;
    }
}
