package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

/** Read-only, exact typed material witnesses; none is persisted or publication authority. */
public final class P11ManaMaterial {
    private P11ManaMaterial() {}

    public static State capture(ServerPlayer actor) {
        Objects.requireNonNull(actor, "actor");
        if (!actor.getServer().isSameThread()) {
            throw new IllegalStateException("P11_MANA_WRONG_THREAD");
        }
        return new State(actor, ManaAttachments.existing(actor));
    }

    static Read readResult(IAttachmentHolder holder, ManaState result) {
        return holder instanceof ServerPlayer actor
                ? new Read(new State(actor, Objects.requireNonNull(result, "result"))) : null;
    }

    static Write written(ManaState state, Tag output) {
        return new Write(state, output);
    }

    static Publication publication(State before, State after) {
        return new Publication(before, after);
    }

    /** Only the actual serializer/copy implementation can mint required-type completion. */
    public static final class Read {
        private final State result;
        private Read(State result) { this.result = result; }
        public boolean isBoundTo(ServerPlayer actor) { return result.isBoundTo(actor); }
        public State observedState() { return result; }
    }

    /** Only the same Mana owner that performed default installation or setData mints this. */
    public static final class Publication {
        private final State before;
        private final State after;
        private Publication(State before, State after) {
            this.before = Objects.requireNonNull(before, "before");
            this.after = Objects.requireNonNull(after, "after");
        }
        public boolean follows(State expected, ServerPlayer actor) {
            return before.sameState(expected) && before.isBoundTo(actor) && after.isCurrent(actor);
        }
        public State observedState() { return after; }
    }

    public static final class State {
        private final ServerPlayer actor;
        private final ManaState state;

        private State(ServerPlayer actor, ManaState state) {
            this.actor = actor;
            this.state = state;
        }

        public boolean isBoundTo(ServerPlayer candidate) { return candidate == actor; }

        public boolean isCurrent(ServerPlayer candidate) {
            return candidate == actor && actor.getServer().isSameThread()
                    && ManaAttachments.existing(actor) == state;
        }

        public boolean sameState(State other) {
            return other != null && actor == other.actor && state == other.state;
        }
    }

    public static final class Write {
        private final ManaState state;
        private final Tag output;

        private Write(ManaState state, Tag output) {
            this.state = Objects.requireNonNull(state, "state");
            this.output = Objects.requireNonNull(output, "output");
        }

        public boolean matches(ServerPlayer actor, Tag actualOutput) {
            return actor.getServer().isSameThread() && matchesOutput(actualOutput)
                    && ManaAttachments.existing(actor) == state;
        }

        boolean matchesOutput(Tag actualOutput) { return output == actualOutput; }
    }
}
