package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.runtime.mana.P11ManaMaterial;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.GuardDecision;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.GuardPoint;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.Invocation;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0Reason;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;

final class V0P6StatementGuard {
    final V0NativeCallFrame frame;
    boolean exactCall;
    private boolean entered;
    private boolean entryAllowed;
    private boolean prepared;
    private boolean stepChecked;
    private P11ReceiptLedger.Source beforeDebit;
    private P11ManaMaterial.State beforeDebitMaterial;
    private P11ReceiptLedger.Source afterDebit;
    private P11ManaMaterial.State afterDebitMaterial;
    P11NativeOperationBoundary.OperationScope operation;

    V0P6StatementGuard(V0NativeCallFrame frame) {
        this.frame = Objects.requireNonNull(frame);
    }

    V0Reason begin(ServerPlayer actor, Invocation invocation) {
        if (!prepared || operation != null || !frame.matchesInvocation(actor, invocation)) {
            return V0Reason.ACTOR_OR_SOURCE_INVALID;
        }
        operation = frame.work().beginNative(actor);
        return operation == null ? V0Reason.ACTOR_OR_SOURCE_INVALID : V0Reason.NONE;
    }

    GuardDecision check(GuardPoint point, int stepIndex) {
        return switch (point) {
            case ENTRY -> {
                if (entered || stepIndex != -1) {
                    yield GuardDecision.CANCELLED;
                }
                entered = true;
                if (!exactCall) {
                    yield GuardDecision.CANCELLED;
                }
                GuardDecision decision = frame.check(point, stepIndex);
                entryAllowed = decision == GuardDecision.ALLOWED;
                yield decision;
            }
            case PRE_COMMIT -> {
                if (!entryAllowed || prepared || stepIndex != -1) {
                    yield GuardDecision.CANCELLED;
                }
                GuardDecision decision = frame.check(point, stepIndex);
                prepared = decision == GuardDecision.ALLOWED;
                yield decision;
            }
            case BEFORE_STEP -> {
                if (!prepared || operation == null || stepChecked || stepIndex != 0) {
                    yield GuardDecision.CANCELLED;
                }
                stepChecked = true;
                if (beforeDebit != null && !captureDebit()) {
                    yield GuardDecision.CANCELLED;
                }
                yield frame.check(point, stepIndex);
            }
        };
    }

    boolean checkDebit(ServerPlayer actor) {
        if (!prepared || stepChecked || actor != frame.actor() || !frame.allowsDebit(actor)) {
            return false;
        }
        P11ReceiptLedger.Source source = frame.work().paymentSource(actor).orElse(null);
        if (source == null) {
            return false;
        }
        if (operation != null) {
            beforeDebit = source;
            beforeDebitMaterial = P11ManaMaterial.capture(actor);
        }
        return true;
    }

    boolean checkCompensation(ServerPlayer actor) {
        return actor == frame.actor() && operation != null && stepChecked
                && afterDebit != null && afterDebitMaterial.isCurrent(actor)
                && frame.work().paymentSource(actor).orElse(null) == afterDebit;
    }

    private boolean captureDebit() {
        ServerPlayer actor = frame.actor();
        P11ReceiptLedger.Source current = frame.work().paymentSource(actor).orElse(null);
        if (current == null || current.dataIdentity() != beforeDebit.dataIdentity()
                || current.epoch() != beforeDebit.epoch() || beforeDebit.version() == Long.MAX_VALUE
                || current.version() != beforeDebit.version() + 1
                || beforeDebitMaterial.isCurrent(actor)) {
            return false;
        }
        afterDebit = current;
        afterDebitMaterial = P11ManaMaterial.capture(actor);
        return true;
    }
}
