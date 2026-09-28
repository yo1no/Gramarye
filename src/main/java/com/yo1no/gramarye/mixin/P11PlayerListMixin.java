package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import com.yo1no.gramarye.P11NativeWorldAccess;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Locked 1.21.1 caller scopes; no lifecycle body is copied or replayed. */
@Mixin(PlayerList.class)
abstract class P11PlayerListMixin implements P11NativeWorldAccess.PlayerStorage {
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private PlayerDataStorage playerIo;
    @Shadow @Final private Map<UUID, ServerStatsCounter> stats;
    @Shadow @Final private Map<UUID, PlayerAdvancements> advancements;
    @Shadow protected abstract void save(ServerPlayer player);

    @Override
    public boolean p11$ownsPlayerStorage(PlayerDataStorage candidate) {
        return playerIo == candidate;
    }

    @Override
    public boolean p11$independentOwnersMatch(ServerPlayer player) {
        var stat = stats.get(player.getUUID());
        var advancement = advancements.get(player.getUUID());
        return (stat == null || stat == player.getStats())
                && (advancement == null || advancement == player.getAdvancements());
    }

    @Override
    public void p11$preparePrimary(P11NativeStorageBoundary.PrimaryReadRequest request) {
        P11NativeStorageBoundary.preparePrimary((PlayerList) (Object) this, playerIo, request,
                args -> { save((ServerPlayer) args[0]); return null; });
    }

    @Override
    public void p11$loadPreparedPrimary(P11NativeStorageBoundary.PrimaryReadRequest request) {
        P11NativeStorageBoundary.loadPreparedPrimary((PlayerList) (Object) this, playerIo, request);
    }

    @WrapMethod(method = "getPlayerForLogin(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)Lnet/minecraft/server/level/ServerPlayer;",
            require = 1, expect = 1, allow = 1)
    private ServerPlayer p11$loginPlayer(GameProfile profile, ClientInformation information,
            Operation<ServerPlayer> original) {
        return P11NativeStorageBoundary.loginPlayer((PlayerList) (Object) this, profile, information, original);
    }

    @WrapMethod(method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V")
    private void p11$placement(Connection connection, ServerPlayer player,
            CommonListenerCookie cookie, Operation<Void> original) {
        P11NativeStorageBoundary.place(server, connection, player, cookie, original);
    }

    @WrapMethod(method = "load(Lnet/minecraft/server/level/ServerPlayer;)Ljava/util/Optional;")
    private Optional<CompoundTag> p11$choose(ServerPlayer player,
            Operation<Optional<CompoundTag>> original) {
        return P11NativeStorageBoundary.load(server, player, original);
    }

    @WrapOperation(method = "load(Lnet/minecraft/server/level/ServerPlayer;)Ljava/util/Optional;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;load(Lnet/minecraft/nbt/CompoundTag;)V"), require = 1, expect = 1)
    private void p11$hostLoad(ServerPlayer player, CompoundTag input, Operation<Void> original) {
        P11NativeStorageBoundary.hostLoad(player, input, original);
    }

    // Reaching this unique anchor proves the entire Dimension/mode/vehicle prefix returned.
    // The LoggedIn/P4 tail is deliberately not a material-completeness requirement.
    @Inject(method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;initInventoryMenu()V"), require = 1, expect = 1)
    private void p11$callerMaterial(Connection connection, ServerPlayer player,
            CommonListenerCookie cookie, CallbackInfo callback) {
        P11NativeStorageBoundary.callerMaterial(player);
    }

    @WrapMethod(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V")
    private void p11$logout(ServerPlayer player, Operation<Void> original) {
        P11NativeStorageBoundary.remove(player, original);
    }

    @WrapMethod(method = "respawn(Lnet/minecraft/server/level/ServerPlayer;ZLnet/minecraft/world/entity/Entity$RemovalReason;)Lnet/minecraft/server/level/ServerPlayer;")
    private ServerPlayer p11$copyScope(ServerPlayer player, boolean keepEverything,
            Entity.RemovalReason reason, Operation<ServerPlayer> original) {
        return P11NativeStorageBoundary.respawn(player, keepEverything, reason, original);
    }
}
