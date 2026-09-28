package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11NativeCanonicalProbe;
import com.yo1no.gramarye.P11NativeDeliveryProbe;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Excluded scalar accounting at the actual native constructor, never retained packet replay. */
@Mixin(PlayerAdvancements.class)
abstract class P11CanonicalPacketMeasurementMixin {
    @WrapMethod(method = "reload(Lnet/minecraft/server/ServerAdvancementManager;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$reloadScope(ServerAdvancementManager manager, Operation<Void> original) {
        var scope = P11NativeCanonicalProbe.beginReload((PlayerAdvancements) (Object) this);
        try { original.call(manager); }
        finally { P11NativeCanonicalProbe.endReload(scope); }
    }

    @WrapOperation(method = "flushDirty(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "NEW", target = "(ZLjava/util/Collection;Ljava/util/Set;Ljava/util/Map;)Lnet/minecraft/network/protocol/game/ClientboundUpdateAdvancementsPacket;"),
            require = 1, expect = 1, allow = 1)
    private ClientboundUpdateAdvancementsPacket p11$packetBuilt(boolean reset,
            Collection<AdvancementHolder> added, Set<ResourceLocation> removed,
            Map<ResourceLocation, AdvancementProgress> progress,
            Operation<ClientboundUpdateAdvancementsPacket> original) {
        long started = System.nanoTime();
        ClientboundUpdateAdvancementsPacket packet = null;
        try { packet = original.call(reset, added, removed, progress); return packet; }
        finally {
            long nanos = System.nanoTime() - started;
            P11NativeCanonicalProbe.resetBuilt((PlayerAdvancements) (Object) this, reset, packet, nanos);
            P11NativeDeliveryProbe.resetBuilt((PlayerAdvancements) (Object) this, reset, packet, nanos);
        }
    }
}
