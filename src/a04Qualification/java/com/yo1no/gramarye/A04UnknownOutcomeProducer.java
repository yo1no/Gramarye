package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.runtime.mana.P11ManaMaterial;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.DamageCommit;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.SpawnCommit;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0NativeOutcome;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0Reason;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0Terminal;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0TerminalReceipt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Non-distributable qualification producer with only the adopted constructor and overrides.
 * A04UnknownQualification alone installs expectations before ingress and reads the observations.
 * These test declarations are excluded from the six-callable production surface inventory.
 */
final class A04UnknownOutcomeProducer extends P9V0WorldEffectHandoff.NativeOutcomePort {
    final boolean damageUnknown;
    final List<Map<String, Object>> receipts = new ArrayList<>(2);
    ServerPlayer expectedActor;
    MinecraftServer expectedServer;
    P11QualifiedSourceOwner expectedSource;
    boolean expectationReady;
    String failure = "";
    int rootAttempts;
    int damageAttempts;
    int childAttempts;
    int rootNativeApplied;
    int unknownReturns;
    int unknownReceipts;
    int completedCallbacks;
    boolean cleanupProbeExpected;
    Runnable negativeControls;
    V0NativeCallFrame probeFrame;
    int probeReceipts;
    P9V0WorldEffectHandoff rootOwner;
    P9V0WorldEffectHandoff damageOwner;
    V0NativeCallFrame rootFrame;
    V0NativeCallFrame damageFrame;
    SkillRuntimeService.V0CarrierBinding binding;
    P11QualifiedSourceOwner.WorkReservation work;
    P9StarterProjectile carrier;
    LivingEntity target;
    P11ManaMaterial.State debitMaterial;
    long rootManaCost;
    long damageMagnitude;
    long rootRequestId;
    long rootEventId;
    long damageRequestId;
    long damageEventId;
    float healthBefore;
    float absorptionBefore;
    long rootOperationsAtReceipt = -1;
    long damageOperationsAtReceipt = -1;
    boolean rootMaterialCurrentAtReceipt;
    boolean damageMaterialCurrentAtReceipt;

    A04UnknownOutcomeProducer(boolean damageUnknown) {
        this.damageUnknown = damageUnknown;
    }

    @Override
    V0NativeOutcome commitSpawn(P9V0WorldEffectHandoff owner, V0NativeCallFrame frame,
            SpawnCommit command, P9StarterProjectile prepared) {
        if (frame.binding().child) {
            childAttempts++;
            failure = "UNEXPECTED_CHILD_SPAWN";
            throw new IllegalStateException("A04_UNEXPECTED_CHILD_SPAWN");
        }
        if (!expectationReady || expectedActor == null || expectedServer == null || expectedSource == null
                || rootAttempts != 0 || unknownReturns != 0 || !expectedServer.isSameThread()
                || frame.actor() != expectedActor || frame.server() != expectedServer
                || !expectedSource.owns(expectedServer)
                || !expectedSource.qualifiedForV0Capture(expectedSource.nativeRecipient(expectedActor))
                || !frame.authority().isOwnerThread() || !frame.allowsNative()
                || command.requestId() <= 0 || frame.requestId() != command.requestId()
                || command.sourceEventId() <= 0 || frame.sourceEventId() != command.sourceEventId()
                || frame.statementOrdinal() != -1 || frame.instanceId().value() <= 0
                || frame.binding().lineage.value() <= 0 || frame.binding().id.value() <= 0
                || !frame.work().qualifies(expectedActor) || frame.work().paymentSource(expectedActor).isEmpty()
                || command.manaCost() != 4L
                || frame.spawn() == null || prepared.getOwner() != expectedActor
                || !frame.binding().adding(prepared) || prepared.v0EverInserted()
                || prepared.isAddedToLevel() || prepared.isRemoved()) {
            failure = "ROOT_EXACT_TUPLE_OR_PRE_NATIVE_GUARD";
            throw new IllegalStateException("A04_ROOT_EXACT_TUPLE_OR_PRE_NATIVE_GUARD");
        }
        rootAttempts++;
        rootOwner = owner;
        rootFrame = frame;
        binding = frame.binding();
        work = frame.work();
        carrier = prepared;
        rootManaCost = command.manaCost();
        rootRequestId = command.requestId();
        rootEventId = command.sourceEventId();
        debitMaterial = P11ManaMaterial.capture(expectedActor);
        if (!damageUnknown) {
            unknownReturns++;
            return V0NativeOutcome.OUTCOME_UNKNOWN;
        }
        V0NativeOutcome outcome = super.commitSpawn(owner, frame, command, prepared);
        if (outcome != V0NativeOutcome.CONFIRMED_APPLIED || !binding.opened()
                || !prepared.v0EverInserted() || !prepared.isAddedToLevel()
                || frame.level().getEntity(prepared.getId()) != prepared
                || frame.level().getEntity(prepared.getUUID()) != prepared) {
            failure = "PREREQUISITE_NATIVE_ROOT_NOT_APPLIED";
            throw new IllegalStateException("A04_PREREQUISITE_NATIVE_ROOT_NOT_APPLIED");
        }
        rootNativeApplied++;
        return outcome;
    }

