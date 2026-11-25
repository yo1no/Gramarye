package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.util.Objects;
import java.util.Set;

/** P5 owns this composition for exactly one server slot. */
final class V0SlotBinding {
    private V0RuntimeCatalog catalog;
    private V0ColdMailbox mailbox;
    private Set<V0RuntimeCatalog.Selection> zeroFeeSelections;
    private final boolean normalProfile;
    private V0NormalColdLane normalCold;
    private NormalSpellPolicy activationPolicy;

    private V0SlotBinding(boolean normalProfile) { this.normalProfile = normalProfile; }

    static V0SlotBinding create(V0RuntimeCatalog catalog, V0ColdMailbox mailbox) {
        var binding = new V0SlotBinding(false);
        binding.catalog = Objects.requireNonNull(catalog, "catalog");
        binding.mailbox = Objects.requireNonNull(mailbox, "mailbox");
        return binding;
    }

    static V0SlotBinding createNormal(V0RuntimeCatalog catalog, V0ColdMailbox mailbox, V0NormalColdLane cold) {
        return createNormal(catalog, mailbox, cold, NormalSpellPolicy.BOOTSTRAP);
    }

    static V0SlotBinding createNormal(V0RuntimeCatalog catalog, V0ColdMailbox mailbox, V0NormalColdLane cold,
            NormalSpellPolicy policy) {
        var binding = new V0SlotBinding(true);
        binding.catalog = Objects.requireNonNull(catalog, "catalog");
        binding.mailbox = Objects.requireNonNull(mailbox, "mailbox");
        binding.normalCold = Objects.requireNonNull(cold, "cold");
        binding.activationPolicy = Objects.requireNonNull(policy, "policy");
        return binding;
    }

    boolean normalPolicy() { return normalProfile; }
    NormalSpellPolicy activationPolicy() { return activationPolicy; }

    void startNormalCold() {
        if (normalProfile) {
            Objects.requireNonNull(normalCold, "closed normal binding").start();
        }
    }

    void abortUnpublished(V0CatalogAuthority authority) {
        var cold = normalCold;
        if (normalProfile && cold != null) {
            cold.abortUnpublished(authority);
            close();
        }
    }

    void wakeNormalCold() {
        var cold = normalCold;
        if (cold != null) { cold.wake(); }
    }

    /** Installed once by the engineering distribution using its own stable routes. */
    void bindZeroFeeSelections(Set<V0RuntimeCatalog.Selection> selections) {
        if (zeroFeeSelections != null) {
            throw new IllegalStateException("V0 zero-fee selection binding is sealed");
        }
        // The closed recipe has at most two logical intent routes for each of two actors.
        if (selections.size() > 4) {
            throw new IllegalArgumentException("Too many V0 zero-fee fixture selections");
        }
        zeroFeeSelections = Set.copyOf(selections);
    }

    boolean isZeroFeeSelection(V0RuntimeCatalog.Selection selected) {
        if (zeroFeeSelections != null) {
            for (var exact : zeroFeeSelections) {
                if (exact == selected) {
                    return true;
                }
            }
        }
        return false;
    }

    V0RuntimeCatalog catalog() {
        return Objects.requireNonNull(catalog, "closed V0 slot binding");
    }

    V0ColdMailbox mailbox() {
        return Objects.requireNonNull(mailbox, "closed V0 slot binding");
    }

    void close() {
        wakeNormalCold();
        catalog = null;
        mailbox = null;
        normalCold = null;
        zeroFeeSelections = Set.of();
    }
}
