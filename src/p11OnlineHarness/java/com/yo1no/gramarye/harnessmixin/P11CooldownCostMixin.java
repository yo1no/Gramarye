package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11CooldownCostProbe;
import com.yo1no.gramarye.P11CooldownDualProbe;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.P11CastCooldownService")
abstract class P11CooldownCostMixin {
    @Shadow @Final private Map<UUID, ?> cells;
    @Coerce
    @WrapMethod(method = "prepareAdmission(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;Lcom/yo1no/gramarye/magic/definition/document/SkillReference;IJJLcom/yo1no/gramarye/P11CastCooldownService$ReleaseReceipt;Lcom/yo1no/gramarye/P11QualifiedSourceOwner$WorkReservation;)Lcom/yo1no/gramarye/P11CastCooldownService$Admission;",
            require = 1, expect = 1, allow = 1)
    private Object p11$cooldownPrepareCost(MinecraftServer server, ServerPlayer actor, SkillReference reference, int duration,
            long accepted, long deadline, @Coerce Object receipt, @Coerce Object work, Operation<Object> original) {
        var timing = P11CooldownCostProbe.before(this, 0); boolean normal = false;
        try { Object result = original.call(server, actor, reference, duration, accepted, deadline, receipt, work); normal = true; return result; }
        finally { P11CooldownCostProbe.after(this, timing, normal, cells); }
    }
    @WrapMethod(method = "installPending(Lcom/yo1no/gramarye/P11CastCooldownService$Prepared;)Z", require = 1, expect = 1, allow = 1)
    private boolean p11$cooldownInstallCost(@Coerce Object attempt, Operation<Boolean> original) {
        var timing = P11CooldownCostProbe.before(this, 1); boolean normal = false;
        try { boolean result = original.call(attempt); normal = true; return result; }
        finally { P11CooldownCostProbe.after(this, timing, normal, cells); }
    }
    @Coerce
    @WrapMethod(method = "prepareArm(Lcom/yo1no/gramarye/P11CastCooldownService$Prepared;Lcom/yo1no/gramarye/P11QualifiedSourceOwner$WorkReservation;Lnet/minecraft/server/level/ServerPlayer;J)Lcom/yo1no/gramarye/P11CastCooldownService$ArmPreparation;",
            require = 1, expect = 1, allow = 1)
    private Object p11$cooldownArmPrepareCost(@Coerce Object attempt, @Coerce Object work, ServerPlayer actor, long tick, Operation<Object> original) {
        var timing = P11CooldownCostProbe.before(this, 2); boolean normal = false;
        try { Object result = original.call(attempt, work, actor, tick); normal = true; return result; }
        finally { P11CooldownCostProbe.after(this, timing, normal, cells); }
    }
    @WrapMethod(method = "completeArm(Lcom/yo1no/gramarye/P11CastCooldownService$ArmPreparation;)V", require = 1, expect = 1, allow = 1)
    private void p11$cooldownArmCompleteCost(@Coerce Object arm, Operation<Void> original) {
        var timing = P11CooldownCostProbe.before(this, 3); boolean normal = false;
        try { original.call(arm); normal = true; }
        finally { P11CooldownCostProbe.after(this, timing, normal, cells); }
    }
    @Inject(method = "publish(Lcom/yo1no/gramarye/P11QualifiedSourceOwner;Lcom/yo1no/gramarye/P11QualifiedSourceOwner$Body;Lcom/yo1no/gramarye/P11CastCooldownService$Cell;Lcom/yo1no/gramarye/P11CastCooldownData;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$cooldownMaterialCost(@Coerce Object owner, @Coerce Object body, @Coerce Object cell,
            @Coerce Object data, CallbackInfo callback) {
        P11CooldownCostProbe.published(this, data, cells);
        P11CooldownDualProbe.published(this, body, cell, data, cells);
    }
    @Inject(method = "stopped(Lnet/minecraft/server/MinecraftServer;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$cooldownStoppedCost(MinecraftServer server, CallbackInfo callback) { P11CooldownCostProbe.stopped(this, cells); }
}
