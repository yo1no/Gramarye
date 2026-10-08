package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Read-only availability for a captured equipped reference, never an admission or release grant. */
final class P11CooldownPolicyProjection {
    private final PlayerSkillAttachmentService players;
    private final SkillDefinitionStoreService store;
    private final SkillSubmissionPolicyProvider policy;
    private final P5RuntimeProjector projector;

    P11CooldownPolicyProjection(PlayerSkillAttachmentService players,
            SkillDefinitionStoreService store, SkillSubmissionPolicyProvider policy,
            ProfileAvailabilityView profiles) {
        this.players = Objects.requireNonNull(players, "players");
        this.store = Objects.requireNonNull(store, "store");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.projector = new P5RuntimeProjector(Objects.requireNonNull(profiles, "profiles"));
    }

    OptionalInt observe(MinecraftServer server, ServerPlayer actor, SkillReference reference) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reference, "reference");
        if (!server.isSameThread() || actor.getServer() != server
                || server.getPlayerList().getPlayer(actor.getUUID()) != actor) {
            return OptionalInt.empty();
        }
        var playerOwner = players.ownerId(actor);
        var committedOwner = store.ownerOf(server, reference.skillId());
        if (!(playerOwner instanceof PlayerSkillAttachmentService.Available<SkillOwnerId> availablePlayer)
                || !(committedOwner instanceof SkillSubsystemResult.Available<Optional<SkillOwnerId>> availableOwner)
                || availableOwner.value().isEmpty()
                || !availablePlayer.value().equals(availableOwner.value().orElseThrow())) {
            return OptionalInt.empty();
        }
        var found = store.find(server, reference);
        if (!(found instanceof SkillSubsystemResult.Available<Optional<SkillDocument>> available)
                || available.value().isEmpty()) {
            return OptionalInt.empty();
        }
        var projection = projector.project(reference, available.value().orElseThrow(),
                policy.snapshot(server).validationContext());
        return projection instanceof P5RuntimeProjector.Projection.Available exact
                ? P9StarterSkillContent.runtimeCooldownTicks(exact.definition()) : OptionalInt.empty();
    }
}
