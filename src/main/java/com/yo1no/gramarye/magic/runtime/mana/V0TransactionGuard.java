package com.yo1no.gramarye.magic.runtime.mana;

import com.yo1no.gramarye.P6RuntimeExecutionCapability;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.Invocation;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0Reason;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;

final class V0TransactionGuard {
    private final P6RuntimeExecutionCapability.V0Call call;

    V0TransactionGuard(P6RuntimeExecutionCapability.V0Call call) {
        this.call = Objects.requireNonNull(call);
    }

    V0Reason begin(ServerPlayer actor, Invocation invocation) {
        return call.begin(actor, invocation);
    }

    boolean checkDebit(ServerPlayer actor) {
        return call.checkDebit(actor);
    }

    boolean checkCompensation(ServerPlayer actor) {
        return call.checkCompensation(actor);
    }
}
