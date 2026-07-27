package com.yo1no.gramarye.magic.definition.player;

import com.mojang.serialization.DataResult;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Exact-shape adapters around the existing identity/reference Codecs. */
final class PlayerSkillAttachmentCodecs {
    private static final Set<String> REFERENCE_FIELDS = Set.of("skill_id", "revision");

    private PlayerSkillAttachmentCodecs() {
    }

    static Optional<SkillId> decodeRoute(Tag input) {
        if (!(input instanceof IntArrayTag array) || array.size() != 4) {
            return Optional.empty();
        }
        return result(SkillId.CODEC.parse(NbtOps.INSTANCE, input));
    }

    static Tag encodeRoute(SkillId skillId) {
        var encoded = result(SkillId.CODEC.encodeStart(NbtOps.INSTANCE, Objects.requireNonNull(skillId, "skillId")));
        if (encoded.isEmpty() || !(encoded.orElseThrow() instanceof IntArrayTag array) || array.size() != 4) {
            throw new IllegalStateException("SkillId Codec no longer has the locked NBT route shape");
        }
        return array;
    }

    static Optional<SkillReference> decodeReference(Tag input) {
        if (!(input instanceof CompoundTag reference)
                || !reference.getAllKeys().equals(REFERENCE_FIELDS)
                || !(reference.get("skill_id") instanceof StringTag)
                || !(reference.get("revision") instanceof IntTag)) {
            return Optional.empty();
        }
        return result(SkillReference.CODEC.parse(NbtOps.INSTANCE, reference));
    }

    static CompoundTag encodeReference(SkillReference reference) {
        var encoded = result(SkillReference.CODEC.encodeStart(
                NbtOps.INSTANCE, Objects.requireNonNull(reference, "reference")));
        if (encoded.isEmpty() || !(encoded.orElseThrow() instanceof CompoundTag compound)
                || !compound.getAllKeys().equals(REFERENCE_FIELDS)
                || !(compound.get("skill_id") instanceof StringTag)
                || !(compound.get("revision") instanceof IntTag)) {
            throw new IllegalStateException("SkillReference Codec no longer has the locked NBT shape");
        }
        return compound;
    }

    private static <T> Optional<T> result(DataResult<T> result) {
        return result.result();
    }
}
