package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.GuardPort;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

final class P6RuntimeExecutionAdapterTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path P5_SOURCE = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye/SkillRuntimeService.java");
    private static final Path ADAPTER_SOURCE = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye/P6RuntimeExecutionPortAdapter.java");
    private static final Path HANDOFF_SOURCE = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye/P9WorldEffectHandoff.java");

    @Test
    void preBridgeUnavailableMappingCompletesWithTheExactEmptyPlan() {
        RuntimeExecutionBatch batch = P6RuntimeExecutionPortAdapter.completedEmpty();

        assertAll(
                () -> assertInstanceOf(RuntimePortOutcome.Completed.class, batch.outcome()),
                () -> assertSame(RuntimeChildPlan.EMPTY, batch.children()),
                () -> assertEquals(0, batch.children().children().size()));
    }

    @Test
    void p10TypedDamageFlowsUnmodifiedThroughAdapterAndExactWorldConversion() throws IOException {
        var adapter = Files.readString(ADAPTER_SOURCE);
        var handoff = Files.readString(HANDOFF_SOURCE);
        assertTrue(adapter.contains("action.magnitude() == 4_000L || action.magnitude() == 5_000L"));
        assertTrue(adapter.contains("hit.targetId(),\n                            action.magnitude(),"));
        assertTrue(handoff.contains("command.magnitude() != 4_000L && command.magnitude() != 5_000L"));
        assertTrue(handoff.contains("command.magnitude() % 1_000L != 0L"));
        assertTrue(handoff.contains("(float) (command.magnitude() / 1_000.0D)"));
        assertTrue(handoff.contains("convertedDamage != 4.0F && convertedDamage != 5.0F"));
        assertTrue(handoff.contains(".indirectMagic(projectile, actor), convertedDamage)"));
        assertEquals(1, occurrences(handoff, ".hurt("));
    }

    @Test
    void publishedEventIdentityMapsLosslesslyToBothBridgeIds() {
        P6RuntimeExecutionIdentity identity =
                P6RuntimeExecutionIdentity.fromPublishedEventId(9_223_372_036_854L);

        assertAll(
                () -> assertEquals(9_223_372_036_854L, identity.requestId()),
                () -> assertEquals(9_223_372_036_854L, identity.sourceEventId()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P6RuntimeExecutionIdentity.fromPublishedEventId(0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P6RuntimeExecutionIdentity.fromPublishedEventId(-1)));
    }

    @Test
    void productionConstructorAndTestInvokerKeepTheObserverFinalContract() throws Exception {
        var production = P6RuntimeExecutionPortAdapter.class.getDeclaredConstructor(
                P6RuntimeExecutionCapability.class,
                P8ServerPresentationService.class);
        var invokerOperation = P6ExecutionBridgeInvoker.class.getDeclaredMethod(
                "execute",
                P6RuntimeExecutionCapability.class,
                ServerPlayer.class,
                P6RuntimeExecutionBridge.Invocation.class,
                GuardPort.class,
                P6RuntimeExecutionBridge.WorldCommitPort.class,
                P6RuntimeExecutionBridge.AppliedFactObserver.class);

        assertAll(
                () -> assertFalse(Modifier.isPublic(production.getModifiers())),
                () -> assertFalse(Modifier.isProtected(production.getModifiers())),
                () -> assertEquals(void.class, invokerOperation.getReturnType()),
                () -> assertEquals(0, invokerOperation.getExceptionTypes().length),
                () -> assertEquals(
                        P6RuntimeExecutionBridge.AppliedFactObserver.class,
                        invokerOperation.getParameterTypes()[5]),
                () -> assertEquals(1L, Arrays.stream(P6ExecutionBridgeInvoker.class
                                .getDeclaredMethods())
                        .filter(method -> Modifier.isAbstract(method.getModifiers()))
                        .count()));
    }

    @Test
    void commitThenThrowLeavesTransferredContinuationForTheOuterP5FaultOwner()
            throws Exception {
        var predicate = P6RuntimeExecutionPortAdapter.class.getDeclaredMethod(
                "isAdapterOwnedReservationState",
                RuntimeProjectileContinuationPermit.State.class);
        predicate.setAccessible(true);

        assertAll(
                () -> assertTrue((boolean) predicate.invoke(
                        null, RuntimeProjectileContinuationPermit.State.RESERVED)),
                () -> assertFalse((boolean) predicate.invoke(
                        null, RuntimeProjectileContinuationPermit.State.OPEN)),
                () -> assertFalse((boolean) predicate.invoke(
                        null,
                        RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE)),
                () -> assertFalse((boolean) predicate.invoke(
                        null, RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT)),
                () -> assertFalse((boolean) predicate.invoke(
                        null, RuntimeProjectileContinuationPermit.State.CLOSED_AFTER_HIT)));
    }

    @Test
    void transferRejectsWrongThreadAndServerWithoutMutationAndConsumesItsOnlyAttempt()
            throws IOException {
        var service = Files.readString(P5_SOURCE);
        var transfer = sourceBlock(
                service,
                "RuntimePermitTransferDisposition transferSpawnedProjectile(");
        var opened = sourceBlock(
                service,
                "static final class Opened extends RuntimeProjectileContinuationOpenResult");
        var openMutation = transfer.indexOf(
                "permit.state = RuntimeProjectileContinuationPermit.State.OPEN;");
        var slotLookup = transfer.indexOf("var slot = slots.get(server);");
        var instanceLookup = transfer.indexOf(
                "var instance = slot.instances.get(permit.skillInstanceId);");

        assertTrue(openMutation >= 0, "OPEN transition missing");
        assertTrue(slotLookup >= 0 && instanceLookup > slotLookup, "server gates missing");
        var rejectionPrefix = transfer.substring(0, openMutation);
        var wrongThreadGate = transfer.substring(0, slotLookup);
        var wrongServerGate = transfer.substring(slotLookup, instanceLookup);
        assertAll(
                () -> assertOrdered(
                        rejectionPrefix,
                        "if (!server.isSameThread())",
                        "return RuntimePermitTransferDisposition.REJECTED;",
                        "var slot = slots.get(server);",
                        "slot == null",
                        "!permit.serverSlotToken.equals(slot.token)",
                        "return RuntimePermitTransferDisposition.REJECTED;"),
                () -> assertFalse(rejectionPrefix.contains("permit.state =")),
                () -> assertFalse(rejectionPrefix.contains(
                        "clearP9AuthenticatedActorWitness()")),
                () -> assertFalse(rejectionPrefix.contains("reservedPending--")),
                () -> assertFalse(rejectionPrefix.contains("committedPending--")),
                () -> assertFalse(rejectionPrefix.contains(
                        "activeProjectileContinuations.remove")),
                () -> assertFalse(wrongThreadGate.contains("recordP9")),
                () -> assertFalse(wrongThreadGate.contains("permit.state =")),
                () -> assertFalse(wrongServerGate.contains("recordP9")),
                () -> assertFalse(wrongServerGate.contains("permit.state =")),
                () -> assertOrdered(
                        opened,
                        "if (transferConsumed)",
                        "return RuntimePermitTransferDisposition.ALREADY_TRANSFERRED;",
                        "transferConsumed = true;",
                        "return owner.transferSpawnedProjectile("),
                () -> assertEquals(1, occurrences(opened, "transferConsumed = true;")),
                () -> assertEquals(
                        1, occurrences(opened, "return owner.transferSpawnedProjectile(")));
    }

    @Test
    void claimAndWholeInstanceCancellationRemainOneShotAndCountTheContinuation()
            throws IOException {
        var service = Files.readString(P5_SOURCE);
        var permitClaim = sourceBlock(
                service,
                "RuntimePermitClaimDisposition claimLoadedEntityHit(");
        var claim = sourceBlock(
                service,
                "private RuntimePermitClaimDisposition claimProjectileHitInSlot(");
        var cancel = sourceBlock(
                service, "private static RuntimeCancellationResult cancelInstance(");
        var addCommitted = sourceBlock(
                service, "private static void addCommittedEvent(");
        var close = sourceBlock(
                service,
                "private RuntimePermitCloseDisposition "
                        + "closeProjectileContinuationOnObservedThread(");

        assertAll(
                () -> assertOrdered(
                        permitClaim,
                        "if (state == State.CLAIMED_PENDING_DAMAGE",
                        "return RuntimePermitClaimDisposition.DUPLICATE_OR_LATE;",
                        "if (state != State.OPEN)",
                        "return RuntimePermitClaimDisposition.REJECTED;",
                        "return owner.claimProjectileHit(server, this, candidate);"),
                () -> assertEquals(
                        1,
                        occurrences(
                                permitClaim,
                                "return owner.claimProjectileHit(server, this, candidate);")),
                () -> assertOrdered(
                        claim,
                        "addCommittedEvent(slot, instance, attribution, child);",
                        "instance.reservedPending--;",
                        "attribution.reservedPending--;",
                        "slot.reservedPending--;",
                        "instance.lifetimeEvents++;",
                        "permit.state = RuntimeProjectileContinuationPermit.State."
                                + "CLAIMED_PENDING_DAMAGE;",
                        "return RuntimePermitClaimDisposition.QUEUED;"),
                () -> assertOrdered(
                        claim,
                        "slot.runtimeTick >= permit.deadlineRuntimeTick",
                        "slot.eventIndex.containsKey(permit.heldChildEventId)",
                        "new RuntimeEvent(\n"
                                + "                permit.heldChildEventId,",
                        "addCommittedEvent(slot, instance, attribution, child);"),
                () -> assertEquals(
                        1,
                        occurrences(
                                claim,
                                "permit.state = RuntimeProjectileContinuationPermit.State."
                                        + "CLAIMED_PENDING_DAMAGE;")),
                () -> assertOrdered(
                        cancel,
                        "if (instance.activeProjectileContinuation != null)",
                        "var permit = instance.activeProjectileContinuation;",
                        "var projectile = loadedProjectile(server, permit);",
                        "var disposition = permit.closeWithoutHit(",
                        "removedContinuationWork = 1;",
                        "projectile.discard();"),
                () -> assertFalse(cancel.contains(
                        "RuntimeProjectileContinuationPermit.State.")),
                () -> assertOrdered(
                        cancel,
                        "if (slot.instances.get(instanceId) != instance)",
                        "new RuntimeCancellationResult.CancelledSkillInstance(",
                        "removedContinuationWork);",
                        "if (inFlight)",
                        "new RuntimeCancellationResult.CancellationRequested(",
                        "Math.addExact(removed, removedContinuationWork));"),
                () -> assertTrue(cancel.contains(
                        "removed = Math.addExact(removed, removedContinuationWork);")),
                () -> assertOrdered(
                        addCommitted,
                        "slot.committedPending == "
                                + "MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SERVER",
                        "throw kernel(RuntimeKernelException.Code."
                                + "RESERVATION_ACCOUNTING_INVARIANT);",
                        "slot.queue.add(event);",
                        "slot.eventIndex.put(event.eventId(), event);",
                        "instance.committedPending++;",
                        "attribution.committedPending++;",
                        "slot.committedPending++;"),
                () -> assertOrdered(
                        close,
                        "var claimed = permit.state",
                        "RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE;",
                        "if (claimed)",
                        "slot.eventIndex.get(permit.heldChildEventId)",
                        "removeCommittedEvent(slot, child);",
                        "instance.activeProjectileContinuation = null;",
                        "if (claimed)",
                        "permit.state = RuntimeProjectileContinuationPermit.State."
                                + "CLOSED_AFTER_HIT;",
                        "instance.reservedPending--;",
                        "permit.state = RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT;"));
    }

    @Test
    void transferAndPostDrainCleanupKeepCapsDeadlinesAndPendingCountsExact()
            throws IOException {
        var service = Files.readString(P5_SOURCE);
        var open = sourceBlock(
                service,
                "RuntimeProjectileContinuationOpenResult openProjectileContinuation(");
        var transfer = sourceBlock(
                service,
                "RuntimePermitTransferDisposition transferSpawnedProjectile(");
        var sweep = sourceBlock(
                service, "private static void sweepActiveProjectileContinuations(");
        var dispatch = sourceBlock(service, "private void dispatchClaimed(");
        var closeActive = sourceBlock(
                service, "private static void closeActiveP9ContinuationAndDiscard(");
        var terminalize = sourceBlock(service, "private static void terminalizeCurrent(");
        var removeInstance = sourceBlock(service, "private static void maybeRemoveInstance(");

        assertAll(
                () -> assertTrue(open.contains(
                        "slot.activeProjectileContinuations.size() >= 128")),
                () -> assertTrue(open.contains(
                        "activeContinuationsForAttribution(slot, instance.attribution) >= 16")),
                () -> assertFalse(transfer.contains("reservedPending++")),
                () -> assertFalse(transfer.contains("reservedPending--")),
                () -> assertFalse(transfer.contains("reservedPending +=")),
                () -> assertFalse(transfer.contains("reservedPending -=")),
                () -> assertFalse(transfer.contains("committedPending++")),
                () -> assertFalse(transfer.contains("committedPending--")),
                () -> assertFalse(transfer.contains("committedPending +=")),
                () -> assertFalse(transfer.contains("committedPending -=")),
                () -> assertOrdered(
                        sweep,
                        "var workUnits = slot.activeProjectileContinuations.size();",
                        "if (workUnits > 128)",
                        "observed++;",
                        "? slot.runtimeTick >= permit.deadlineRuntimeTick",
                        ": slot.runtimeTick > permit.deadlineRuntimeTick;",
                        "var close = permit.closeWithoutHit(server, reason);",
                        "permits.remove();",
                        "projectile.discard();",
                        "maybeRemoveInstance(slot, instance.id);",
                        "if (observed != workUnits)"),
                () -> assertOrdered(
                        dispatch,
                        "if (event.executionData() instanceof ProjectileHitExecutionDataV0)",
                        "closeActiveP9ContinuationAndDiscard(",
                        "instance.activeProjectileContinuation == null",
                        "terminalizeCurrent(slot, instance, event);"),
                () -> assertOrdered(
                        closeActive,
                        "var permit = instance.activeProjectileContinuation;",
                        "var projectile = loadedProjectile(server, permit);",
                        "var close = permit.closeWithoutHit(server, reason);",
                        "projectile.discard();"),
                () -> assertOrdered(
                        terminalize,
                        "releaseCurrentReservationStatic(slot, instance, attribution);",
                        "removeCommittedEvent(slot, event);",
                        "instance.inFlight = false;",
                        "slot.currentEvent = null;",
                        "maybeRemoveInstance(slot, instance.id);"),
                () -> assertOrdered(
                        removeInstance,
                        "instance.activeProjectileContinuation != null",
                        "slot.instances.remove(instanceId);",
                        "attribution.activeInstances--;",
                        "instance.lease.release()",
                        "slot.leases.remove(instance.lease.reference)"));
    }

    @Test
    void postTransferThrowablesKeepIdentityForOneOuterP5CleanupPass()
            throws IOException {
        var adapter = Files.readString(ADAPTER_SOURCE);
        var handoff = Files.readString(HANDOFF_SOURCE);
        var service = Files.readString(P5_SOURCE);
        var execute = sourceBlock(adapter, "RuntimeExecutionBatch executeMapped(");
        var commitSpawn = sourceBlock(handoff, "public CommitDisposition commitSpawn(");
        var drain = sourceBlock(service, "private void drain(");
        var claim = sourceBlock(service, "RuntimePermitClaimDisposition claimProjectileHit(");
        var preserveRuntime = sourceBlock(
                service, "RuntimeException preserveRuntimeFault(");
        var preserveError = sourceBlock(service, "Error preserveErrorFault(");
        var runtimeCleanup = sourceBlock(
                service, "private static void clearSlotAfterRuntimeException(");
        var errorCleanup = sourceBlock(service, "static void clearSlotAfterError(");
        var errorVisitor = sourceBlock(
                service,
                "static final class P9ErrorCleanup");

        assertAll(
                () -> assertOrdered(
                        execute,
                        "catch (RuntimeException failure)",
                        "isAdapterOwnedReservationState(",
                        "bestEffortCloseOpened(",
                        "throw failure;",
                        "catch (Error failure)",
                        "throw failure;"),
                () -> assertOrdered(
                        commitSpawn,
                        "var transfer = opened.transferAfterAppliedSpawn(server, projectile);",
                        "catch (RuntimeException failure)",
                        "!= RuntimeProjectileContinuationPermit.State.OPEN",
                        "bestEffortClose(",
                        "bestEffortDiscard(projectile);",
                        "throw failure;",
                        "catch (Error failure)",
                        "throw failure;"),
                () -> assertOrdered(
                        drain,
                        "catch (RuntimeException primary)",
                        "throw preserveRuntimeFault(slot, primary);",
                        "catch (Error primary)",
                        "slot.p9ErrorCleanup.prepare(server);",
                        "throw preserveErrorFault(slot, primary);"),
                () -> assertOrdered(
                        claim,
                        "catch (RuntimeException primary)",
                        "throw preserveRuntimeFault(slot, primary);",
                        "catch (Error primary)",
                        "slot.p9ErrorCleanup.prepare(server);",
                        "throw preserveErrorFault(slot, primary);"),
                () -> assertOrdered(
                        preserveRuntime,
                        "enterFaultAfterRuntimeException(serverForSlot(exactSlot), exactSlot);",
                        "return primary;"),
                () -> assertOrdered(
                        preserveError,
                        "enterFaultAfterError(Objects.requireNonNull(slot, \"slot\"));",
                        "return primary;"),
                () -> assertEquals(
                        1, occurrences(runtimeCleanup, "closeAllIndexedContinuations(")),
                () -> assertOrdered(
                        runtimeCleanup,
                        "closeAllIndexedContinuations(",
                        "terminalizeRemainingP9(",
                        "slot.queue.clear();",
                        "slot.eventIndex.clear();",
                        "slot.committedPending = 0;",
                        "slot.reservedPending = 0;",
                        "slot.instances.clear();",
                        "slot.leases.clear();"),
                () -> assertOrdered(
                        errorVisitor,
                        "projectile = loadedProjectile(server, permit);",
                        "permit.state = permit.state",
                        "instance.activeProjectileContinuation = null;",
                        "projectile.discard();"),
                () -> assertEquals(
                        1,
                        occurrences(
                                errorCleanup,
                                "slot.activeProjectileContinuations.forEach("
                                        + "slot.p9ErrorCleanup);")),
                () -> assertOrdered(
                        errorCleanup,
                        "slot.activeProjectileContinuations.forEach(slot.p9ErrorCleanup);",
                        "slot.p9ErrorCleanup.clear();",
                        "slot.instances.forEach(slot.p9InstanceErrorCleanup);",
                        "slot.queue.clear();",
                        "slot.eventIndex.clear();",
                        "slot.activeProjectileContinuations.clear();",
                        "slot.committedPending = 0;",
                        "slot.reservedPending = 0;"));
    }

    private static String sourceBlock(String source, String declaration) {
        var declarationStart = source.indexOf(declaration);
        assertTrue(declarationStart >= 0, () -> "source declaration unavailable: " + declaration);
        var openBrace = source.indexOf('{', declarationStart + declaration.length());
        assertTrue(openBrace >= 0, () -> "source block unavailable: " + declaration);
        var depth = 0;
        for (var index = openBrace; index < source.length(); index++) {
            var character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(declarationStart, index + 1);
            }
        }
        throw new AssertionError("unterminated source block: " + declaration);
    }

    private static void assertOrdered(String source, String... fragments) {
        var from = 0;
        for (var fragment : fragments) {
            var found = source.indexOf(fragment, from);
            assertTrue(found >= 0, () -> "missing or out-of-order source fragment: " + fragment);
            from = found + fragment.length();
        }
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        for (var from = source.indexOf(fragment); from >= 0;
                from = source.indexOf(fragment, from + fragment.length())) {
            count++;
        }
        return count;
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root unavailable");
    }
}
