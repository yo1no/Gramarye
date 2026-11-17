package com.yo1no.gramarye;

import com.yo1no.gramarye.P9V0WorldEffectHandoff.NativeOutcomePort;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.Invocation;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0Terminal;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0TerminalReceipt;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;

final class V0P6StatementAdapter {
    private final P8V0AppliedFactHandoff presentation;
    private final P6RuntimeExecutionCapability.V0 capability;
    private final NativeOutcomePort nativeOutcomes;

    V0P6StatementAdapter(P8ServerPresentationService presentation) {
        this(presentation, new NativeOutcomePort());
    }

    V0P6StatementAdapter(P8ServerPresentationService presentation, NativeOutcomePort nativeOutcomes) {
        this.presentation = new P8V0AppliedFactHandoff(Objects.requireNonNull(presentation));
        this.capability = P6RuntimeExecutionCapability.forV0StatementAdapter();
        this.nativeOutcomes = Objects.requireNonNull(nativeOutcomes, "nativeOutcomes");
    }

    P9V0WorldEffectHandoff nativeHandoff(V0NativeCallFrame frame) {
        return new P9V0WorldEffectHandoff(frame, nativeOutcomes);
    }

    V0TerminalReceipt execute(
            ServerPlayer actor, Invocation input, int statementOrdinal,
            V0P6StatementGuard guard, P9V0WorldEffectHandoff handoff) {
        guard.exactCall = guard.frame.matchesInvocation(actor, input)
                && guard.frame.statementOrdinal() == statementOrdinal;
        V0TerminalReceipt receipt;
        boolean normal = false;
        try {
            receipt = P6RuntimeExecutionBridge.executeV0(capability, actor, input, statementOrdinal,
                    new P6RuntimeExecutionCapability.V0Call(guard, handoff));
            if (guard.exactCall && (receipt.requestId() != guard.frame.requestId()
                    || receipt.sourceEventId() != guard.frame.sourceEventId()
                    || receipt.statementOrdinal() != guard.frame.statementOrdinal())) {
                throw new IllegalStateException("V0 statement receipt identity changed");
            }
            normal = receipt.terminal() != V0Terminal.OUTCOME_UNKNOWN
                    && receipt.terminal() != V0Terminal.CONFIRMED_APPLIED_WITH_FAILURE;
        } finally {
            P11NativeOperationBoundary.end(guard.operation, normal);
            guard.operation = null;
        }
        nativeOutcomes.completed(guard.frame, receipt);
        if (receipt.terminal() == V0Terminal.CONFIRMED_APPLIED
                || receipt.terminal() == V0Terminal.CONFIRMED_APPLIED_WITH_FAILURE) {
            presentation.offer(receipt, handoff);
        }
        return receipt;
    }
}
