package com.yo1no.gramarye.magic.runtime.mana;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

final class ManaAttachmentSerializer implements IAttachmentSerializer<Tag, ManaState> {
    static final ManaAttachmentSerializer INSTANCE = new ManaAttachmentSerializer();

    private ManaAttachmentSerializer() {}

    @Override
    public ManaState read(
            IAttachmentHolder holder,
            Tag input,
            HolderLookup.Provider provider) {
        var result = ManaStateCodec.decode(input);
        com.yo1no.gramarye.P11NativeStorageBoundary.manaReadCompleted(
                holder, P11ManaMaterial.readResult(holder, result));
        return result;
    }

    @Override
    public Tag write(ManaState state, HolderLookup.Provider provider) {
        var result = ManaStateCodec.encode(state);
        com.yo1no.gramarye.P11NativeStorageBoundary.manaWritten(P11ManaMaterial.written(state, result));
        return result;
    }
}
