package com.yo1no.gramarye;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

final class P11CastCooldownAttachments {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "cast_cooldowns");
    static final AttachmentType<P11CastCooldownData> TYPE = AttachmentType.builder(P11CastCooldownData::unbound)
            .serialize(new Serializer()).copyOnDeath().build();
    private P11CastCooldownAttachments() { }

    static P11CastCooldownData existing(ServerPlayer actor) {
        return actor.hasData(TYPE) ? actor.getData(TYPE) : null;
    }
    static void replace(ServerPlayer actor, P11CastCooldownData expected, P11CastCooldownData replacement) {
        if (existing(actor) != expected) { throw new IllegalStateException("COOLDOWN_MATERIAL_CHANGED"); }
        var before = P11CastCooldownMaterial.capture(actor);
        actor.setData(TYPE, replacement);
        P11NativeStorageBoundary.cooldownPublished(before, P11CastCooldownMaterial.capture(actor));
    }
    private static final class Serializer implements IAttachmentSerializer<Tag, P11CastCooldownData> {
        @Override public P11CastCooldownData read(IAttachmentHolder holder, Tag input, HolderLookup.Provider provider) {
            var result = P11CastCooldownCodec.read(input);
            P11NativeStorageBoundary.cooldownReadCompleted(holder, P11CastCooldownMaterial.read(holder, result));
            return result;
        }
        @Override public Tag write(P11CastCooldownData data, HolderLookup.Provider provider) {
            Tag output = P11CastCooldownCodec.write(data);
            P11NativeStorageBoundary.cooldownWritten(P11CastCooldownMaterial.written(data, output));
            return output;
        }
    }
}
