package com.yo1no.gramarye;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/** Closed native producer bridge. Public visibility exists solely for exact mixin callsites. */
public final class P11LiveTransitionBoundary {
    public enum TaskDecision { FINISH, WAIT }
    private static volatile P11FoundationService root;

    private P11LiveTransitionBoundary() { }

    static synchronized void install(P11FoundationService service) {
        Objects.requireNonNull(service, "service");
        if (root != null) { throw new IllegalStateException("P11_LIVE_ROOT_ALREADY_INSTALLED"); }
        root = service;
    }

    private static P11LiveTransitionService service() {
        var current = root;
        return current == null ? null : current.transitions();
    }

    private static P11LiveTransitionService required() {
        var service = service();
        if (service == null) { throw new IllegalStateException("P11_LIVE_CONTROL_UNAVAILABLE"); }
        return service;
    }

    /** Existing non-P11 native callers only; absence of a service is never sufficient. */
    private static boolean observedInactive(MinecraftServer exact) {
        var current = root;
        if (current == null || exact == null || !exact.isSameThread()) { return false; }
        var observed = current.startupState(exact).orElse(null);
        return observed instanceof P11StartupLoadState.Invalid
                || observed instanceof P11StartupLoadState.Unavailable;
    }

    private static boolean observedInactive(PlayerList list) {
        return list != null && observedInactive(list.getServer())
                && list.getServer().getPlayerList() == list;
    }

    private static boolean observedInactive(ServerGamePacketListenerImpl listener) {
        var exact = listener.player.getServer();
        return observedInactive(exact) && listener.getMainThreadEventLoop() == exact;
    }

    static void ingress(P11TransitionProtocol.Request request, Connection connection,
            ICommonPacketListener listener) {
        var service = service();
        if (service != null) { service.ingress(request, connection, listener); }
    }

    public static TaskDecision configurationTaskStarted(ServerConfigurationPacketListenerImpl listener) {
        var service = service();
        if (service == null) {
            listener.disconnect(net.minecraft.network.chat.Component.translatable("gramarye.transition.unavailable"));
            return TaskDecision.WAIT;
        }
        return service.taskStarted(listener);
    }

    /** Observation only, after the exact ACK caller's original native pipeline install returns. */
    public static void configurationAcknowledged(ServerGamePacketListenerImpl previous,
            ServerConfigurationPacketListenerImpl installed) {
        var service = service();
        if (service != null) { service.configurationAcknowledged(previous, installed); }
    }

    public static void configurationFinished(ServerConfigurationPacketListenerImpl listener,
            ServerboundFinishConfigurationPacket packet, Operation<Void> original) {
        if (!listener.getMainThreadEventLoop().isSameThread()) { original.call(packet); return; }
        required().configurationFinished(listener, original, packet);
    }

    public static boolean beforeConfigurationFactory(ServerConfigurationPacketListenerImpl listener) {
        return required().beforeConfigurationFactory(listener);
    }

    public static void prepareParking(ServerConfigurationPacketListenerImpl previous,
            P11ParkingPacketListener next) { required().prepareParking(previous, next); }

    public static void parkingInstalled(ServerConfigurationPacketListenerImpl previous,
            P11ParkingPacketListener parking) { required().parkingInstalled(parking); }

    public static void parkingDisconnected(P11ParkingPacketListener parking) {
        // Ordinary global cleanup observes the exact closed physical connection; no actor lookup.
    }

    public static boolean keepAliveManaged(Connection connection) {
        var service = service();
        return service != null && service.owns(connection);
    }

    public static void performRespawn(ServerGamePacketListenerImpl listener,
            ServerboundClientCommandPacket packet, Operation<Void> original) {
        if (!listener.getMainThreadEventLoop().isSameThread()) { original.call(packet); return; }
        if (observedInactive(listener)) { original.call(packet); return; }
        required().performRespawn(listener, packet, original);
    }

    public static void switchToConfig(ServerGamePacketListenerImpl listener, Operation<Void> original) {
        if (observedInactive(listener)) { original.call(); return; }
        required().switchToConfig(listener, original);
    }

    /** Exact native original-entry observation; only retires old gameplay producers, never grants execution. */
    public static boolean configurationTickRetirementAuthorized(ServerGamePacketListenerImpl listener) {
        var service = service();
        return service != null && service.configurationTickRetirementAuthorized(listener);
    }

    static P11LiveTransitionService.Ticket currentNativeAttempt(ServerCommonPacketListenerImpl caller) {
        var service = service();
        return service == null ? null : service.current(caller);
    }

    static P11LiveTransitionService.NativeContinuity nativeContinuity(P11QualifiedSourceOwner owner) {
        var service = service();
        return service == null ? null : service.continuity(owner);
    }

    public static void requireFactoryTicket(PlayerList list, GameProfile profile, ClientInformation information) {
        if (observedInactive(list)) { return; }
        required().requireFactory(list, profile, information);
    }

    public static void requireRespawnTicket(PlayerList list, ServerPlayer actor, boolean keepEverything) {
        if (observedInactive(list) && actor.getServer() == list.getServer()) { return; }
        required().requireRespawn(list, actor, keepEverything);
    }

    public static void expectedActor(ServerPlayer actor) {
        if (observedInactive(actor.getServer())) { return; }
        required().expectedActor(actor);
    }

    public static void expectedNativeFrame(PlayerList list, Packet<?> packet) {
        if (observedInactive(list)) { return; }
        required().expectedNativeFrame(packet);
    }

    static boolean adoptSourceBody(P11QualifiedSourceOwner.Body body, P11QualifiedSourceOwner owner) {
        return required().adoptSourceBody(body, owner);
    }

    public static void nativeSend(ServerCommonPacketListenerImpl listener, Packet<?> packet,
            Operation<Void> original) {
        var service = service();
        if (service == null) { original.call(packet); }
        else { service.nativeSend(listener, packet, original); }
    }

    public static void publishDeath(ServerPlayer actor) {
        var service = service();
        if (service != null) { service.publish(actor, P11TransitionProtocol.Kind.DEATH); }
    }

    public static void publishEnd(ServerPlayer actor) {
        var service = service();
        if (service != null) { service.publish(actor, P11TransitionProtocol.Kind.END); }
    }

    static void blockersChanged(MinecraftServer server, UUID playerId) {
        var service = service();
        if (service != null && service.ownsServer(server) && server.isSameThread()) {
            service.blockersChanged(playerId);
        }
    }
}
