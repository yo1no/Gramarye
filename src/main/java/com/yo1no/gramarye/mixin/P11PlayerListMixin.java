package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import com.yo1no.gramarye.P11NativeWorldAccess;
import com.yo1no.gramarye.P11CanonicalAdvancements;
import com.yo1no.gramarye.P11RecipeDelivery;
import com.yo1no.gramarye.P11NativeCleanup;
import com.yo1no.gramarye.P11LiveTransitionBoundary;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.List;
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
abstract class P11PlayerListMixin implements P11NativeWorldAccess.PlayerStorage, P11NativeCleanup.PlayerListTail {
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private PlayerDataStorage playerIo;
    @Shadow @Final private Map<UUID, ServerStatsCounter> stats;
    @Shadow @Final private Map<UUID, PlayerAdvancements> advancements;
    @Shadow @Final private Map<UUID, ServerPlayer> playersByUUID;
    @Shadow @Final private List<ServerPlayer> players;
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

    @Override
    public void p11$saveDetachedAtStop(P11NativeStorageBoundary.DetachedStopSaveRequest request) {
        P11NativeStorageBoundary.saveDetachedAtStop((PlayerList) (Object) this, playerIo, request,
                args -> { save((ServerPlayer) args[0]); return null; });
    }

    @Override
    public boolean p11$structuralLogoutTail(P11NativeCleanup.LogoutScope scope) {
        return P11NativeCleanup.logoutTail(scope, (PlayerList) (Object) this,
                players, playersByUUID, stats, advancements);
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

    @ModifyExpressionValue(method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
            at = @At(value = "NEW", target = "(IZLjava/util/Set;IIIZZZLnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;Z)Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;"),
            require = 1, expect = 1, allow = 1)
    private net.minecraft.network.protocol.game.ClientboundLoginPacket p11$originalLoginFrame(
            net.minecraft.network.protocol.game.ClientboundLoginPacket packet) {
        P11LiveTransitionBoundary.expectedNativeFrame((PlayerList) (Object) this, packet);
        return packet;
    }

    @ModifyExpressionValue(method = "respawn(Lnet/minecraft/server/level/ServerPlayer;ZLnet/minecraft/world/entity/Entity$RemovalReason;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At(value = "NEW", target = "(Lnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;B)Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;"),
            require = 1, expect = 1, allow = 1)
    private net.minecraft.network.protocol.game.ClientboundRespawnPacket p11$originalRespawnFrame(
            net.minecraft.network.protocol.game.ClientboundRespawnPacket packet) {
        P11LiveTransitionBoundary.expectedNativeFrame((PlayerList) (Object) this, packet);
        return packet;
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

    @WrapOperation(method = "getPlayerAdvancements(Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/server/PlayerAdvancements;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerAdvancements;setPlayer(Lnet/minecraft/server/level/ServerPlayer;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$provisionalAssociation(PlayerAdvancements canonical, ServerPlayer next,
            Operation<Void> original) {
        var previous = ((P11CanonicalAdvancements.Access) canonical).p11$associatedPlayer();
        if (P11NativeStorageBoundary.observeAssociation(canonical, previous, next)) {
            original.call(canonical, next);
        }
    }

    @Inject(method = "respawn(Lnet/minecraft/server/level/ServerPlayer;ZLnet/minecraft/world/entity/Entity$RemovalReason;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removePlayerImmediately(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity$RemovalReason;)V", shift = At.Shift.AFTER),
            require = 1, expect = 1, allow = 1)
    private void p11$preConstructorCleanup(ServerPlayer previous, boolean keepEverything,
            Entity.RemovalReason reason,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<ServerPlayer> callback) {
        P11NativeStorageBoundary.preConstructorCleanupReturned(previous);
    }

    @WrapOperation(method = "respawn(Lnet/minecraft/server/level/ServerPlayer;ZLnet/minecraft/world/entity/Entity$RemovalReason;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At(value = "NEW", target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)Lnet/minecraft/server/level/ServerPlayer;"),
            require = 1, expect = 1, allow = 1)
    private ServerPlayer p11$actualRespawnBody(MinecraftServer exactServer,
            net.minecraft.server.level.ServerLevel level, GameProfile profile, ClientInformation information,
            Operation<ServerPlayer> original) {
        var next = original.call(exactServer, level, profile, information);
        P11NativeStorageBoundary.respawnConstructed(next);
        return next;
    }

