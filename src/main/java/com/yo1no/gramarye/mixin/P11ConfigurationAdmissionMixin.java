package com.yo1no.gramarye.mixin;

import com.yo1no.gramarye.P11ConfigurationBoundary;
import java.util.Queue;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
abstract class P11ConfigurationAdmissionMixin extends ServerCommonPacketListenerImpl
        implements P11ConfigurationBoundary.Access {
    @Shadow @Final private Queue<ConfigurationTask> configurationTasks;
    @Shadow private ConfigurationTask currentTask;
    @Shadow private ClientInformation clientInformation;

    protected P11ConfigurationAdmissionMixin(MinecraftServer server, Connection connection, CommonListenerCookie cookie) {
        super(server, connection, cookie);
    }

    @Override
    public CommonListenerCookie p11$configurationCookie() { return createCookie(clientInformation, connectionType); }

    @Override
    public ConfigurationTask.Type p11$currentConfigurationTask() { return currentTask == null ? null : currentTask.type(); }

    @Inject(method = "returnToWorld()V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void p11$returnAdmission(CallbackInfo callback) {
        var task = P11ConfigurationBoundary.returnTask((ServerConfigurationPacketListenerImpl) (Object) this);
        if (currentTask != null || configurationTasks.stream().anyMatch(queued -> queued.type().equals(task.type()))) {
            throw new IllegalStateException("P11_RETURN_TASK_ALREADY_ACTIVE");
        }
        configurationTasks.add(task);
    }

    @Inject(method = "handleConfigurationFinished(Lnet/minecraft/network/protocol/configuration/ServerboundFinishConfigurationPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayerForLogin(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)Lnet/minecraft/server/level/ServerPlayer;"),
            cancellable = true, require = 1, expect = 1, allow = 1)
    private void p11$finalAdmission(ServerboundFinishConfigurationPacket packet, CallbackInfo callback) {
        if (!P11ConfigurationBoundary.beforeFactory((ServerConfigurationPacketListenerImpl) (Object) this)) {
            callback.cancel();
        }
    }
}