    @Override
    V0NativeOutcome commitDamage(P9V0WorldEffectHandoff owner, V0NativeCallFrame frame,
            DamageCommit command) {
        if (!damageUnknown || !expectationReady || rootNativeApplied != 1 || rootFrame == null
                || damageAttempts != 0 || unknownReturns != 0 || !expectedServer.isSameThread()
                || frame.actor() != expectedActor || frame.server() != expectedServer
                || frame.authority() != rootFrame.authority() || !frame.authority().isOwnerThread()
                || frame.binding() != binding || frame.work() != work || !frame.allowsNative()
                || !frame.work().qualifies(expectedActor) || frame.work().paymentSource(expectedActor).isEmpty()
                || !expectedSource.owns(expectedServer)
                || !expectedSource.qualifiedForV0Capture(expectedSource.nativeRecipient(expectedActor))
                || !frame.instanceId().equals(rootFrame.instanceId()) || frame.binding().child
                || command.requestId() <= rootRequestId || frame.requestId() != command.requestId()
                || command.sourceEventId() <= 0 || command.sourceEventId() == rootEventId
                || frame.sourceEventId() != command.sourceEventId() || frame.statementOrdinal() != 0
                || command.manaCost() != 0 || !binding.claimed() || frame.carrier() != carrier
                || frame.target() == null || frame.target() == expectedActor
                || !frame.target().getUUID().equals(command.targetId())
                || receipts.size() != 1 || !receipts.getFirst().get("terminal").equals("CONFIRMED_APPLIED")) {
            failure = "DAMAGE_EXACT_CLAIM_OR_PRE_NATIVE_GUARD";
            throw new IllegalStateException("A04_DAMAGE_EXACT_CLAIM_OR_PRE_NATIVE_GUARD");
        }
        damageAttempts++;
        damageOwner = owner;
        damageFrame = frame;
        damageMagnitude = command.magnitude();
        damageRequestId = command.requestId();
        damageEventId = command.sourceEventId();
        target = frame.target();
        healthBefore = target.getHealth();
        absorptionBefore = target.getAbsorptionAmount();
        unknownReturns++;
        return V0NativeOutcome.OUTCOME_UNKNOWN;
    }

    @Override
    void completed(V0NativeCallFrame frame, V0TerminalReceipt receipt) {
        // Callback observations never replace the receipt or interrupt its P5 consumer.
        try {
            if (cleanupProbeExpected && unknownReceipts == 1 && probeReceipts == 0
                    && frame != rootFrame && frame != damageFrame) {
                if (receipt == null || frame.actor() != expectedActor || frame.server() != expectedServer
                        || frame.authority() != rootFrame.authority()
                        || frame.binding().cell != binding.cell || frame.binding() == binding
                        || frame.binding().lineage.equals(binding.lineage) || frame.binding().id.equals(binding.id)
                        || frame.binding().child || frame.statementOrdinal() != -1
                        || frame.requestId() <= Math.max(rootRequestId, damageRequestId)
                        || frame.sourceEventId() == rootEventId || frame.sourceEventId() == damageEventId
                        || receipt.requestId() != frame.requestId()
                        || receipt.sourceEventId() != frame.sourceEventId()
                        || receipt.statementOrdinal() != -1
                        || receipt.terminal() != V0Terminal.REJECTED_BEFORE_COMMIT
                        || receipt.reason() != V0Reason.INSUFFICIENT_MANA
                        || work.qualifies(expectedActor) || !frame.work().qualifies(expectedActor)
                        || frame.work() == work) {
                    failure = "CLEANUP_PROBE_NOT_FRESH_SAME_CELL_ACCEPTED_WORK";
                    return;
                }
                probeFrame = frame;
                probeReceipts++;
                completedCallbacks++;
                receipts.add(Map.of("requestId", receipt.requestId(), "sourceEventId", receipt.sourceEventId(),
                        "statementOrdinal", receipt.statementOrdinal(), "terminal", receipt.terminal().name(),
                        "reason", receipt.reason().name()));
                return;
            }
            boolean rootCompletion = frame == rootFrame && completedCallbacks == 0;
            boolean damageCompletion = damageUnknown && frame == damageFrame && completedCallbacks == 1;
            if (receipt == null || !rootCompletion && !damageCompletion
                    || receipt.requestId() != frame.requestId()
                    || receipt.sourceEventId() != frame.sourceEventId()
                    || receipt.statementOrdinal() != frame.statementOrdinal()) {
                failure = "COMPLETED_RECEIPT_TUPLE_CHANGED";
                return;
            }
            boolean selectedUnknown = damageCompletion || !damageUnknown;
            if (receipt.terminal() != (selectedUnknown ? V0Terminal.OUTCOME_UNKNOWN : V0Terminal.CONFIRMED_APPLIED)
                    || receipt.reason() != (selectedUnknown ? V0Reason.NATIVE_OUTCOME_UNCERTAIN : V0Reason.NONE)) {
                failure = "COMPLETED_RECEIPT_TERMINAL_CHANGED";
                return;
            }
            long operations = -1;
            for (var entry : expectedSource.diagnostics(expectedActor.getUUID()).nativeResponsibilities().roots()) {
                if (entry.kind().equals("OPERATION")) { operations = entry.count(); }
            }
            if (rootCompletion) {
                rootOperationsAtReceipt = operations;
                rootMaterialCurrentAtReceipt = debitMaterial.isCurrent(expectedActor);
            } else {
                damageOperationsAtReceipt = operations;
                damageMaterialCurrentAtReceipt = debitMaterial.isCurrent(expectedActor);
            }
            receipts.add(Map.of("requestId", receipt.requestId(), "sourceEventId", receipt.sourceEventId(),
                    "statementOrdinal", receipt.statementOrdinal(), "terminal", receipt.terminal().name(),
                    "reason", receipt.reason().name()));
            completedCallbacks++;
            if (selectedUnknown) {
                unknownReceipts++;
                if (negativeControls != null) { negativeControls.run(); }
            }
        } catch (RuntimeException observationFailure) {
            failure = "COMPLETED_OBSERVATION_FAILED";
        }
    }
}
