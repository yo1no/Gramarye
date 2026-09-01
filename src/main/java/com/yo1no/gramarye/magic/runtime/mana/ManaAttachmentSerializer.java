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
        return ManaStateCodec.decode(input);
    }

    @Override
    public Tag write(ManaState state, HolderLookup.Provider provider) {
        return ManaStateCodec.encode(state);
    }
}
