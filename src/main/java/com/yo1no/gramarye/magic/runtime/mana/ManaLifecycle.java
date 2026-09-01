package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

final class ManaLifecycle {
    private ManaLifecycle() {}

    static ManaState copy(
            ManaState source,
            IAttachmentHolder targetHolder,
            HolderLookup.Provider provider) {
        return Objects.requireNonNull(source, "source").copy();
    }
}
