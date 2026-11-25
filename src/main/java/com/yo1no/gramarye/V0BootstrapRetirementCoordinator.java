package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/** Main-owner-only Sunset composition. No live source, actor or server crosses its mailbox. */
final class V0BootstrapRetirementCoordinator {
    static final SkillOwnerId SEED_OWNER = new SkillOwnerId(UUID.fromString("10c92d08-46d6-3e19-87ba-3fe5afabf5d7"));
    private final SkillRuntimeService runtime;
    private final V0SlotRuntime slot;
    private final V0SlotBinding binding;
    private final V0CatalogAuthority authority;
    private final MinecraftServer server;
    private final P11CastCooldownService cooldowns;
    private V0BootstrapCleanupRequest pending;
    private final Map<SkillOwnerId, V0RuntimeCatalog.BootstrapCleanupReceipt> receipts = new HashMap<>();
    private V0RuntimeCatalog.BootstrapRosterProof seedProof;

    private V0BootstrapRetirementCoordinator(SkillRuntimeService runtime, V0SlotRuntime slot,
            V0SlotBinding binding, V0CatalogAuthority authority, MinecraftServer server) {
        this.runtime = runtime; this.slot = slot; this.binding = binding;
        this.authority = authority; this.server = server; cooldowns = runtime.v0Cooldowns();
    }

    static Optional<V0BootstrapRetirementCoordinator> begin(SkillRuntimeService runtime, V0SlotRuntime slot,
            V0SlotBinding binding, V0CatalogAuthority authority, MinecraftServer server) {
        if (runtime == null || slot == null || binding == null || authority == null || server == null
                || !authority.isOwnerThread() || !server.isSameThread() || !runtime.v0Active(slot, server)
                || runtime.v0Cooldowns() == null || !slot.sealBootstrapForSunset(authority, binding)) {
            return Optional.empty();
        }
        return Optional.of(new V0BootstrapRetirementCoordinator(runtime, slot, binding, authority, server));
    }

    boolean requestPlayerCleanup(P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body) {
        if (!ready() || pending != null || source == null || body == null || body.actor.getServer() != server
                || SEED_OWNER.value().equals(body.actor.getUUID())) { return false; }
        var owner = new SkillOwnerId(body.actor.getUUID());
        if (!receipts.containsKey(owner) && receipts.size() == 31) { return false; }
        var retirement = cooldowns.preparePolicyRetirement(server, NormalSpellPolicy.BOOTSTRAP.key(),
                source, body, body.source);
        if (retirement == null) { return false; }
        var permit = authority.permitBootstrapRetirement(new DrainProof(this, owner), cooldowns, retirement, owner);
        if (permit == null) { return false; }
        long ticket = slot.reserveBootstrapCleanupTicket(authority, binding);
        if (ticket == 0) { return false; }
        var request = new V0BootstrapCleanupRequest(authority, ticket, owner, permit);
        if (!binding.mailbox().submitBootstrapCleanup(request)) { return false; }
        pending = request;
        binding.wakeNormalCold();
        return true;
    }

    boolean requestRosterFinalization() {
        if (!ready() || pending != null) { return false; }
        long ticket = slot.reserveBootstrapCleanupTicket(authority, binding);
        if (ticket == 0) { return false; }
        var request = V0BootstrapCleanupRequest.roster(authority, ticket, List.copyOf(receipts.values()));
        if (!binding.mailbox().submitBootstrapCleanup(request)) { return false; }
        seedProof = null;
        pending = request;
        binding.wakeNormalCold();
        return true;
    }

    boolean requestSeedCleanup() {
        if (!ready() || pending != null || seedProof == null) { return false; }
        var proof = seedProof;
        seedProof = null;
        var permit = authority.permitBootstrapSeedRetirement(new DrainProof(this, SEED_OWNER), cooldowns, proof);
        if (permit == null) { return false; }
        long ticket = slot.reserveBootstrapCleanupTicket(authority, binding);
        if (ticket == 0) { return false; }
        var request = V0BootstrapCleanupRequest.seed(authority, ticket, permit, proof);
        if (!binding.mailbox().submitBootstrapCleanup(request)) { return false; }
        pending = request;
        binding.wakeNormalCold();
        return true;
    }

    Optional<V0BootstrapCleanupCompletion> takeCompletion() {
        if (!authority.isOwnerThread()) { return Optional.empty(); }
        if (!server.isSameThread() || !runtime.v0Active(slot, server)) {
            pending = null;
            seedProof = null;
            return Optional.empty();
        }
        if (pending == null) { return Optional.empty(); }
        var result = binding.mailbox().takeBootstrapCleanupCompletion().orElse(null);
        if (result == null) { return Optional.empty(); }
        var expected = pending;
        pending = null;
        if (result.authority() != authority || result.ticket() != expected.ticket()
                || !result.owner().equals(expected.owner())) { return Optional.empty(); }
        if (expected.receipts() != null) {
            seedProof = result.roster();
        } else if (!result.owner().equals(SEED_OWNER) && result.receipt() != null
                && (result.result() == V0RuntimeCatalog.BootstrapCleanup.REMOVED
                        || result.result() == V0RuntimeCatalog.BootstrapCleanup.NO_MATCH)
                && (receipts.containsKey(result.owner()) || receipts.size() < 31)) {
            receipts.put(result.owner(), result.receipt());
            seedProof = null;
        }
        return Optional.of(result);
    }

    private boolean ready() {
        return authority.isOwnerThread() && server.isSameThread() && runtime.v0Active(slot, server)
                && runtime.v0Cooldowns() == cooldowns && slot.bootstrapDrainedForSunset(authority, binding);
    }

    /** Constructed only after the coordinator's actual root observations, never from supplied booleans. */
    static final class DrainProof {
        private final V0BootstrapRetirementCoordinator coordinator;
        private final SkillOwnerId owner;
        private boolean consumed;

        private DrainProof(V0BootstrapRetirementCoordinator coordinator, SkillOwnerId owner) {
            this.coordinator = coordinator; this.owner = owner;
        }

        V0RuntimeCatalog claim(V0CatalogAuthority exact, SkillOwnerId target, P11CastCooldownService service) {
            if (consumed) { return null; }
            consumed = true;
            return exact == coordinator.authority && owner.equals(target) && service == coordinator.cooldowns
                    && coordinator.ready() ? coordinator.binding.catalog() : null;
        }
    }
}
