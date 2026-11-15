package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.util.Objects;

/** P5 owns this composition for exactly one server slot. */
final class V0SlotBinding {
    private V0RuntimeCatalog catalog;
    private V0ColdMailbox mailbox;

    private V0SlotBinding() { }

    static V0SlotBinding create(V0RuntimeCatalog catalog, V0ColdMailbox mailbox) {
        var binding = new V0SlotBinding();
        binding.catalog = Objects.requireNonNull(catalog, "catalog");
        binding.mailbox = Objects.requireNonNull(mailbox, "mailbox");
        return binding;
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
    }
}
