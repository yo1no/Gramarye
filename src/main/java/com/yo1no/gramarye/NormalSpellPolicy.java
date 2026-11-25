package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Immutable server activation policy, distinct from any player ownership or selected-spell data. */
final class NormalSpellPolicy {
    private static final P11CooldownKey.LogicalPolicy BOOTSTRAP_KEY = new P11CooldownKey.LogicalPolicy(
            ResourceLocation.fromNamespaceAndPath("gramarye", "u0_bootstrap_qualification"));
    static final NormalSpellPolicy BOOTSTRAP = new NormalSpellPolicy(
            BOOTSTRAP_KEY, 0, 20, true, Map.of());

    private final P11CooldownKey.LogicalPolicy key;
    private final int manaFee;
    private final int cooldownTicks;
    private final boolean bootstrap;
    private final Map<SkillOwnerId, V0RuntimeCatalog.Selection> roots;

    NormalSpellPolicy(P11CooldownKey.LogicalPolicy key, int manaFee, int cooldownTicks,
            boolean bootstrap, Map<SkillOwnerId, V0RuntimeCatalog.Selection> roots) {
        this.key = Objects.requireNonNull(key, "key");
        if (manaFee < 0 || manaFee > 4 || cooldownTicks < 0 || cooldownTicks > 600 || roots.size() > 31) {
            throw new IllegalArgumentException("Normal policy is outside the captured V0 bounds");
        }
        if (bootstrap && (!key.equals(BOOTSTRAP_KEY) || manaFee != 0 || cooldownTicks != 20 || !roots.isEmpty())) {
            throw new IllegalArgumentException("Temporary Bootstrap policy is fixed");
        }
        this.manaFee = manaFee;
        this.cooldownTicks = cooldownTicks;
        this.bootstrap = bootstrap;
        this.roots = Map.copyOf(roots);
    }

    P11CooldownKey.LogicalPolicy key() { return key; }
    int manaFee() { return manaFee; }
    int cooldownTicks() { return cooldownTicks; }
    boolean bootstrap() { return bootstrap; }
    Map<SkillOwnerId, V0RuntimeCatalog.Selection> roots() { return roots; }
}
