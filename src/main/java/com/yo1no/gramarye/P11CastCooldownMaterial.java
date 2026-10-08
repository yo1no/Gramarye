package com.yo1no.gramarye;

import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

/** Closed native serializer/copy/publication witnesses; no caller-supplied successful-read bit. */
final class P11CastCooldownMaterial {
    private P11CastCooldownMaterial() { }
    static State capture(ServerPlayer actor) { return new State(actor, P11CastCooldownAttachments.existing(actor)); }
    static State read(IAttachmentHolder holder, P11CastCooldownData result) {
        return holder instanceof ServerPlayer actor ? new State(actor, result) : null;
    }
    static Write written(P11CastCooldownData data, Tag output) { return new Write(data, output); }
    static final class State {
        final ServerPlayer actor;
        final P11CastCooldownData data;
        private State(ServerPlayer actor, P11CastCooldownData data) { this.actor = actor; this.data = data; }
        boolean bound(ServerPlayer expected) { return actor == expected; }
        boolean current(ServerPlayer expected) { return bound(expected) && P11CastCooldownAttachments.existing(expected) == data; }
        boolean same(State other) { return other != null && actor == other.actor && data == other.data; }
    }
    static final class Write {
        private final P11CastCooldownData data;
        private final Tag output;
        private Write(P11CastCooldownData data, Tag output) { this.data = data; this.output = output; }
        boolean matches(ServerPlayer actor, Tag actual) {
            return output == actual && data == P11CastCooldownAttachments.existing(actor);
        }
    }
}
