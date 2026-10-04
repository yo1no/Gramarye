package com.yo1no.gramarye;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.neoforged.neoforge.network.payload.CommonRegisterPayload;
import net.neoforged.neoforge.network.payload.CommonVersionPayload;
import net.neoforged.neoforge.network.payload.MinecraftRegisterPayload;
import net.neoforged.neoforge.network.payload.MinecraftUnregisterPayload;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Actual actorless PLAY listener. Intentionally does not implement ServerPlayerConnection. */
public final class P11ParkingPacketListener extends ServerCommonPacketListenerImpl
        implements ServerGamePacketListener, TickablePacketListener {
    private static final Component UNEXPECTED = Component.translatable("multiplayer.disconnect.unexpected_query_response");
    private final GameProfile profile;
    private volatile ClientInformation information;
    private volatile long benignDrops;

    P11ParkingPacketListener(MinecraftServer server, Connection connection, CommonListenerCookie cookie) {
        super(server, connection, cookie);
        profile = cookie.gameProfile();
        information = cookie.clientInformation();
    }

    GameProfile profile() { return profile; }
    ClientInformation clientInformation() { return information; }
    CommonListenerCookie currentCookie() { return createCookie(information, connectionType); }
    long benignDrops() { return benignDrops; }

    @Override protected GameProfile playerProfile() { return profile; }
    @Override public boolean isAcceptingMessages() { return connection.isConnected(); }

    @Override
    public boolean shouldHandleMessage(Packet<?> packet) {
        return isAcceptingMessages()
                && (connection.getPacketListener() == this || packet instanceof ServerboundKeepAlivePacket);
    }

    @Override public void tick() { keepConnectionAlive(); }

    @Override
    public void onDisconnect(DisconnectionDetails details) {
        try { P11LiveTransitionBoundary.parkingDisconnected(this); }
        finally { super.onDisconnect(details); }
    }

    @Override
    public void handleClientInformation(ServerboundClientInformationPacket packet) {
        if (connection.getPacketListener() == this) { information = packet.information(); }
    }

    @Override
    public void handlePingRequest(ServerboundPingRequestPacket packet) {
        if (connection.getPacketListener() == this) {
            connection.send(new ClientboundPongResponsePacket(packet.getTime()));
        }
    }

    @Override
    public void handleCustomPayload(ServerboundCustomPayloadPacket packet) {
        if (connection.getPacketListener() != this) { return; }
        var payload = packet.payload();
        if (payload instanceof P11TransitionRequestPayload
                || payload instanceof MinecraftRegisterPayload
                || payload instanceof MinecraftUnregisterPayload
                || payload instanceof CommonVersionPayload
                || payload instanceof CommonRegisterPayload
                || !NetworkRegistry.isModdedPayload(payload)) {
            // The only modded gameplay registration admitted here is our player-free NETWORK handler.
            super.handleCustomPayload(packet);
        } else {
            unexpected();
        }
    }

    private void unexpected() {
        if (connection.getPacketListener() == this) { disconnect(UNEXPECTED); }
    }

    private void benignDrop() {
        if (benignDrops != Long.MAX_VALUE) { benignDrops++; }
    }

    @Override public void handleChatSessionUpdate(ServerboundChatSessionUpdatePacket packet) { benignDrop(); }
    @Override public void handleDebugSampleSubscription(ServerboundDebugSampleSubscriptionPacket packet) { benignDrop(); }
    @Override public void handleAnimate(ServerboundSwingPacket packet) { unexpected(); }
    @Override public void handleChat(ServerboundChatPacket packet) { unexpected(); }
    @Override public void handleChatCommand(ServerboundChatCommandPacket packet) { unexpected(); }
    @Override public void handleSignedChatCommand(ServerboundChatCommandSignedPacket packet) { unexpected(); }
    @Override public void handleChatAck(ServerboundChatAckPacket packet) { unexpected(); }
    @Override public void handleClientCommand(ServerboundClientCommandPacket packet) { unexpected(); }
    @Override public void handleContainerButtonClick(ServerboundContainerButtonClickPacket packet) { unexpected(); }
    @Override public void handleContainerClick(ServerboundContainerClickPacket packet) { unexpected(); }
    @Override public void handlePlaceRecipe(ServerboundPlaceRecipePacket packet) { unexpected(); }
    @Override public void handleContainerClose(ServerboundContainerClosePacket packet) { unexpected(); }
    @Override public void handleInteract(ServerboundInteractPacket packet) { unexpected(); }
    @Override public void handleMovePlayer(ServerboundMovePlayerPacket packet) { unexpected(); }
    @Override public void handlePlayerAbilities(ServerboundPlayerAbilitiesPacket packet) { unexpected(); }
    @Override public void handlePlayerAction(ServerboundPlayerActionPacket packet) { unexpected(); }
    @Override public void handlePlayerCommand(ServerboundPlayerCommandPacket packet) { unexpected(); }
    @Override public void handlePlayerInput(ServerboundPlayerInputPacket packet) { unexpected(); }
    @Override public void handleSetCarriedItem(ServerboundSetCarriedItemPacket packet) { unexpected(); }
    @Override public void handleSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket packet) { unexpected(); }
    @Override public void handleSignUpdate(ServerboundSignUpdatePacket packet) { unexpected(); }
    @Override public void handleUseItemOn(ServerboundUseItemOnPacket packet) { unexpected(); }
    @Override public void handleUseItem(ServerboundUseItemPacket packet) { unexpected(); }
    @Override public void handleTeleportToEntityPacket(ServerboundTeleportToEntityPacket packet) { unexpected(); }
    @Override public void handlePaddleBoat(ServerboundPaddleBoatPacket packet) { unexpected(); }
    @Override public void handleMoveVehicle(ServerboundMoveVehiclePacket packet) { unexpected(); }
    @Override public void handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket packet) { unexpected(); }
    @Override public void handleRecipeBookSeenRecipePacket(ServerboundRecipeBookSeenRecipePacket packet) { unexpected(); }
    @Override public void handleRecipeBookChangeSettingsPacket(ServerboundRecipeBookChangeSettingsPacket packet) { unexpected(); }
    @Override public void handleSeenAdvancements(ServerboundSeenAdvancementsPacket packet) { unexpected(); }
    @Override public void handleCustomCommandSuggestions(ServerboundCommandSuggestionPacket packet) { unexpected(); }
    @Override public void handleSetCommandBlock(ServerboundSetCommandBlockPacket packet) { unexpected(); }
    @Override public void handleSetCommandMinecart(ServerboundSetCommandMinecartPacket packet) { unexpected(); }
    @Override public void handlePickItem(ServerboundPickItemPacket packet) { unexpected(); }
    @Override public void handleRenameItem(ServerboundRenameItemPacket packet) { unexpected(); }
    @Override public void handleSetBeaconPacket(ServerboundSetBeaconPacket packet) { unexpected(); }
    @Override public void handleSetStructureBlock(ServerboundSetStructureBlockPacket packet) { unexpected(); }
    @Override public void handleSelectTrade(ServerboundSelectTradePacket packet) { unexpected(); }
    @Override public void handleEditBook(ServerboundEditBookPacket packet) { unexpected(); }
    @Override public void handleEntityTagQuery(ServerboundEntityTagQueryPacket packet) { unexpected(); }
    @Override public void handleContainerSlotStateChanged(ServerboundContainerSlotStateChangedPacket packet) { unexpected(); }
    @Override public void handleBlockEntityTagQuery(ServerboundBlockEntityTagQueryPacket packet) { unexpected(); }
    @Override public void handleSetJigsawBlock(ServerboundSetJigsawBlockPacket packet) { unexpected(); }
    @Override public void handleJigsawGenerate(ServerboundJigsawGeneratePacket packet) { unexpected(); }
    @Override public void handleChangeDifficulty(ServerboundChangeDifficultyPacket packet) { unexpected(); }
    @Override public void handleLockDifficulty(ServerboundLockDifficultyPacket packet) { unexpected(); }
    @Override public void handleConfigurationAcknowledged(ServerboundConfigurationAcknowledgedPacket packet) { unexpected(); }
    @Override public void handleChunkBatchReceived(ServerboundChunkBatchReceivedPacket packet) { unexpected(); }
}
