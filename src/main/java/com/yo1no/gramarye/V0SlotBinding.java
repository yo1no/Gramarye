package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.util.Objects;
import java.util.Set;

/** P5 owns this composition for exactly one server slot. */
final class V0SlotBinding {
    private V0RuntimeCatalog catalog;
    private V0ColdMailbox mailbox;
    private Set<V0RuntimeCatalog.Selection> zeroFeeSelections;

    private V0SlotBinding() { }

    static V0SlotBinding create(V0RuntimeCatalog catalog, V0ColdMailbox mailbox) {
        var binding = new V0SlotBinding();
        binding.catalog = Objects.requireNonNull(catalog, "catalog");
        binding.mailbox = Objects.requireNonNull(mailbox, "mailbox");
        return binding;
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
        catalog = null;
        mailbox = null;
        zeroFeeSelections = Set.of();
    }
}
