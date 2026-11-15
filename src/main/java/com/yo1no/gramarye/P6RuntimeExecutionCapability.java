package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.DamageCommit;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.GuardDecision;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.GuardPoint;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.GuardPort;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.Invocation;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.SpawnCommit;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0NativeOutcome;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0Reason;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;

public final class P6RuntimeExecutionCapability {
    private static final P6RuntimeExecutionCapability INSTANCE =
            new P6RuntimeExecutionCapability();

    private P6RuntimeExecutionCapability() {}

    static P6RuntimeExecutionCapability forRuntimeAdapter() {
        return INSTANCE;
    }

    static V0 forV0StatementAdapter() {
        return V0.INSTANCE;
    }

    public static final class V0 {
        private static final V0 INSTANCE = new V0();

        private V0() {}
    }

    public static final class V0Call implements GuardPort {
        private final V0P6StatementGuard guard;
        private final P9V0WorldEffectHandoff handoff;
        private boolean commitReady;

        V0Call(V0P6StatementGuard guard, P9V0WorldEffectHandoff handoff) {
            this.guard = Objects.requireNonNull(guard);
            this.handoff = Objects.requireNonNull(handoff);
        }

        public V0Reason begin(ServerPlayer actor, Invocation input) {
            return guard.begin(actor, input);
        }

        @Override
        public GuardDecision check(GuardPoint point, int stepIndex) {
            GuardDecision decision = guard.check(point, stepIndex);
            commitReady = point == GuardPoint.BEFORE_STEP && decision == GuardDecision.ALLOWED;
            return decision;
        }

        public boolean checkDebit(ServerPlayer actor) {
            return guard.checkDebit(actor);
        }

        public boolean checkCompensation(ServerPlayer actor) {
            return guard.checkCompensation(actor);
        }

        public V0NativeOutcome commitSpawn(SpawnCommit command) {
            consumeCommit();
            return handoff.commitSpawn(command);
        }

        public V0NativeOutcome commitDamage(DamageCommit command) {
            consumeCommit();
            return handoff.commitDamage(command);
        }

        private void consumeCommit() {
            if (!commitReady) {
                throw new IllegalStateException("V0 native call has no authorized primary step");
            }
            commitReady = false;
        }
    }
}