    @Inject(method = "respawn(Lnet/minecraft/server/level/ServerPlayer;ZLnet/minecraft/world/entity/Entity$RemovalReason;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setHealth(F)V", shift = At.Shift.AFTER),
            require = 1, expect = 1, allow = 1)
    private void p11$respawnBodyComplete(ServerPlayer previous, boolean keepEverything,
            Entity.RemovalReason reason,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<ServerPlayer> callback,
            @Local(ordinal = 1) ServerPlayer next) {
        P11NativeStorageBoundary.respawnMaterial(next);
    }

    @Inject(method = "respawn(Lnet/minecraft/server/level/ServerPlayer;ZLnet/minecraft/world/entity/Entity$RemovalReason;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$respawnRecipes(ServerPlayer previous, boolean keepEverything,
            Entity.RemovalReason reason,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<ServerPlayer> callback) {
        P11RecipeDelivery.afterRespawn(callback.getReturnValue());
    }

    @WrapOperation(method = {"remove(Lnet/minecraft/server/level/ServerPlayer;)V",
            "respawn(Lnet/minecraft/server/level/ServerPlayer;ZLnet/minecraft/world/entity/Entity$RemovalReason;)Lnet/minecraft/server/level/ServerPlayer;"},
            at = @At(value = "INVOKE", target = "Ljava/util/List;remove(Ljava/lang/Object;)Z"),
            require = 2, expect = 2, allow = 2)
    private boolean p11$removeExactRoster(List<?> roster, Object expected, Operation<Boolean> original) {
        // Entity.equals compares only entity id; a failed A/B transition may share that id.
        return P11NativeCleanup.removeExact(roster, expected);
    }

    @WrapOperation(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerAdvancements;stopListening()V"),
            require = 1, expect = 1, allow = 1)
    private void p11$canonicalListeners(PlayerAdvancements canonical, Operation<Void> original,
            @Local(argsOnly = true) ServerPlayer actor) {
        if (!P11NativeStorageBoundary.preserveCanonical(actor, canonical)) { original.call(canonical); }
    }

    @WrapOperation(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removePlayerImmediately(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity$RemovalReason;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$logoutWorld(net.minecraft.server.level.ServerLevel level, ServerPlayer actor,
            Entity.RemovalReason reason, Operation<Void> original) {
        P11NativeCleanup.logoutWorld(level, actor, reason, original);
    }

    @WrapOperation(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 3, expect = 3, allow = 3)
    private Object p11$canonicalMaps(Map<?, ?> map, Object key, Operation<Object> original,
            @Local(argsOnly = true) ServerPlayer actor) {
        var value = map.get(key);
        if (map == playersByUUID) {
            return value == actor ? original.call(map, key) : null;
        }
        Object expected = map == stats ? actor.getStats() : map == advancements ? actor.getAdvancements() : null;
        if (expected == null || value != expected
                || P11NativeStorageBoundary.preserveCanonical(actor, expected)) { return null; }
        return original.call(map, key);
    }

    @WrapMethod(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V")
    private void p11$logout(ServerPlayer player, Operation<Void> original) {
        P11NativeStorageBoundary.remove(player, original);
    }

    @WrapMethod(method = "respawn(Lnet/minecraft/server/level/ServerPlayer;ZLnet/minecraft/world/entity/Entity$RemovalReason;)Lnet/minecraft/server/level/ServerPlayer;")
    private ServerPlayer p11$copyScope(ServerPlayer player, boolean keepEverything,
            Entity.RemovalReason reason, Operation<ServerPlayer> original) {
        return P11NativeStorageBoundary.respawn((PlayerList) (Object) this, player, keepEverything, reason, original);
    }
}
