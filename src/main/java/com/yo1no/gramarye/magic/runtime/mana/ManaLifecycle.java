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
        var result = Objects.requireNonNull(source, "source").copy();
        com.yo1no.gramarye.P11NativeStorageBoundary.manaReadCompleted(
                targetHolder, P11ManaMaterial.readResult(targetHolder, result));
        return result;
    }
}
