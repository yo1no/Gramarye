package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Exact, non-installing online Attachment observation with source-local ordered roots. */
sealed abstract class PlayerSkillAttachmentSourceObservation
        permits PlayerSkillAttachmentSourceObservation.Missing,
                PlayerSkillAttachmentSourceObservation.Ready,
                PlayerSkillAttachmentSourceObservation.Quarantined {
    private final MinecraftServer serverIdentity;
    private final UUID playerId;
    private final ServerPlayer playerIdentity;
    private final PlayerSkillAttachmentState stateIdentity;

    private PlayerSkillAttachmentSourceObservation(
            MinecraftServer serverIdentity,
            UUID playerId,
            ServerPlayer playerIdentity,
            PlayerSkillAttachmentState stateIdentity) {
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.playerIdentity = Objects.requireNonNull(playerIdentity, "playerIdentity");
        this.stateIdentity = stateIdentity;
    }

    static PlayerSkillAttachmentSourceObservation observe(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        var server = Objects.requireNonNull(player.getServer(), "player server");
        requireServerThread(server);
        requireOnlineIdentity(server, player.getUUID(), player);
        var type = PlayerSkillAttachments.type();
        if (!player.hasData(type)) {
            return new Missing(server, player.getUUID(), player);
        }
        var state = player.getData(type);
        return switch (state) {
            case PlayerSkillAttachmentReady ready ->
                    new Ready(server, player.getUUID(), player, ready, rootsForReady(ready));
            case PlayerSkillAttachmentPreservedRaw preserved -> new Quarantined(
                    server,
                    player.getUUID(),
                    player,
                    preserved,
                    PlayerSkillAttachmentService.UnavailableReason
                            .PRESERVED_RAW_QUARANTINE);
            case PlayerSkillAttachmentOversizeMarker oversize -> new Quarantined(
                    server,
                    player.getUUID(),
                    player,
                    oversize,
                    PlayerSkillAttachmentService.UnavailableReason.OVERSIZE_QUARANTINE);
        };
    }

    final boolean isCurrent(ServerPlayer candidate) {
        Objects.requireNonNull(candidate, "candidate");
        requireServerThread(serverIdentity);
        if (candidate != playerIdentity
                || candidate.getServer() != serverIdentity
                || !candidate.getUUID().equals(playerId)
                || serverIdentity.getPlayerList().getPlayer(playerId) != playerIdentity) {
            return false;
        }
        var type = PlayerSkillAttachments.type();
        if (stateIdentity == null) {
            return !candidate.hasData(type);
        }
        return candidate.hasData(type) && candidate.getData(type) == stateIdentity;
    }

    abstract boolean rootsAvailable();

    abstract List<SkillReference> roots();

    static List<SkillReference> rootsForReady(PlayerSkillAttachmentReady ready) {
        Objects.requireNonNull(ready, "ready");
        var references = new ArrayList<SkillReference>(
                ready.latestStates().size() + ready.equipped().size());
        ready.latestStates().stream()
                .flatMap(state -> state.pointer().stream())
                .forEach(references::add);
        ready.equipped().stream()
                .map(EquippedSkillReference::reference)
                .forEach(references::add);
        return List.copyOf(references);
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "Player skill source observation requires the server thread");
        }
    }

    private static void requireOnlineIdentity(
            MinecraftServer server, UUID playerId, ServerPlayer player) {
        if (server.getPlayerList().getPlayer(playerId) != player) {
            throw new IllegalStateException(
                    "Player skill source observation requires the exact online player");
        }
    }

    static final class Missing extends PlayerSkillAttachmentSourceObservation {
        private Missing(MinecraftServer server, UUID playerId, ServerPlayer player) {
            super(server, playerId, player, null);
        }

        @Override
        boolean rootsAvailable() {
            return true;
        }

        @Override
        List<SkillReference> roots() {
            return List.of();
        }
    }

    static final class Ready extends PlayerSkillAttachmentSourceObservation {
        private final List<SkillReference> roots;

        private Ready(
                MinecraftServer server,
                UUID playerId,
                ServerPlayer player,
                PlayerSkillAttachmentReady ready,
                List<SkillReference> roots) {
            super(server, playerId, player, Objects.requireNonNull(ready, "ready"));
            this.roots = List.copyOf(Objects.requireNonNull(roots, "roots"));
        }

        @Override
        boolean rootsAvailable() {
            return true;
        }

        @Override
        List<SkillReference> roots() {
            return roots;
        }
    }

    static final class Quarantined extends PlayerSkillAttachmentSourceObservation {
        private final PlayerSkillAttachmentService.UnavailableReason reason;

        private Quarantined(
                MinecraftServer server,
                UUID playerId,
                ServerPlayer player,
                PlayerSkillAttachmentQuarantine state,
                PlayerSkillAttachmentService.UnavailableReason reason) {
            super(server, playerId, player, Objects.requireNonNull(state, "state"));
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        PlayerSkillAttachmentService.UnavailableReason reason() {
            return reason;
        }

        @Override
        boolean rootsAvailable() {
            return false;
        }

        @Override
        List<SkillReference> roots() {
            throw new IllegalStateException("Quarantined Attachment roots are unavailable");
        }
    }
}
