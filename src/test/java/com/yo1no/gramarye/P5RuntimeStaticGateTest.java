package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.store.ControlledSkillPin;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.Test;

/** Source and class-surface guard for the package-private P5 implementation boundary. */
final class P5RuntimeStaticGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path ROOT_PACKAGE = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye");
    private static final Path GRAMARYE_SOURCE = ROOT_PACKAGE.resolve("Gramarye.java");
    private static final Path SERVICE_SOURCE = ROOT_PACKAGE.resolve("SkillRuntimeService.java");
    private static final Path CONFIG_SOURCE = ROOT_PACKAGE.resolve("P5ServerRuntimeConfig.java");
    private static final Path RESOLVER_SOURCE =
            ROOT_PACKAGE.resolve("P5LoadedReferenceResolver.java");
    private static final Path VOCABULARY_SOURCE =
            ROOT_PACKAGE.resolve("P5RuntimeVocabulary.java");
    private static final Path P5_AUTHORITY = PROJECT_ROOT.resolve(
            "docs/architecture/P5-A-server-runtime-event-kernel.md");
    private static final Pattern TOP_LEVEL_DECLARATION = Pattern.compile(
            "(?m)^(?:(?:public|protected|private|static|final|sealed|non-sealed|abstract)\\s+)*"
                    + "(?:class|interface|record|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern ADMIT_ROOT = Pattern.compile("\\badmitRoot\\s*\\(");
    private static final Pattern STORE_CALL = Pattern.compile(
            "\\bstoreService\\.([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(");

    @Test
    void lifecycleRegistrationsAndCompositionRetentionAreExact() throws Exception {
        var combined = productionJavaSource();
        var gramarye = Files.readString(GRAMARYE_SOURCE);
        var service = Files.readString(SERVICE_SOURCE);
        var config = Files.readString(CONFIG_SOURCE);

        assertAll(
                () -> assertEquals(1, occurrences(combined, "::handleP5RuntimeStarted")),
                () -> assertEquals(1, occurrences(combined, "::handleRuntimePost")),
                () -> assertEquals(1, occurrences(combined, "::handleRuntimeStopping")),
                () -> assertEquals(1, occurrences(combined, "::handleRuntimeStopped")),
                () -> assertEquals(1, occurrences(
                        gramarye,
                        "NeoForge.EVENT_BUS.addListener(this::handleP5RuntimeStarted);")),
                () -> assertEquals(1, occurrences(
                        service,
                        "gameBus.addListener(EventPriority.LOWEST, service::handleRuntimePost);")),
                () -> assertEquals(1, occurrences(
                        service,
                        "gameBus.addListener(service::handleRuntimeStopping);")),
                () -> assertEquals(1, occurrences(
                        service,
                        "gameBus.addListener(service::handleRuntimeStopped);")),
                () -> assertEquals(2, occurrences(config, "modBus.addListener(")),
                () -> assertEquals(1, occurrences(
                        config,
                        "modBus.addListener(this::handleRuntimeConfigReloading);")),
                () -> assertEquals(1, occurrences(
                        config,
                        "modBus.addListener(this::handleRuntimeConfigUnloading);")),
                () -> assertEquals(1, occurrences(
                        gramarye,
                        "skillRuntimeService.handleRuntimeStarted(event, limits);")),
                () -> assertTrue(gramarye.indexOf(
                                "skillRuntimeService = SkillRuntimeService.create(")
                        < gramarye.indexOf(
                                "p8ServerPresentationService.registerAfterP5(")),
                () -> assertTrue(gramarye.indexOf(
                                "p8ServerPresentationService.handleServerStarted(event);")
                        < gramarye.indexOf(
                                "skillRuntimeService.handleRuntimeStarted(event, limits);")));

        var fields = Arrays.asList(Gramarye.class.getDeclaredFields());
        assertAll(
                () -> assertEquals(1, fields.stream()
                        .filter(field -> field.getType() == P5ServerRuntimeConfig.class)
                        .count()),
                () -> assertEquals(1, fields.stream()
                        .filter(field -> field.getType() == SkillRuntimeService.class)
                        .count()),
                () -> assertTrue(fields.stream()
                        .filter(field -> field.getType() == P5ServerRuntimeConfig.class
                                || field.getType() == SkillRuntimeService.class)
                        .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                                && Modifier.isFinal(field.getModifiers()))),
                () -> assertTrue(Arrays.stream(P5ServerRuntimeConfig.class.getDeclaredFields())
                        .noneMatch(field -> field.getType() == SkillRuntimeService.class)));
    }

    @Test
    void startedConfigurationSnapshotPrecedesEverySlotAndTokenPublication() throws Exception {
        var gramarye = Files.readString(GRAMARYE_SOURCE);
        var service = Files.readString(SERVICE_SOURCE);
        var bridge = section(
                gramarye,
                "private void handleP5RuntimeStarted(",
                "/** Returns the controlled server skill subsystem port");
        assertInOrder(
                bridge,
                "p5ServerRuntimeConfig.snapshotForStarted()",
                "skillRuntimeService.handleRuntimeStarted(event, limits)");
        assertAll(
                () -> assertEquals(1, occurrences(bridge, "snapshotForStarted()")),
                () -> assertFalse(bridge.contains("catch ("),
                        "invalid configuration must escape before the service is entered"));

        var started = section(
                service,
                "void handleRuntimeStarted(",
                "RuntimeAdmissionResult admitRoot(");
        assertInOrder(
                started,
                "Objects.requireNonNull(limits, \"limits\")",
                "checkedPositiveSuccessor(serverTokenHighWater)",
                "new RuntimeServerToken(nextToken.orElseThrow())",
                "newRunningSlot(token, limits)",
                "slots.put(server, slot)",
                "serverTokenHighWater = token.value()");
    }

    @Test
    void oneImmutableSlotSnapshotIsTheOnlyRuntimeLimitRetentionRoot() throws Exception {
        var slotLimitFields = Arrays.stream(ServerSlot.class.getDeclaredFields())
                .filter(field -> field.getType() == P5RuntimeLimits.class)
                .toList();
        assertAll(
                () -> assertEquals(1, slotLimitFields.size()),
                () -> assertTrue(Modifier.isFinal(slotLimitFields.getFirst().getModifiers())),
                () -> assertTrue(Arrays.stream(RuntimeEvent.class.getRecordComponents())
                        .noneMatch(component -> component.getType() == P5RuntimeLimits.class)),
                () -> assertTrue(Arrays.stream(SkillRuntimeService.class.getDeclaredFields())
                        .noneMatch(field -> field.getType() == P5RuntimeLimits.class)));

        var source = Files.readString(SERVICE_SOURCE);
        var admission = section(
                source, "RuntimeAdmissionResult admitRoot(", "RuntimeCancellationResult cancel(");
        var drain = section(source, "private void drain(", "private void dispatchClaimed(");
        var children = section(
                source, "private RuntimeExecutionOutcome processCompletedPlan(",
                "private ChildReservation reserveForPort(");
        var budgetDecision = section(
                source, "private RuntimeBudgetDecision executionDecision(",
                "RuntimeBudgetDecision decideExecution(");
        assertAll(
                () -> assertTrue(admission.contains("slot.limits")),
                () -> assertTrue(drain.contains("slot.limits")),
                () -> assertTrue(children.contains("slot.limits")),
                () -> assertTrue(budgetDecision.contains("slot.limits")),
                () -> assertFalse(source.contains("P5ServerRuntimeConfig runtimeConfig")),
                () -> assertFalse(source.contains("P5RawServerConfigSpec")),
                () -> assertFalse(source.contains("ConfigValue")));
    }

    @Test
    void reservationsClaimsAndReentrantGuardsPrecedePortAndWholePlanPublication()
            throws Exception {
        var source = Files.readString(SERVICE_SOURCE);
        var drain = section(source, "private void drain(", "private void dispatchClaimed(");
        assertInOrder(
                drain,
                "claim(slot, event, instance, attribution)",
                "dispatchClaimed(server, slot, instance, attribution, event)");

        var invocation = section(
                source,
                "private DetachedInvocation invokeRuntimeBoundary(",
                "private RuntimeExecutionOutcome finishPort(");
        assertInOrder(
                invocation,
                "referenceResolver.resolve(server, event)",
                "slot.state != ServerSlot.State.RUNNING",
                "!server.isRunning() || server.isStopped()",
                "instance.cancellationRequested",
                "reserveForPort(slot, instance, attribution, event)",
                "new RuntimeExecutionContext(",
                "executionPort.execute(event, context)");

        var finishPort = section(
                source,
                "private RuntimeExecutionOutcome finishPort(",
                "RuntimeExecutionOutcome referenceFailureOutcome(");
        assertInOrder(
                finishPort,
                "slot.state != ServerSlot.State.RUNNING",
                "!server.isRunning() || server.isStopped()",
                "instance.cancellationRequested",
                "processCompletedPlan(");

        var reserve = section(
                source,
                "private ChildReservation reserveForPort(",
                "private void releaseCurrentReservation(");
        assertInOrder(
                reserve,
                "var eventIdStart = slot.eventSequenceHighWater",
                "slot.eventSequenceHighWater = Math.addExact(",
                "slot.currentReservationCount = capacity",
                "return new ChildReservation(");

        var children = section(
                source,
                "private RuntimeExecutionOutcome processCompletedPlan(",
                "private ChildReservation reserveForPort(");
        assertInOrder(
                children,
                "var pendingBreak = pendingBreak(",
                "childCount > reservation.capacity()",
                "var published = new RuntimeEvent[childCount]",
                "new EventId(Math.addExact(reservation.eventIdStart(), index + 1L))",
                "for (var child : published)",
                "convertReservedChildToCommitted(",
                "releaseCurrentReservation(slot, instance, attribution)",
                "instance.lifetimeEvents += childCount");
        assertFalse(source.contains("eventSequenceHighWater -="),
                "released firm reservations must leave deterministic EventId holes");
    }

    @Test
    void exactRevisionLeaseRetainReleaseAndStopCloseWiringIsClosed() throws Exception {
        var source = Files.readString(SERVICE_SOURCE);
        var acquire = section(
                source, "private LeaseAcquisition acquireLease(",
                "private static void closePinAfterLeaseConstructionFailure(");
        assertInOrder(
                acquire,
                "slot.leases.get(reference)",
                "storeService.find(server, reference)",
                "projector.project(reference, document.orElseThrow(), context)",
                "storeService.pin(server, reference)",
                "new RuntimeRevisionLease(reference, exactPin, definition)");

        var publish = section(
                source, "private static void publishRoot(",
                "private static void releaseProvisionalLease(");
        assertInOrder(
                publish,
                "slot.leases.put(lease.reference, lease)",
                "lease.retain()",
                "slot.instances.put(instance.id, instance)");

        var removal = section(
                source, "private static void maybeRemoveInstance(",
                "static void verifyQueuedIdentity(");
        assertInOrder(
                removal,
                "instance.lease.release()",
                "slot.leases.remove(instance.lease.reference)");

        var normalClear = section(
                source, "private static int clearSlotNormal(",
                "private static void clearSlotAfterRuntimeException(");
        assertInOrder(normalClear, "for (var lease : slot.leases.values())", "lease.close()",
                "slot.leases.clear()");

        var leaseSource = section(source, "final class RuntimeRevisionLease", "void close() {");
        var pinFields = Arrays.stream(RuntimeRevisionLease.class.getDeclaredFields())
                .filter(field -> field.getType() == ControlledSkillPin.class)
                .toList();
        assertAll(
                () -> assertEquals(1, pinFields.size()),
                () -> assertTrue(Modifier.isFinal(pinFields.getFirst().getModifiers())),
                () -> assertTrue(leaseSource.contains("void retain()")),
                () -> assertTrue(leaseSource.contains("boolean release()")));
        var close = source.substring(source.indexOf("void close() {", source.indexOf(
                "final class RuntimeRevisionLease")));
        assertInOrder(close, "pin.close()", "closed = true", "instanceReferences = 0");
    }

    @Test
    void breakerCleanupCannotPublishFalseCircuitBrokenOrRetainThrowable() throws Exception {
        var source = Files.readString(SERVICE_SOURCE);
        var children = section(
                source,
                "private RuntimeExecutionOutcome processCompletedPlan(",
                "private ChildReservation reserveForPort(");
        var pendingBranch = section(
                children, "if (pendingBreak != null)", "if (childCount > reservation.capacity())");
        assertInOrder(
                pendingBranch,
                "releaseCurrentReservation(slot, instance, attribution)",
                "instance.terminal = true",
                "removeInstanceQueuedAndDeferred(slot, instance.id)",
                "new RuntimeCircuitBreakerSummary(",
                "new RuntimeExecutionOutcome.CircuitBroken(summary)");
        assertFalse(pendingBranch.contains("catch ("),
                "cleanup failure must escape to the enclosing FAULTED path before a result exists");
        assertTrue(recursivelyDeclaredTypes(p5TopLevelClasses()).stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .noneMatch(field -> Throwable.class.isAssignableFrom(field.getType())));
    }

    @Test
    void postIsTheSoleRuntimeTickClockAndAbsenceCannotAdvanceIt() throws Exception {
        var source = Files.readString(SERVICE_SOURCE);
        var post = section(
                source, "void handleRuntimePost(", "private void handleRuntimeStopping(");
        var advance = section(
                source, "static RuntimeTickAdvanceResult advanceRuntimeTick(",
                "static void observeDrainStop(");
        var postOwner = SkillRuntimeService.class.getDeclaredMethod(
                "handleRuntimePost", net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class);
        assertAll(
                () -> assertEquals(1, occurrences(source, "::handleRuntimePost")),
                () -> assertEquals(1, occurrences(source, "advanceRuntimeTick(slot)")),
                () -> assertEquals(1, occurrences(
                        source, "slot.runtimeTick = Math.incrementExact(slot.runtimeTick)")),
                () -> assertTrue(isPackagePrivate(postOwner.getModifiers())),
                () -> assertFalse(Modifier.isStatic(postOwner.getModifiers())),
                () -> assertEquals(void.class, postOwner.getReturnType()),
                () -> assertEquals(0, postOwner.getExceptionTypes().length),
                () -> assertTrue(post.contains("advanceRuntimeTick(slot)")),
                () -> assertInOrder(
                        post,
                        "drain(server, slot)",
                        "sweepActiveProjectileContinuations(server, slot)",
                        "catch (RuntimeException primary)",
                        "throw preserveRuntimeFault(slot, primary)",
                        "catch (Error primary)",
                        "slot.p9ErrorCleanup.prepare(server)",
                        "throw preserveErrorFault(slot, primary)"),
                () -> assertTrue(advance.contains("RuntimeTickAdvanceResult.EXHAUSTED")),
                () -> assertFalse(source.contains("ServerTickEvent.Pre")));
    }

    @Test
    void v1AuthorityPlusTheExactS4GuardIsByteForByteTheProductionVocabulary()
            throws Exception {
        var authority = Files.readString(P5_AUTHORITY);
        var heading = authority.indexOf("### 47.3 Exact Java Declarations");
        assertTrue(heading >= 0, "missing V1 exact-declarations heading");
        var fenceStart = authority.indexOf("```java\n", heading);
        assertTrue(fenceStart >= 0, "missing V1 Java fence");
        fenceStart += "```java\n".length();
        var fenceEnd = authority.indexOf("\n```\n\n### 47.4 Result-Family Responsibilities", fenceStart);
        assertTrue(fenceEnd >= 0, "missing unique V1 Java fence terminator");
        var exactDeclarations = authority.substring(fenceStart, fenceEnd) + "\n";
        var guardDeclaration = "enum RuntimeExecutionGuardDecision {\n"
                + "    ALLOWED,\n"
                + "    CANCELLED,\n"
                + "    DEADLINE_EXCEEDED\n"
                + "}\n\n"
                + "@FunctionalInterface\n"
                + "interface RuntimeExecutionGuard {\n"
                + "    RuntimeExecutionGuardDecision check();\n"
                + "}\n\n";
        var v1ExecutionDataDeclaration = "sealed interface RuntimeExecutionData "
                + "permits NoRuntimeExecutionData {}\n\n"
                + "enum NoRuntimeExecutionData implements RuntimeExecutionData {\n"
                + "    INSTANCE\n"
                + "}\n";
        var s2ExecutionDataDeclaration = """
sealed interface RuntimeExecutionData
        permits NoRuntimeExecutionData,
                CastGeometryExecutionDataV0,
                ProjectileHitExecutionDataV0 {}

enum NoRuntimeExecutionData implements RuntimeExecutionData {
    INSTANCE
}

/** Immutable server-observed cast geometry for the canonical P9 root event. */
record CastGeometryExecutionDataV0(
        ResourceLocation dimension,
        double originX,
        double originY,
        double originZ,
        int directionXQ15,
        int directionYQ15,
        int directionZQ15,
        int profileCode) implements RuntimeExecutionData {
    CastGeometryExecutionDataV0 {
        Objects.requireNonNull(dimension, "dimension");
        if (!Double.isFinite(originX)
                || !Double.isFinite(originY)
                || !Double.isFinite(originZ)
                || originX < -30_000_000.0
                || originX >= 30_000_000.0
                || originY < -20_000_000.0
                || originY >= 20_000_000.0
                || originZ < -30_000_000.0
                || originZ >= 30_000_000.0) {
            throw new IllegalArgumentException("cast origin is outside the static domain");
        }
        if (!legalQ15(directionXQ15, directionYQ15, directionZQ15)) {
            throw new IllegalArgumentException("cast direction is not a legal Q15 tuple");
        }
        if (profileCode != 0) {
            throw new IllegalArgumentException("cast profile code must be zero");
        }
    }

    private static boolean legalQ15(int x, int y, int z) {
        return x >= -32_767 && x <= 32_767
                && y >= -32_767 && y <= 32_767
                && z >= -32_767 && z <= 32_767
                && (x != 0 || y != 0 || z != 0);
    }
}

/** Immutable direct-output family identity retained by a P9 continuation. */
record SourceFamilyKey(
        SkillInstanceId skillInstanceId,
        EventId sourceEventId,
        int producerNodeIndex,
        int outputOrdinal) {
    SourceFamilyKey {
        Objects.requireNonNull(skillInstanceId, "skillInstanceId");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        var instanceValue = skillInstanceId.value();
        if (instanceValue.getMostSignificantBits() == 0L
                && instanceValue.getLeastSignificantBits() == 0L
                || sourceEventId.value() <= 0L
                || producerNodeIndex != 0
                || outputOrdinal != 0) {
            throw new IllegalArgumentException("invalid P9 source family");
        }
    }

    boolean matches(
            SourceFamilyKey candidateFamily,
            SkillReference exactReference,
            SkillReference candidateReference,
            int sourceDerivationDepth,
            boolean includeDerived) {
        Objects.requireNonNull(candidateFamily, "candidateFamily");
        Objects.requireNonNull(exactReference, "exactReference");
        Objects.requireNonNull(candidateReference, "candidateReference");
        return sourceDerivationDepth >= 0
                && sourceDerivationDepth <= MagicSafetyCeilings.MAX_DEPTH_PER_LINEAGE
                && (includeDerived || sourceDerivationDepth == 0)
                && equals(candidateFamily)
                && exactReference.equals(candidateReference);
    }
}

/** Immutable server-collision snapshot accepted only by the owning P5 permit. */
record ProjectileHitCandidateV0(
        UUID projectileId,
        UUID targetId,
        ResourceLocation dimension,
        double hitX,
        double hitY,
        double hitZ,
        int directionXQ15,
        int directionYQ15,
        int directionZQ15) {
    ProjectileHitCandidateV0 {
        Objects.requireNonNull(projectileId, "projectileId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(dimension, "dimension");
        if (zeroUuid(projectileId) || zeroUuid(targetId)) {
            throw new IllegalArgumentException("P9 hit candidate identities must be nonzero");
        }
        if (!validStaticPosition(hitX, hitY, hitZ)) {
            throw new IllegalArgumentException("P9 hit candidate position is outside the static domain");
        }
        if (!legalQ15(directionXQ15, directionYQ15, directionZQ15)) {
            throw new IllegalArgumentException("P9 hit candidate direction is not a legal Q15 tuple");
        }
    }

    private static boolean zeroUuid(UUID value) {
        return value.getMostSignificantBits() == 0L
                && value.getLeastSignificantBits() == 0L;
    }

    private static boolean validStaticPosition(double x, double y, double z) {
        return Double.isFinite(x)
                && Double.isFinite(y)
                && Double.isFinite(z)
                && x >= -30_000_000.0
                && x < 30_000_000.0
                && y >= -20_000_000.0
                && y < 20_000_000.0
                && z >= -30_000_000.0
                && z < 30_000_000.0;
    }

    private static boolean legalQ15(int x, int y, int z) {
        return x >= -32_767 && x <= 32_767
                && y >= -32_767 && y <= 32_767
                && z >= -32_767 && z <= 32_767
                && (x != 0 || y != 0 || z != 0);
    }
}

/** Closed-vocabulary P9 hit data constructed only by the owning P5 permit. */
record ProjectileHitExecutionDataV0(
        UUID permitId,
        UUID projectileId,
        SourceFamilyKey sourceFamily,
        int sourceDerivationDepth,
        ResourceLocation dimension,
        UUID targetId,
        double hitX,
        double hitY,
        double hitZ,
        int directionXQ15,
        int directionYQ15,
        int directionZQ15) implements RuntimeExecutionData {
    ProjectileHitExecutionDataV0 {
        Objects.requireNonNull(permitId, "permitId");
        Objects.requireNonNull(projectileId, "projectileId");
        Objects.requireNonNull(sourceFamily, "sourceFamily");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(targetId, "targetId");
        if (zeroUuid(permitId) || zeroUuid(projectileId) || zeroUuid(targetId)) {
            throw new IllegalArgumentException("P9 hit identities must be nonzero");
        }
        if (sourceDerivationDepth != 0) {
            throw new IllegalArgumentException("P9 hit source depth must be zero");
        }
        if (!Double.isFinite(hitX)
                || !Double.isFinite(hitY)
                || !Double.isFinite(hitZ)
                || hitX < -30_000_000.0
                || hitX >= 30_000_000.0
                || hitY < -20_000_000.0
                || hitY >= 20_000_000.0
                || hitZ < -30_000_000.0
                || hitZ >= 30_000_000.0) {
            throw new IllegalArgumentException("P9 hit position is outside the static domain");
        }
        if (!legalQ15(directionXQ15, directionYQ15, directionZQ15)) {
            throw new IllegalArgumentException("P9 hit direction is not a legal Q15 tuple");
        }
    }

    private static boolean zeroUuid(UUID value) {
        return value.getMostSignificantBits() == 0L
                && value.getLeastSignificantBits() == 0L;
    }

    private static boolean legalQ15(int x, int y, int z) {
        return x >= -32_767 && x <= 32_767
                && y >= -32_767 && y <= 32_767
                && z >= -32_767 && z <= 32_767
                && (x != 0 || y != 0 || z != 0);
    }
}
""";
        var s4Vocabulary = exactDeclarations
                .replace(
                        "record RuntimeExecutionContext(\n",
                        guardDeclaration + "record RuntimeExecutionContext(\n")
                .replace(
                        "        RuntimeExecutionBudget executionBudget) {\n",
                        "        RuntimeExecutionBudget executionBudget,\n"
                                + "        RuntimeExecutionGuard executionGuard) {\n")
                .replace(
                        "        Objects.requireNonNull(executionBudget, \"executionBudget\");\n",
                        "        Objects.requireNonNull(executionBudget, \"executionBudget\");\n"
                                + "        Objects.requireNonNull(executionGuard, \"executionGuard\");\n");
        var s2Vocabulary = s4Vocabulary
                .replace(v1ExecutionDataDeclaration, s2ExecutionDataDeclaration)
                .replace(
                        "        RuntimeExecutionGuard executionGuard) {\n",
                        "        RuntimeExecutionGuard executionGuard,\n"
                                + "        RuntimeProjectileContinuationOpener "
                                + "projectileContinuationOpener) {\n")
                .replace(
                        "        Objects.requireNonNull(executionGuard, \"executionGuard\");\n",
                        "        Objects.requireNonNull(executionGuard, \"executionGuard\");\n"
                                + "        Objects.requireNonNull(projectileContinuationOpener, "
                                + "\"projectileContinuationOpener\");\n");
        assertAll(
                () -> assertEquals(1, occurrences(s4Vocabulary, guardDeclaration)),
                () -> assertEquals(1, occurrences(
                        s4Vocabulary, "RuntimeExecutionGuard executionGuard")),
                () -> assertEquals(1, occurrences(
                        s2Vocabulary, "RuntimeProjectileContinuationOpener "
                                + "projectileContinuationOpener")),
                () -> assertEquals(1, occurrences(
                        s2Vocabulary, s2ExecutionDataDeclaration)),
                () -> assertEquals(s2Vocabulary, Files.readString(VOCABULARY_SOURCE)));
    }

    @Test
    void p9S2ExecutionDataAndNinthContextInventoriesAreExact() {
        assertAll(
                () -> assertTrue(RuntimeExecutionData.class.isSealed()),
                () -> assertTrue(isPackagePrivate(RuntimeExecutionData.class.getModifiers())),
                () -> assertEquals(
                        List.of(
                                NoRuntimeExecutionData.class,
                                CastGeometryExecutionDataV0.class,
                                ProjectileHitExecutionDataV0.class),
                        Arrays.asList(RuntimeExecutionData.class.getPermittedSubclasses())),
                () -> assertTrue(List.of(
                                CastGeometryExecutionDataV0.class,
                                SourceFamilyKey.class,
                                ProjectileHitCandidateV0.class,
                                ProjectileHitExecutionDataV0.class)
                        .stream()
                        .allMatch(type -> type.isRecord()
                                && Modifier.isFinal(type.getModifiers())
                                && isPackagePrivate(type.getModifiers()))),
                () -> assertEquals(
                        List.of(
                                "net.minecraft.resources.ResourceLocation dimension",
                                "double originX",
                                "double originY",
                                "double originZ",
                                "int directionXQ15",
                                "int directionYQ15",
                                "int directionZQ15",
                                "int profileCode"),
                        recordComponentSignatures(CastGeometryExecutionDataV0.class)),
                () -> assertEquals(
                        List.of(
                                "com.yo1no.gramarye.magic.api.id.SkillInstanceId "
                                        + "skillInstanceId",
                                "com.yo1no.gramarye.magic.api.id.EventId sourceEventId",
                                "int producerNodeIndex",
                                "int outputOrdinal"),
                        recordComponentSignatures(SourceFamilyKey.class)),
                () -> assertEquals(
                        List.of(
                                "java.util.UUID projectileId",
                                "java.util.UUID targetId",
                                "net.minecraft.resources.ResourceLocation dimension",
                                "double hitX",
                                "double hitY",
                                "double hitZ",
                                "int directionXQ15",
                                "int directionYQ15",
                                "int directionZQ15"),
                        recordComponentSignatures(ProjectileHitCandidateV0.class)),
                () -> assertEquals(
                        List.of(
                                "java.util.UUID permitId",
                                "java.util.UUID projectileId",
                                "com.yo1no.gramarye.SourceFamilyKey sourceFamily",
                                "int sourceDerivationDepth",
                                "net.minecraft.resources.ResourceLocation dimension",
                                "java.util.UUID targetId",
                                "double hitX",
                                "double hitY",
                                "double hitZ",
                                "int directionXQ15",
                                "int directionYQ15",
                                "int directionZQ15"),
                        recordComponentSignatures(ProjectileHitExecutionDataV0.class)),
                () -> assertEquals(
                        List.of(
                                "net.minecraft.server.MinecraftServer server",
                                "com.yo1no.gramarye.magic.definition.validation."
                                        + "ValidatedSkillDefinition definition",
                                "com.yo1no.gramarye.magic.definition.validation."
                                        + "ValidatedNodeDefinition node",
                                "long currentRuntimeTick",
                                "com.yo1no.gramarye.RuntimeServerToken serverSlotToken",
                                "com.yo1no.gramarye.ResolvedRuntimeReferenceContext "
                                        + "resolvedReferences",
                                "com.yo1no.gramarye.RuntimeExecutionBudget executionBudget",
                                "com.yo1no.gramarye.RuntimeExecutionGuard executionGuard",
                                "com.yo1no.gramarye.RuntimeProjectileContinuationOpener "
                                        + "projectileContinuationOpener"),
                        recordComponentSignatures(RuntimeExecutionContext.class)),
                () -> assertTrue(RuntimeExecutionContext.class.isRecord()),
                () -> assertTrue(Modifier.isFinal(RuntimeExecutionContext.class.getModifiers())),
                () -> assertTrue(isPackagePrivate(
                        RuntimeExecutionContext.class.getModifiers())));
    }

    @Test
    void p9S3OpenerResultPermitAndDispositionInventoriesAreExact() throws Exception {
        var openerFields = declaredFieldSignatures(RuntimeProjectileContinuationOpener.class);
        var openerMethods = Arrays.stream(
                        RuntimeProjectileContinuationOpener.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .toList();
        var openerConstructor = RuntimeProjectileContinuationOpener.class.getDeclaredConstructor(
                SkillRuntimeService.class,
                MinecraftServer.class,
                ServerSlot.class,
                RuntimeEvent.class,
                ChildReservation.class);
        assertAll(
                () -> assertTrue(Modifier.isFinal(
                        RuntimeProjectileContinuationOpener.class.getModifiers())),
                () -> assertTrue(isPackagePrivate(
                        RuntimeProjectileContinuationOpener.class.getModifiers())),
                () -> assertEquals(
                        Set.of(
                                "com.yo1no.gramarye.SkillRuntimeService owner",
                                "net.minecraft.server.MinecraftServer server",
                                "com.yo1no.gramarye.ServerSlot slot",
                                "com.yo1no.gramarye.RuntimeEvent sourceEvent",
                                "com.yo1no.gramarye.ChildReservation reservation",
                                "boolean consumed"),
                        openerFields),
                () -> assertTrue(Arrays.stream(
                                RuntimeProjectileContinuationOpener.class.getDeclaredFields())
                        .allMatch(field -> Modifier.isPrivate(field.getModifiers()))),
                () -> assertTrue(Arrays.stream(
                                RuntimeProjectileContinuationOpener.class.getDeclaredFields())
                        .filter(field -> !field.getName().equals("consumed"))
                        .allMatch(field -> Modifier.isFinal(field.getModifiers()))),
                () -> assertFalse(Modifier.isFinal(
                        RuntimeProjectileContinuationOpener.class
                                .getDeclaredField("consumed").getModifiers())),
                () -> assertEquals(1,
                        RuntimeProjectileContinuationOpener.class
                                .getDeclaredConstructors().length),
                () -> assertTrue(isPackagePrivate(openerConstructor.getModifiers())),
                () -> assertEquals(1, openerMethods.size()),
                () -> assertEquals(
                        "openProjectileContinuation", openerMethods.getFirst().getName()),
                () -> assertEquals(
                        List.of(ActionOutputKind.class, int.class),
                        Arrays.asList(openerMethods.getFirst().getParameterTypes())),
                () -> assertEquals(
                        RuntimeProjectileContinuationOpenResult.class,
                        openerMethods.getFirst().getReturnType()),
                () -> assertTrue(isPackagePrivate(openerMethods.getFirst().getModifiers())));

        var result = RuntimeProjectileContinuationOpenResult.class;
        var opened = RuntimeProjectileContinuationOpenResult.Opened.class;
        var rejected = RuntimeProjectileContinuationOpenResult.Rejected.class;
        var openedConstructor = opened.getDeclaredConstructor(
                RuntimeProjectileContinuationPermit.class,
                UUID.class,
                SkillRuntimeService.class);
        var rejectedConstructor = rejected.getDeclaredConstructor(
                RuntimeProjectileContinuationOpenRejectionReason.class);
        assertAll(
                () -> assertTrue(result.isSealed()),
                () -> assertTrue(Modifier.isAbstract(result.getModifiers())),
                () -> assertTrue(isPackagePrivate(result.getModifiers())),
                () -> assertEquals(
                        List.of(opened, rejected),
                        Arrays.asList(result.getPermittedSubclasses())),
                () -> assertEquals(0, result.getDeclaredFields().length),
                () -> assertEquals(1, result.getDeclaredConstructors().length),
                () -> assertTrue(Modifier.isPrivate(
                        result.getDeclaredConstructors()[0].getModifiers())),
                () -> assertTrue(Modifier.isFinal(opened.getModifiers())),
                () -> assertEquals(
                        Set.of(
                                "com.yo1no.gramarye.RuntimeProjectileContinuationPermit permit",
                                "java.util.UUID plannedProjectileId",
                                "com.yo1no.gramarye.SkillRuntimeService owner",
                                "boolean transferConsumed"),
                        declaredFieldSignatures(opened)),
                () -> assertTrue(Arrays.stream(opened.getDeclaredFields())
                        .allMatch(field -> Modifier.isPrivate(field.getModifiers()))),
                () -> assertTrue(Arrays.stream(opened.getDeclaredFields())
                        .filter(field -> !field.getName().equals("transferConsumed"))
                        .allMatch(field -> Modifier.isFinal(field.getModifiers()))),
                () -> assertFalse(Modifier.isFinal(opened
                        .getDeclaredField("transferConsumed").getModifiers())),
                () -> assertEquals(1, opened.getDeclaredConstructors().length),
                () -> assertTrue(isPackagePrivate(openedConstructor.getModifiers())),
                () -> assertEquals(
                        Set.of(
                                "com.yo1no.gramarye.RuntimeProjectileContinuationPermit "
                                        + "permit()",
                                "java.util.UUID plannedProjectileId()",
                                "com.yo1no.gramarye.RuntimePermitTransferDisposition "
                                        + "transferAfterAppliedSpawn("
                                        + "net.minecraft.server.MinecraftServer,"
                                        + "com.yo1no.gramarye.P9StarterProjectile)"),
                        declaredMethodSignatures(opened)),
                () -> assertTrue(Arrays.stream(opened.getDeclaredMethods())
                        .filter(method -> !method.isSynthetic())
                        .allMatch(method -> isPackagePrivate(method.getModifiers()))),
                () -> assertTrue(Modifier.isFinal(rejected.getModifiers())),
                () -> assertEquals(
                        Set.of("com.yo1no.gramarye."
                                + "RuntimeProjectileContinuationOpenRejectionReason reason"),
                        declaredFieldSignatures(rejected)),
                () -> assertTrue(Arrays.stream(rejected.getDeclaredFields()).allMatch(field ->
                        Modifier.isPrivate(field.getModifiers())
                                && Modifier.isFinal(field.getModifiers()))),
                () -> assertEquals(1, rejected.getDeclaredConstructors().length),
                () -> assertTrue(isPackagePrivate(rejectedConstructor.getModifiers())),
                () -> assertEquals(
                        Set.of("com.yo1no.gramarye."
                                + "RuntimeProjectileContinuationOpenRejectionReason reason()"),
                        declaredMethodSignatures(rejected)),
                () -> assertEquals(
                        List.of(
                                "CAPACITY_UNAVAILABLE",
                                "LIFECYCLE_UNAVAILABLE",
                                "INVARIANT_REJECTED"),
                        Arrays.stream(RuntimeProjectileContinuationOpenRejectionReason.values())
                                .map(Enum::name)
                                .toList()));

        var permit = RuntimeProjectileContinuationPermit.class;
        var permitConstructor = permit.getDeclaredConstructor(
                SkillRuntimeService.class,
                RuntimeServerToken.class,
                com.yo1no.gramarye.magic.api.id.SkillInstanceId.class,
                RuntimeBudgetAttribution.class,
                SkillReference.class,
                net.minecraft.resources.ResourceLocation.class,
                SourceFamilyKey.class,
                int.class,
                com.yo1no.gramarye.magic.api.id.EventId.class,
                long.class,
                UUID.class,
                UUID.class);
        var permitState = Arrays.stream(permit.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("State"))
                .findFirst()
                .orElseThrow();
        assertAll(
                () -> assertTrue(Modifier.isFinal(permit.getModifiers())),
                () -> assertTrue(isPackagePrivate(permit.getModifiers())),
                () -> assertEquals(
                        Set.of(
                                "com.yo1no.gramarye.SkillRuntimeService owner",
                                "com.yo1no.gramarye.RuntimeProjectileContinuationPermit$Mode mode",
                                "com.yo1no.gramarye.RuntimeServerToken serverSlotToken",
                                "com.yo1no.gramarye.magic.api.id.SkillInstanceId "
                                        + "skillInstanceId",
                                "com.yo1no.gramarye.RuntimeBudgetAttribution "
                                        + "budgetAttribution",
                                "com.yo1no.gramarye.magic.definition.document.SkillReference "
                                        + "exactReference",
                                "net.minecraft.resources.ResourceLocation dimension",
                                "com.yo1no.gramarye.SourceFamilyKey sourceFamily",
                                "int sourceDerivationDepth",
                                "com.yo1no.gramarye.magic.api.id.EventId heldChildEventId",
                                "long deadlineRuntimeTick",
                                "java.util.UUID permitId",
                                "java.util.UUID plannedProjectileId",
                                "com.yo1no.gramarye.RuntimeProjectileContinuationPermit$State "
                                        + "state"),
                        declaredFieldSignatures(permit)),
                () -> assertTrue(Modifier.isPrivate(
                        permit.getDeclaredField("owner").getModifiers())),
                () -> assertTrue(Arrays.stream(permit.getDeclaredFields())
                        .filter(field -> !field.getName().equals("state"))
                        .allMatch(field -> Modifier.isFinal(field.getModifiers()))),
                () -> assertFalse(Modifier.isFinal(
                        permit.getDeclaredField("state").getModifiers())),
                () -> assertEquals(1, Arrays.stream(permit.getDeclaredFields())
                        .filter(field -> field.getType() == SkillRuntimeService.class)
                        .count()),
                () -> assertEquals(2, permit.getDeclaredConstructors().length),
                () -> assertTrue(isPackagePrivate(permitConstructor.getModifiers())),
                () -> assertEquals(
                        Set.of(
                                "com.yo1no.gramarye.RuntimePermitClaimDisposition "
                                        + "claimLoadedEntityHit("
                                        + "net.minecraft.server.MinecraftServer,"
                                        + "com.yo1no.gramarye.ProjectileHitCandidateV0)",
                                "com.yo1no.gramarye.RuntimePermitCloseDisposition "
                                        + "closeWithoutHit(net.minecraft.server.MinecraftServer,"
                                        + "com.yo1no.gramarye.ProjectileClosureReason)"),
                        declaredMethodSignatures(permit)),
                () -> assertEquals(2, permit.getDeclaredClasses().length),
                () -> assertTrue(permitState.isEnum()),
                () -> assertTrue(isPackagePrivate(permitState.getModifiers())),
                () -> assertEquals(
                        List.of(
                                "RESERVED",
                                "OPEN",
                                "CLAIMED_PENDING_DAMAGE",
                                "CLOSED_NO_HIT",
                                "CLOSED_AFTER_HIT"),
                        Arrays.stream(permitState.getEnumConstants())
                                .map(value -> ((Enum<?>) value).name())
                                .toList()),
                () -> assertEquals(
                        List.of(
                                "SPAWN_NOT_APPLIED",
                                "BLOCK_OR_INVALID_HIT",
                                "RANGE_EXHAUSTED",
                                "AGE_EXHAUSTED",
                                "DEADLINE_REACHED",
                                "OWNER_INVALIDATED",
                                "ENTITY_OR_LEVEL_REMOVED",
                                "RELOAD_INVALIDATED",
                                "SERVER_STOPPED",
                                "RUNTIME_FAULT",
                                "CLAIM_REJECTED",
                                "DAMAGE_TERMINAL"),
                        Arrays.stream(ProjectileClosureReason.values())
                                .map(Enum::name)
                                .toList()),
                () -> assertEquals(
                        List.of("CLOSED", "ALREADY_CLOSED", "REJECTED"),
                        Arrays.stream(RuntimePermitCloseDisposition.values())
                                .map(Enum::name)
                                .toList()),
                () -> assertEquals(
                        List.of("TRANSFERRED", "REJECTED", "ALREADY_TRANSFERRED"),
                        Arrays.stream(RuntimePermitTransferDisposition.values())
                                .map(Enum::name)
                                .toList()),
                () -> assertEquals(
                        List.of("QUEUED", "REJECTED", "DUPLICATE_OR_LATE"),
                        Arrays.stream(RuntimePermitClaimDisposition.values())
                                .map(Enum::name)
                                .toList()));
    }

    @Test
    void p9S3OwnerIndexReloadDiagnosticsAndLiveContinuationSurfaceAreExact()
            throws Exception {
        var serviceFields = Arrays.asList(SkillRuntimeService.class.getDeclaredFields());
        var reloadFields = serviceFields.stream()
                .filter(field -> field.getType() == AtomicBoolean.class)
                .toList();
        var requestReload = SkillRuntimeService.class.getDeclaredMethod(
                "requestP9ReloadInvalidation");
        var completeReload = SkillRuntimeService.class.getDeclaredMethod(
                "completeP9Reload", MinecraftServer.class);
        var activeIndexFields = recursivelyDeclaredTypes(p5TopLevelClasses()).stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> field.getType() == Map.class)
                .filter(field -> field.getGenericType().getTypeName().equals(
                        "java.util.Map<java.util.UUID, "
                                + "com.yo1no.gramarye.RuntimeProjectileContinuationPermit>"))
                .toList();
        var terminalRingFields = Arrays.stream(ServerSlot.class.getDeclaredFields())
                .filter(field -> field.getType()
                        == ServerSlot.P9TerminalDiagnostic[].class)
                .toList();
        assertAll(
                () -> assertEquals(1, reloadFields.size()),
                () -> assertEquals("p9ReloadCloseRequested", reloadFields.getFirst().getName()),
                () -> assertTrue(Modifier.isPrivate(reloadFields.getFirst().getModifiers())),
                () -> assertTrue(Modifier.isFinal(reloadFields.getFirst().getModifiers())),
                () -> assertEquals(1, Arrays.stream(SkillRuntimeService.class.getDeclaredMethods())
                        .filter(method -> method.getName().equals(
                                "requestP9ReloadInvalidation"))
                        .count()),
                () -> assertEquals(1, Arrays.stream(SkillRuntimeService.class.getDeclaredMethods())
                        .filter(method -> method.getName().equals("completeP9Reload"))
                        .count()),
                () -> assertEquals(void.class, requestReload.getReturnType()),
                () -> assertEquals(void.class, completeReload.getReturnType()),
                () -> assertTrue(isPackagePrivate(requestReload.getModifiers())),
                () -> assertTrue(isPackagePrivate(completeReload.getModifiers())),
                () -> assertEquals(1, activeIndexFields.size()),
                () -> assertEquals(
                        "activeProjectileContinuations",
                        activeIndexFields.getFirst().getName()),
                () -> assertTrue(Modifier.isFinal(
                        activeIndexFields.getFirst().getModifiers())),
                () -> assertEquals(1, terminalRingFields.size()),
                () -> assertEquals("p9TerminalRing", terminalRingFields.getFirst().getName()),
                () -> assertTrue(Modifier.isFinal(
                        terminalRingFields.getFirst().getModifiers())),
                () -> assertEquals(
                        List.of(
                                "CAST_ACCEPTED",
                                "INSTANCE_PINNED",
                                "NODE0_MATCHED",
                                "CONTINUATION_OPENED",
                                "SPAWN_RESOLVED",
                                "SPAWN_COMMIT_RESULT",
                                "PROJECTILE_ACTIVE",
                                "CAST_PRESENTATION_OFFERED",
                                "HIT_CLAIM_RESULT",
                                "NODE1_QUEUED",
                                "NODE1_MATCHED",
                                "DAMAGE_RESOLVED",
                                "DAMAGE_COMMIT_RESULT",
                                "HIT_PRESENTATION_OFFERED",
                                "TERMINAL_CAUSE",
                                "CLEANUP_DISPOSITION"),
                        Arrays.stream(P9RuntimeDiagnosticStage.values())
                                .map(Enum::name)
                                .toList()),
                () -> assertEquals(
                        List.of("RELEASED", "ERROR_DEFERRED"),
                        Arrays.stream(P9RuntimeCleanupDisposition.values())
                                .map(Enum::name)
                                .toList()));

        var source = Files.readString(SERVICE_SOURCE);
        var production = productionJavaSource();
        var requestSource = section(
                source,
                "void requestP9ReloadInvalidation()",
                "void completeP9Reload(");
        var completeSource = section(
                source,
                "void completeP9Reload(",
                "RuntimeProjectileContinuationOpenResult openProjectileContinuation(");
        var childValidationSource = section(
                source,
                "private static Optional<InvalidEventReason> validateChildShape(",
                "private static boolean stableTokensMatch(");
        var childPublicationSource = section(
                source,
                "private RuntimeExecutionOutcome processCompletedPlan(",
                "private ChildReservation reserveForPort(");
        var transferSource = section(
                source,
                "RuntimePermitTransferDisposition transferSpawnedProjectile(",
                "RuntimePermitClaimDisposition claimProjectileHit(");
        var claimSource = section(
                source,
                "RuntimePermitClaimDisposition claimProjectileHit(",
                "private RuntimePermitClaimDisposition rejectClaimAndClose(");
        var rejectClaimSource = section(
                source,
                "private RuntimePermitClaimDisposition rejectClaimAndClose(",
                "RuntimePermitCloseDisposition closeProjectileContinuation(");
        var continuationCloseSource = section(
                source,
                "private RuntimePermitCloseDisposition "
                        + "closeProjectileContinuationOnObservedThread(",
                "private static RuntimeProjectileContinuationOpenResult "
                        + "continuationRejected(");
        var sweepSource = section(
                source,
                "private static void sweepActiveProjectileContinuations(",
                "private void handleRuntimeStopping(");
        var cancelInstanceSource = section(
                source,
                "private static RuntimeCancellationResult cancelInstance(",
                "private static int removeInstanceQueuedAndDeferred(");
        var openedSource = section(
                source,
                "static final class Opened extends RuntimeProjectileContinuationOpenResult {",
                "static final class Rejected extends RuntimeProjectileContinuationOpenResult {");
        var activeDiagnosticSource = section(
                source,
                "static final class P9ActiveDiagnostic {",
                "record P9TerminalDiagnostic(");
        var spawnDiagnosticSource = section(
                source,
                "private static void recordP9SpawnResult(",
                "private static void recordP9HitClaimResult(");
        var hitDiagnosticSource = section(
                source,
                "private static void recordP9HitClaimResult(",
                "private static void recordP9Terminal(");
        var activeSpawnRecorder = ServerSlot.P9ActiveDiagnostic.class.getDeclaredMethod(
                "recordSpawnResult",
                RuntimePermitTransferDisposition.class,
                long.class);
        var activeHitRecorder = ServerSlot.P9ActiveDiagnostic.class.getDeclaredMethod(
                "recordHitClaimResult",
                long.class,
                long.class,
                ProjectileHitCandidateV0.class,
                RuntimePermitClaimDisposition.class,
                long.class);
        assertAll(
                () -> assertEquals(
                        "void requestP9ReloadInvalidation() {\n"
                                + "        p9ReloadCloseRequested.set(true);\n"
                                + "    }\n\n    ",
                        requestSource),
                () -> assertInOrder(
                        completeSource,
                        "!server.isSameThread()",
                        "invalidateP9WorkPreservingPrimary("
                                + "\n                server, slot, "
                                + "ProjectileClosureReason.RELOAD_INVALIDATED)",
                        "p9ReloadCloseRequested.set(false)"),
                () -> assertEquals(1, occurrences(source, "new int[16]")),
                () -> assertEquals(1, occurrences(source, "new long[16]")),
                () -> assertEquals(1,
                        occurrences(source, "new P9TerminalDiagnostic[256]")),
                () -> assertInOrder(
                        source,
                        "slot.p9TerminalRing[slot.p9TerminalWriteIndex] = "
                                + "new ServerSlot.P9TerminalDiagnostic(",
                        "slot.p9TerminalWriteIndex = (slot.p9TerminalWriteIndex + 1) "
                                + "% slot.p9TerminalRing.length",
                        "if (slot.p9TerminalCount < slot.p9TerminalRing.length)",
                        "slot.p9TerminalCount++"),
                () -> assertInOrder(
                        source,
                        "trace.profileCode,",
                        "trace.spawnCommitResultCode,",
                        "trace.hitPermitIdMostSignificantBits,",
                        "trace.hitProjectileIdMostSignificantBits,",
                        "trace.hitTargetIdMostSignificantBits,",
                        "trace.hitXBits,",
                        "trace.hitDirectionXQ15,",
                        "trace.hitClaimResultCode,",
                        "trace.stageCount,"),
                () -> assertEquals(1, occurrences(
                        source, "P9RuntimeDiagnosticStage.CONTINUATION_OPENED")),
                () -> assertEquals(2, occurrences(
                        source, "P9RuntimeDiagnosticStage.SPAWN_RESOLVED")),
                () -> assertEquals(1, occurrences(
                        source, "P9RuntimeDiagnosticStage.SPAWN_COMMIT_RESULT")),
                () -> assertEquals(1, occurrences(
                        source, "P9RuntimeDiagnosticStage.PROJECTILE_ACTIVE")),
                () -> assertEquals(0, occurrences(
                        source, "P9RuntimeDiagnosticStage.CAST_PRESENTATION_OFFERED")),
                () -> assertEquals(2, occurrences(
                        source, "P9RuntimeDiagnosticStage.HIT_CLAIM_RESULT")),
                () -> assertEquals(1, occurrences(
                        source, "P9RuntimeDiagnosticStage.NODE1_QUEUED")),
                () -> assertEquals(0, occurrences(
                        source, "P9RuntimeDiagnosticStage.NODE1_MATCHED")),
                () -> assertEquals(0, occurrences(
                        source, "P9RuntimeDiagnosticStage.DAMAGE_RESOLVED")),
                () -> assertEquals(0, occurrences(
                        source, "P9RuntimeDiagnosticStage.DAMAGE_COMMIT_RESULT")),
                () -> assertEquals(0, occurrences(
                        source, "P9RuntimeDiagnosticStage.HIT_PRESENTATION_OFFERED")),
                () -> assertTrue(production.contains("ProjectileHitCandidateV0")),
                () -> assertTrue(production.contains("P9StarterProjectile")),
                () -> assertTrue(production.contains("RuntimePermitTransferDisposition")),
                () -> assertTrue(production.contains("RuntimePermitClaimDisposition")),
                () -> assertTrue(production.contains("transferAfterAppliedSpawn(")),
                () -> assertTrue(production.contains("claimLoadedEntityHit(")),
                () -> assertFalse(p5SourceText().contains(".addFreshEntity(")),
                () -> assertEquals(
                        1, occurrences(production, "new ProjectileHitExecutionDataV0(")),
                () -> assertTrue(childValidationSource.contains(
                        "var p9Hit = child.executionData() instanceof "
                                + "ProjectileHitExecutionDataV0")),
                () -> assertFalse(childPublicationSource.contains(
                        "ProjectileHitExecutionDataV0")),
                () -> assertInOrder(
                        openedSource,
                        "if (transferConsumed)",
                        "return RuntimePermitTransferDisposition.ALREADY_TRANSFERRED",
                        "transferConsumed = true",
                        "return owner.transferSpawnedProjectile("),
                () -> assertTrue(transferSource.contains(
                        "var detachedAndCurrentReservationCount = "
                                + "slot.currentReservationCount + 1;")),
                () -> assertTrue(transferSource.contains(
                        "instance.reservedPending != detachedAndCurrentReservationCount")),
                () -> assertTrue(transferSource.contains(
                        "attribution.reservedPending < detachedAndCurrentReservationCount")),
                () -> assertTrue(transferSource.contains(
                        "slot.reservedPending < detachedAndCurrentReservationCount")),
                () -> assertInOrder(
                        transferSource,
                        "levelForDimension(server, permit.dimension)",
                        "level.getEntity(plannedProjectileId) != projectile",
                        "!projectile.isAddedToLevel()"),
                () -> assertInOrder(
                        transferSource,
                        "permit.state = RuntimeProjectileContinuationPermit.State.OPEN",
                        "instance.clearP9AuthenticatedActorWitness()",
                        "recordP9SpawnResult(",
                        "RuntimePermitTransferDisposition.TRANSFERRED"),
                () -> assertInOrder(
                        activeDiagnosticSource,
                        "spawnCommitResultCode = recordsProjectileActive ? 1 : 2",
                        "record(P9RuntimeDiagnosticStage.SPAWN_RESOLVED, runtimeTick)",
                        "record(P9RuntimeDiagnosticStage.SPAWN_COMMIT_RESULT, runtimeTick)",
                        "record(P9RuntimeDiagnosticStage.PROJECTILE_ACTIVE, runtimeTick)"),
                () -> assertInOrder(
                        continuationCloseSource,
                        "reason == ProjectileClosureReason.SPAWN_NOT_APPLIED",
                        "recordP9SpawnResult(",
                        "RuntimePermitTransferDisposition.REJECTED"),
                () -> assertFalse(spawnDiagnosticSource.contains("catch (")),
                () -> assertFalse(hitDiagnosticSource.contains("catch (")),
                () -> assertEquals(
                        0,
                        activeSpawnRecorder.getModifiers()
                                & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE)),
                () -> assertEquals(
                        0,
                        activeHitRecorder.getModifiers()
                                & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE)),
                () -> assertTrue(claimSource.contains(
                        "slot.runtimeTick >= permit.deadlineRuntimeTick")),
                () -> assertInOrder(
                        claimSource,
                        "if (!server.isSameThread())",
                        "return RuntimePermitClaimDisposition.REJECTED",
                        "return claimProjectileHitInSlot(",
                        "catch (RuntimeException primary)",
                        "throw preserveRuntimeFault(slot, primary)",
                        "catch (Error primary)",
                        "slot.p9ErrorCleanup.prepare(server)",
                        "throw preserveErrorFault(slot, primary)"),
                () -> assertInOrder(
                        claimSource,
                        "BlockPos.containing(",
                        "level.isInWorldBounds(hitPosition)",
                        "level.isLoaded(hitPosition)",
                        "level.getWorldBorder().isWithinBounds("),
                () -> assertInOrder(
                        claimSource,
                        "validateChildShape(",
                        ".isPresent()",
                        "return rejectClaimAndClose(server, slot, instance, permit, candidate)",
                        "addCommittedEvent(slot, instance, attribution, child)"),
                () -> assertInOrder(
                        rejectClaimSource,
                        "recordP9HitClaimResult(",
                        "RuntimePermitClaimDisposition.REJECTED",
                        "closeProjectileContinuation("),
                () -> assertInOrder(
                        activeDiagnosticSource,
                        "hitPermitIdMostSignificantBits = permitIdMostSignificantBits",
                        "hitProjectileIdMostSignificantBits =",
                        "hitTargetIdMostSignificantBits =",
                        "hitXBits = Double.doubleToRawLongBits(candidate.hitX())",
                        "hitClaimResultCode = result.ordinal() + 1",
                        "record(P9RuntimeDiagnosticStage.HIT_CLAIM_RESULT, runtimeTick)",
                        "record(P9RuntimeDiagnosticStage.NODE1_QUEUED, runtimeTick)"),
                () -> assertTrue(sweepSource.contains(
                        "? slot.runtimeTick >= permit.deadlineRuntimeTick")),
                () -> assertTrue(sweepSource.contains(
                        ": slot.runtimeTick > permit.deadlineRuntimeTick")),
                () -> assertInOrder(
                        sweepSource,
                        "permit.closeWithoutHit(server, reason)",
                        "permits.remove()",
                        "projectile.discard()",
                        "maybeRemoveInstance(slot, instance.id)"),
                () -> assertInOrder(
                        cancelInstanceSource,
                        "var removedContinuationWork = 0",
                        "permit.closeWithoutHit(",
                        "removedContinuationWork = 1",
                        "new RuntimeCancellationResult.CancellationRequested("
                                + "\n                    Math.addExact(removed, "
                                + "removedContinuationWork))",
                        "removed = Math.addExact(removed, removedContinuationWork)",
                        "new RuntimeCancellationResult.CancelledSkillInstance(removed)"));
    }

    @Test
    void p9ExactActorWitnessHolderIdentityOperationsAndTransitiveRetentionAreExact()
            throws Exception {
        var instanceType = ServerSlot.InstanceState.class;
        var witness = instanceType.getDeclaredField("p9AuthenticatedActorWitness");
        var constructor = instanceType.getDeclaredConstructor(
                SkillInstanceId.class,
                RuntimeSkillInstanceSequence.class,
                RuntimeBudgetAttribution.class,
                RuntimeRevisionLease.class,
                ServerPlayer.class);
        var hasWitness = instanceType.getDeclaredMethod(
                "hasP9AuthenticatedActorWitness", ServerPlayer.class);
        var clearWitness = instanceType.getDeclaredMethod(
                "clearP9AuthenticatedActorWitness");
        var serviceSource = Files.readString(SERVICE_SOURCE);
        var instanceSource = section(
                serviceSource,
                "static final class InstanceState {",
                "static final class P9ActiveDiagnostic {");
        var normalizedInstanceSource = instanceSource.replaceAll("\\s+", "");

        var persistentP5Types = List.of(
                SkillRuntimeService.class,
                ServerSlot.class,
                ServerSlot.InstanceState.class,
                RuntimeProjectileContinuationOpener.class,
                RuntimeProjectileContinuationOpenResult.Opened.class,
                RuntimeProjectileContinuationPermit.class);
        var retainedPlayers = persistentP5Types.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields())
                        .filter(field -> field.getType() == ServerPlayer.class)
                        .map(field -> type.getName() + "#" + field.getName()))
                .toList();
        var permitOwner = RuntimeProjectileContinuationPermit.class.getDeclaredField("owner");
        var entityWitness = P9StarterProjectile.class.getDeclaredField(
                "authenticatedCasterIdentity");
        var serviceSlots = SkillRuntimeService.class.getDeclaredField("slots");
        var slotInstances = ServerSlot.class.getDeclaredField("instances");
        var externalWitnessCallsites = new ArrayList<String>();
        try (var paths = Files.walk(PROJECT_ROOT.resolve("src/main/java"))) {
            for (var path : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.equals(SERVICE_SOURCE))
                    .toList()) {
                var otherSource = Files.readString(path);
                for (var operation : List.of(
                        "new ServerSlot.InstanceState(",
                        "hasP9AuthenticatedActorWitness(",
                        "clearP9AuthenticatedActorWitness(")) {
                    if (otherSource.contains(operation)) {
                        externalWitnessCallsites.add(path + ":" + operation);
                    }
                }
            }
        }

        assertAll(
                () -> assertEquals(ServerPlayer.class, witness.getType()),
                () -> assertTrue(Modifier.isPrivate(witness.getModifiers())),
                () -> assertFalse(Modifier.isFinal(witness.getModifiers())),
                () -> assertFalse(Modifier.isStatic(witness.getModifiers())),
                () -> assertEquals(1, instanceType.getDeclaredConstructors().length),
                () -> assertTrue(isPackagePrivate(constructor.getModifiers())),
                () -> assertEquals(0, constructor.getExceptionTypes().length),
                () -> assertTrue(isPackagePrivate(hasWitness.getModifiers())),
                () -> assertFalse(Modifier.isStatic(hasWitness.getModifiers())),
                () -> assertEquals(boolean.class, hasWitness.getReturnType()),
                () -> assertEquals(0, hasWitness.getExceptionTypes().length),
                () -> assertTrue(isPackagePrivate(clearWitness.getModifiers())),
                () -> assertFalse(Modifier.isStatic(clearWitness.getModifiers())),
                () -> assertEquals(void.class, clearWitness.getReturnType()),
                () -> assertEquals(0, clearWitness.getParameterCount()),
                () -> assertEquals(0, clearWitness.getExceptionTypes().length),
                () -> assertEquals(
                        List.of("com.yo1no.gramarye.ServerSlot$InstanceState"
                                + "#p9AuthenticatedActorWitness"),
                        retainedPlayers),
                () -> assertEquals(SkillRuntimeService.class, permitOwner.getType()),
                () -> assertTrue(Modifier.isPrivate(permitOwner.getModifiers())),
                () -> assertTrue(Modifier.isFinal(permitOwner.getModifiers())),
                () -> assertEquals(ServerPlayer.class, entityWitness.getType()),
                () -> assertTrue(Modifier.isPrivate(entityWitness.getModifiers())),
                () -> assertTrue(Modifier.isFinal(entityWitness.getModifiers())),
                () -> assertTrue(Modifier.isTransient(entityWitness.getModifiers())),
                () -> assertEquals(
                        "java.util.IdentityHashMap<net.minecraft.server.MinecraftServer, "
                                + "com.yo1no.gramarye.ServerSlot>",
                        serviceSlots.getGenericType().getTypeName()),
                () -> assertEquals(
                        "java.util.Map<com.yo1no.gramarye.magic.api.id.SkillInstanceId, "
                                + "com.yo1no.gramarye.ServerSlot$InstanceState>",
                        slotInstances.getGenericType().getTypeName()),
                () -> assertTrue(externalWitnessCallsites.isEmpty(),
                        () -> "external witness callsites: " + externalWitnessCallsites),
                () -> assertEquals(1, occurrences(
                        serviceSource, "new ServerSlot.InstanceState(")),
                () -> assertEquals(2, occurrences(
                        serviceSource, "hasP9AuthenticatedActorWitness(")),
                () -> assertTrue(normalizedInstanceSource.contains(
                        "privateServerPlayerp9AuthenticatedActorWitness;")),
                () -> assertTrue(normalizedInstanceSource.contains(
                        "ServerPlayerp9AuthenticatedActorWitness){")),
                () -> assertTrue(normalizedInstanceSource.contains(
                        "this.p9AuthenticatedActorWitness=p9AuthenticatedActorWitness;")),
                () -> assertTrue(normalizedInstanceSource.contains(
                        "booleanhasP9AuthenticatedActorWitness(ServerPlayercandidate){"
                                + "returncandidate!=null"
                                + "&&p9AuthenticatedActorWitness==candidate;}")),
                () -> assertTrue(normalizedInstanceSource.contains(
                        "voidclearP9AuthenticatedActorWitness(){"
                                + "p9AuthenticatedActorWitness=null;}")),
                () -> assertEquals(1, Arrays.stream(instanceType.getDeclaredMethods())
                        .filter(method -> method.getName().equals(
                                "hasP9AuthenticatedActorWitness"))
                        .count()),
                () -> assertEquals(1, Arrays.stream(instanceType.getDeclaredMethods())
                        .filter(method -> method.getName().equals(
                                "clearP9AuthenticatedActorWitness"))
                        .count()),
                () -> assertTrue(Arrays.stream(instanceType.getDeclaredMethods())
                        .noneMatch(method -> method.getName().matches(
                                "(?i).*(get|set|bind|replace).*P9AuthenticatedActor.*"))),
                () -> assertTrue(Arrays.stream(P6RuntimeExecutionBridge.class.getDeclaredFields())
                        .noneMatch(field -> field.getType() == ServerPlayer.class)),
                () -> assertTrue(Arrays.stream(P6RuntimeExecutionPortAdapter.class
                                .getDeclaredFields())
                        .noneMatch(field -> field.getType() == ServerPlayer.class)));
    }

    @Test
    void p9ActorBindingBypassRejectionAndCurrentActorCheckpointsAreClosed()
            throws Exception {
        var serviceSource = Files.readString(SERVICE_SOURCE);
        var genericAdmission = methodSource(
                serviceSource,
                "RuntimeAdmissionResult admitRoot(MinecraftServer server, "
                        + "RuntimeRootEventSpec spec)");
        var authenticatedIngress = methodSource(
                serviceSource,
                "RuntimeAdmissionResult admitAuthenticatedPlayerCast(");
        var actorAdmission = section(
                serviceSource,
                "private RuntimeAdmissionResult admitRootWithP9Actor(",
                "private RuntimeAdmissionResult acquireAndPublishRoot(");
        var acquisition = section(
                serviceSource,
                "private RuntimeAdmissionResult acquireAndPublishRoot(",
                "RuntimeCancellationResult cancel(");
        var publication = methodSource(serviceSource, "private static void publishRoot(");
        var invocation = section(
                serviceSource,
                "private DetachedInvocation invokeRuntimeBoundary(",
                "private RuntimeExecutionOutcome finishPort(");
        var opener = section(
                serviceSource,
                "RuntimeProjectileContinuationOpenResult openProjectileContinuation(",
                "RuntimePermitCloseDisposition closeProjectileContinuation(");
        var predicateSource = methodSource(
                serviceSource,
                "private static boolean isCurrentP9AuthenticatedActor(");
        var predicate = SkillRuntimeService.class.getDeclaredMethod(
                "isCurrentP9AuthenticatedActor",
                MinecraftServer.class,
                ServerSlot.InstanceState.class,
                ServerPlayer.class,
                ResourceLocation.class);
        var actorAdmissionMethod = SkillRuntimeService.class.getDeclaredMethod(
                "admitRootWithP9Actor",
                MinecraftServer.class,
                RuntimeRootEventSpec.class,
                ServerPlayer.class);

        assertAll(
                () -> assertTrue(Modifier.isPrivate(predicate.getModifiers())),
                () -> assertTrue(Modifier.isStatic(predicate.getModifiers())),
                () -> assertEquals(boolean.class, predicate.getReturnType()),
                () -> assertEquals(0, predicate.getExceptionTypes().length),
                () -> assertEquals(1, Arrays.stream(SkillRuntimeService.class
                                .getDeclaredMethods())
                        .filter(method -> method.getName().equals(
                                "isCurrentP9AuthenticatedActor"))
                        .count()),
                () -> assertTrue(Modifier.isPrivate(actorAdmissionMethod.getModifiers())),
                () -> assertFalse(Modifier.isStatic(actorAdmissionMethod.getModifiers())),
                () -> assertEquals(
                        RuntimeAdmissionResult.class, actorAdmissionMethod.getReturnType()),
                () -> assertEquals(0, actorAdmissionMethod.getExceptionTypes().length),
                () -> assertTrue(actorAdmission.contains("p9AuthenticatedActorWitness")),
                () -> assertTrue(actorAdmission.contains("resolvedP9Actor")),
                () -> assertEquals(6, occurrences(
                        serviceSource, "isCurrentP9AuthenticatedActor(")),
                () -> assertInOrder(
                        predicateSource,
                        "!server.isSameThread()",
                        "!server.isRunning()",
                        "server.isStopped()",
                        "candidate == null",
                        "instance.attribution instanceof PlayerRuntimeBudgetAttribution player",
                        "var playerId = player.playerId().value()",
                        "!candidate.getUUID().equals(playerId)",
                        "server.getPlayerList().getPlayer(",
                        "instance.hasP9AuthenticatedActorWitness(candidate)",
                        "candidate.getServer()",
                        "var level = candidate.serverLevel()",
                        "level.getServer()",
                        "candidate.isRemoved()",
                        "candidate.isAlive()",
                        "level.dimension().location()",
                        "candidate.connection != null",
                        "candidate.connection.isAcceptingMessages()"),
                () -> assertInOrder(
                        genericAdmission,
                        "spec.executionData() instanceof CastGeometryExecutionDataV0",
                        "InvalidEventReason.INVALID_EXECUTION_DATA",
                        "return admitRootWithP9Actor(server, spec, null)"),
                () -> assertFalse(genericAdmission.contains(
                        "p9AuthenticatedActorWitness")),
                () -> assertTrue(authenticatedIngress.contains(
                        "return admitRootWithP9Actor(")),
                () -> assertTrue(authenticatedIngress.contains("actor")),
                () -> assertInOrder(
                        acquisition,
                        "new ServerSlot.InstanceState(",
                        "isCurrentP9AuthenticatedActor(",
                        "publishRoot("),
                () -> assertTrue(acquisition.contains("p9AuthenticatedActorWitness")),
                () -> assertInOrder(
                        publication,
                        "slot.instances.put(instance.id, instance)",
                        "addCommittedEvent("),
                () -> assertInOrder(
                        invocation,
                        "referenceResolver.resolve(server, event)",
                        "isCurrentP9AuthenticatedActor(",
                        "reserveForPort(",
                        "actorForGuard != null",
                        "isCurrentP9AuthenticatedActor(",
                        "runtimeExecutionGuardDecision(slot, instance, event)"),
                () -> assertEquals(2, occurrences(
                        invocation, "isCurrentP9AuthenticatedActor(")),
                () -> assertInOrder(
                        opener,
                        "isCurrentP9AuthenticatedActor(",
                        "reservation.attach(permit)"),
                () -> assertFalse(predicateSource.contains(".equals(candidate)")),
                () -> assertFalse(predicateSource.contains("clearP9AuthenticatedActorWitness")),
                () -> assertFalse(serviceSource.contains(
                        "new P9StarterProjectile(")),
                () -> assertEquals(1, occurrences(
                        serviceSource, "transferAfterAppliedSpawn(")),
                () -> assertEquals(1, occurrences(
                        serviceSource, "claimLoadedEntityHit(")));
    }

    @Test
    void rootAdmissionHasOneTokenSafeCallerAndMandatoryOverridesAreExact()
            throws Exception {
        var callsites = 0;
        try (var paths = Files.walk(PROJECT_ROOT.resolve("src/main/java"))) {
            for (var path : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                var matcher = ADMIT_ROOT.matcher(Files.readString(path));
                while (matcher.find()) {
                    callsites++;
                }
            }
        }
        assertEquals(1, callsites,
                "generic admitRoot must have only its actor-free declaration");
        assertTrue(Files.readString(SERVICE_SOURCE).contains(
                "RuntimeAdmissionResult admitRoot(MinecraftServer server, RuntimeRootEventSpec spec)"));
        var serviceSource = Files.readString(SERVICE_SOURCE);
        assertEquals(1, occurrences(
                serviceSource,
                "RuntimeAdmissionResult admitAuthenticatedPlayerCast("));
        assertEquals(0, occurrences(serviceSource, "return admitRoot("));
        assertEquals(2, occurrences(serviceSource, "return admitRootWithP9Actor("));
        var authenticatedIngressSource = section(
                serviceSource,
                "RuntimeAdmissionResult admitAuthenticatedPlayerCast(",
                "void requestP9ReloadInvalidation(");
        var compactAuthenticatedIngress = authenticatedIngressSource.replaceAll("\\s+", " ");
        assertAll(
                () -> assertInOrder(
                        authenticatedIngressSource,
                        "Objects.requireNonNull(geometry, \"geometry\")",
                        "!server.isSameThread()",
                        "p9ReloadCloseRequested.get()",
                        "return admitRootWithP9Actor("),
                () -> assertTrue(compactAuthenticatedIngress.contains(
                        "new RuntimeScheduleSpec( 0, 100, "
                                + "RuntimeSchedulePersistence.MEMORY_ONLY)")),
                () -> assertTrue(authenticatedIngressSource.contains(
                        "new PlayerOrigin(slot.token, actorLevel.dimension(), "
                                + "playerId)")),
                () -> assertInOrder(
                        authenticatedIngressSource,
                        "var actorId = actor.getUUID()",
                        "var actorLevel = actor.serverLevel()",
                        "actor.getServer() != server",
                        "server.getPlayerList().getPlayer(actorId) != actor",
                        "actor.isRemoved()",
                        "!actor.isAlive()",
                        "!geometry.dimension().equals(actorLevel.dimension().location())",
                        "return admitRootWithP9Actor("),
                () -> assertTrue(authenticatedIngressSource.contains("geometry),")),
                () -> assertTrue(authenticatedIngressSource.contains("actor);")),
                () -> assertFalse(authenticatedIngressSource.contains(
                        "NoRuntimeExecutionData.INSTANCE")));

        var authenticatedIngress = SkillRuntimeService.class.getDeclaredMethod(
                "admitAuthenticatedPlayerCast",
                MinecraftServer.class,
                ServerPlayer.class,
                SkillReference.class,
                CastGeometryExecutionDataV0.class);
        assertFalse(Modifier.isPublic(authenticatedIngress.getModifiers()));
        assertFalse(Modifier.isProtected(authenticatedIngress.getModifiers()));
        assertFalse(Modifier.isPrivate(authenticatedIngress.getModifiers()));
        assertFalse(Modifier.isStatic(authenticatedIngress.getModifiers()));
        assertEquals(RuntimeAdmissionResult.class, authenticatedIngress.getReturnType());
        assertEquals(0, authenticatedIngress.getExceptionTypes().length);

        var owner = P7AuthenticatedPlayerCastIngress.class;
        var override = owner.getDeclaredMethod(
                "authorizeAndAdmit",
                MinecraftServer.class,
                ServerPlayer.class,
                int.class,
                P7ServerAuthorizationBoundary.AdvisoryTargetCheck.class);
        var publicProtectedMethods = Arrays.stream(owner.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())
                        || Modifier.isProtected(method.getModifiers()))
                .toList();
        assertAll(
                () -> assertTrue(Modifier.isFinal(owner.getModifiers())),
                () -> assertFalse(Modifier.isPublic(owner.getModifiers())),
                () -> assertFalse(Modifier.isProtected(owner.getModifiers())),
                () -> assertEquals(
                        List.of(P7ServerAuthorizationBoundary.RootIngressPort.class),
                        List.of(owner.getInterfaces())),
                () -> assertEquals(List.of(override), publicProtectedMethods),
                () -> assertTrue(Modifier.isPublic(override.getModifiers())),
                () -> assertFalse(Modifier.isStatic(override.getModifiers())),
                () -> assertFalse(override.isBridge()),
                () -> assertFalse(override.isSynthetic()),
                () -> assertEquals(
                        P7ServerAuthorizationBoundary.AdmissionDisposition.class,
                        override.getReturnType()),
                () -> assertEquals(0, override.getExceptionTypes().length),
                () -> assertEquals(0, Arrays.stream(owner.getDeclaredConstructors())
                        .filter(constructor -> Modifier.isPublic(constructor.getModifiers())
                                || Modifier.isProtected(constructor.getModifiers()))
                        .count()),
                () -> assertEquals(0, Arrays.stream(owner.getDeclaredFields())
                        .filter(field -> Modifier.isPublic(field.getModifiers())
                                || Modifier.isProtected(field.getModifiers()))
                        .count()),
                () -> assertEquals(0, Arrays.stream(owner.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                && Modifier.isStatic(method.getModifiers()))
                        .count()));

        var handoffOwner = P8AppliedFactHandoff.class;
        var observerOverride = handoffOwner.getDeclaredMethod(
                "observe", P6RuntimeExecutionBridge.AppliedFact.class);
        var handoffConstructor = handoffOwner.getDeclaredConstructor(
                P8ServerPresentationService.class,
                RuntimeEvent.class,
                RuntimeExecutionContext.class);
        var handoffFields = Arrays.stream(handoffOwner.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .toList();
        var handoffPublicProtectedMethods = Arrays.stream(handoffOwner.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())
                        || Modifier.isProtected(method.getModifiers()))
                .toList();
        assertAll(
                () -> assertTrue(Modifier.isFinal(handoffOwner.getModifiers())),
                () -> assertFalse(Modifier.isPublic(handoffOwner.getModifiers())),
                () -> assertFalse(Modifier.isProtected(handoffOwner.getModifiers())),
                () -> assertEquals(
                        List.of(P6RuntimeExecutionBridge.AppliedFactObserver.class),
                        List.of(handoffOwner.getInterfaces())),
                () -> assertEquals(
                        List.of(observerOverride), handoffPublicProtectedMethods),
                () -> assertTrue(Modifier.isPublic(observerOverride.getModifiers())),
                () -> assertFalse(Modifier.isStatic(observerOverride.getModifiers())),
                () -> assertFalse(observerOverride.isBridge()),
                () -> assertFalse(observerOverride.isSynthetic()),
                () -> assertEquals(void.class, observerOverride.getReturnType()),
                () -> assertEquals(0, observerOverride.getExceptionTypes().length),
                () -> assertEquals(1, handoffOwner.getDeclaredConstructors().length),
                () -> assertFalse(Modifier.isPublic(handoffConstructor.getModifiers())),
                () -> assertFalse(Modifier.isProtected(handoffConstructor.getModifiers())),
                () -> assertFalse(Modifier.isPrivate(handoffConstructor.getModifiers())),
                () -> assertEquals(0, handoffConstructor.getExceptionTypes().length),
                () -> assertEquals(3, handoffFields.size()),
                () -> assertEquals(
                        Set.of("service", "event", "context"),
                        handoffFields.stream()
                                .map(java.lang.reflect.Field::getName)
                                .collect(java.util.stream.Collectors.toUnmodifiableSet())),
                () -> assertEquals(
                        Set.of(
                                P8ServerPresentationService.class,
                                RuntimeEvent.class,
                                RuntimeExecutionContext.class),
                        handoffFields.stream()
                                .map(java.lang.reflect.Field::getType)
                                .collect(java.util.stream.Collectors.toUnmodifiableSet())),
                () -> assertTrue(handoffFields.stream().allMatch(field ->
                        Modifier.isPrivate(field.getModifiers())
                                && Modifier.isFinal(field.getModifiers()))),
                () -> assertEquals(0, Arrays.stream(handoffOwner.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                && Modifier.isStatic(method.getModifiers()))
                        .count()));

        var topLevels = p5TopLevelClasses();
        assertFalse(topLevels.isEmpty());
        assertTrue(topLevels.stream().noneMatch(type -> Modifier.isPublic(type.getModifiers())),
                () -> "public P5 top-level type: " + topLevels.stream()
                        .filter(type -> Modifier.isPublic(type.getModifiers()))
                        .map(Class::getName)
                        .toList());
    }

    @Test
    void p5UsesOnlyControlledExactFindAndPinWithoutP4PersistenceOperations()
            throws Exception {
        var service = Files.readString(SERVICE_SOURCE);
        var calls = new HashSet<String>();
        var matcher = STORE_CALL.matcher(service);
        while (matcher.find()) {
            calls.add(matcher.group(1));
        }
        assertEquals(Set.of("find", "pin"), calls);

        var p5Source = p5SourceText();
        for (var forbidden : List.of(
                ".reclaim(",
                ".journalRoots(",
                ".observeP4",
                ".audit(",
                "SkillRetentionRootAuditService",
                "SkillDefinitionStoreSubmissionPort",
                "GramaryeSkillSavedData",
                "PlayerSkillAttachmentService")) {
            assertFalse(p5Source.contains(forbidden),
                    () -> "P5 source reaches forbidden P4 operation/type: " + forbidden);
        }
    }

    @Test
    void loadedReferenceResolverCannotForceLoadOrMutateWorld() throws Exception {
        var resolver = Files.readString(RESOLVER_SOURCE);
        var forbiddenFragments = List.of(
                ".getChunk(",
                ".getChunkSource(",
                ".setChunkForced(",
                ".setBlock(",
                ".setBlockAndUpdate(",
                ".setBlockEntity(",
                ".removeBlock(",
                ".removeBlockEntity(",
                ".destroyBlock(",
                ".addFreshEntity(",
                ".addFreshEntityWithPassengers(",
                ".teleportTo(",
                ".setPos(",
                ".hurt(",
                ".kill(",
                ".discard(",
                ".explode(",
                ".scheduleTick(",
                ".levelEvent(",
                ".gameEvent(",
                "ChunkStatus",
                "TicketType",
                "ServerChunkCache");
        for (var fragment : forbiddenFragments) {
            assertFalse(resolver.contains(fragment),
                    () -> "loaded-only resolver contains forbidden API: " + fragment);
        }
        assertAll(
                () -> assertTrue(resolver.contains("server.getLevel(dimension)")),
                () -> assertTrue(resolver.contains("server.getPlayerList().getPlayer(")),
                () -> assertTrue(resolver.contains("level.getEntity(")),
                () -> assertTrue(resolver.contains("level.isLoaded(position)")),
                () -> assertEquals(3, occurrences(resolver, "classifySourceFailure(")),
                () -> assertEquals(3, occurrences(resolver, "classifyTargetFailure(")));
    }

    @Test
    void p5HasNoBackgroundReflectionUnsafeOrRawGenericSurface() throws Exception {
        var source = p5SourceText();
        for (var forbidden : List.of(
                "ExecutorService",
                "CompletableFuture",
                "java.util.concurrent.Future",
                ".parallelStream(",
                ".parallel(",
                "java.lang.reflect",
                "sun.misc.Unsafe",
                "jdk.internal.misc.Unsafe",
                "new Thread(",
                "ThreadLocal")) {
            assertFalse(source.contains(forbidden),
                    () -> "P5 source contains forbidden runtime primitive: " + forbidden);
        }

        var allTypes = recursivelyDeclaredTypes(p5TopLevelClasses());
        var rawSurfaces = new ArrayList<String>();
        var forbiddenFieldSurfaces = new ArrayList<String>();
        for (var type : allTypes) {
            for (var field : type.getDeclaredFields()) {
                if (isRaw(field.getType(), field.getGenericType())) {
                    rawSurfaces.add(type.getName() + "#" + field.getName());
                }
                if ((type == SkillRuntimeService.class || type == ServerSlot.class)
                        && (Executor.class.isAssignableFrom(field.getType())
                                || Future.class.isAssignableFrom(field.getType())
                                || Callable.class.isAssignableFrom(field.getType())
                                || Runnable.class.isAssignableFrom(field.getType())
                                || Thread.class.isAssignableFrom(field.getType()))) {
                    forbiddenFieldSurfaces.add(type.getName() + "#" + field.getName());
                }
            }
            for (var constructor : type.getDeclaredConstructors()) {
                var rawParameters = constructor.getParameterTypes();
                var genericParameters = constructor.getGenericParameterTypes();
                var syntheticOffset = rawParameters.length - genericParameters.length;
                for (var index = 0; index < genericParameters.length; index++) {
                    if (isRaw(rawParameters[index + syntheticOffset], genericParameters[index])) {
                        rawSurfaces.add(type.getName() + " constructor parameter "
                                + (index + syntheticOffset));
                    }
                }
            }
            for (var method : type.getDeclaredMethods()) {
                if (method.isSynthetic()) {
                    continue;
                }
                if (isRaw(method.getReturnType(), method.getGenericReturnType())) {
                    rawSurfaces.add(type.getName() + "#" + method.getName() + " return");
                }
                var rawParameters = method.getParameterTypes();
                var genericParameters = method.getGenericParameterTypes();
                for (var index = 0; index < rawParameters.length; index++) {
                    if (isRaw(rawParameters[index], genericParameters[index])) {
                        rawSurfaces.add(type.getName() + "#" + method.getName()
                                + " parameter " + index);
                    }
                }
            }
        }
        assertAll(
                () -> assertTrue(rawSurfaces.isEmpty(), () -> "raw generic surfaces: " + rawSurfaces),
                () -> assertTrue(forbiddenFieldSurfaces.isEmpty(),
                        () -> "background fields: " + forbiddenFieldSurfaces));
    }

    @Test
    void queuedAndDiagnosticStateRetainsNoUnexpectedLiveObjectsThrowableOrConfigProvider()
            throws Exception {
        var forbiddenLiveTypes = Set.of(
                MinecraftServer.class,
                ServerLevel.class,
                ServerPlayer.class,
                Entity.class);
        var carriers = List.of(
                RuntimeRootEventSpec.class,
                RuntimeEvent.class,
                RuntimeChildSpec.class,
                RuntimeCircuitBreakerSummary.class,
                ServerSlot.BreakerDiagnostic.class,
                ServerSlot.P9TerminalDiagnostic.class);
        for (var carrier : carriers) {
            assertTrue(Arrays.stream(carrier.getRecordComponents())
                    .noneMatch(component -> forbiddenLiveTypes.stream()
                            .anyMatch(forbidden -> forbidden.isAssignableFrom(component.getType()))
                                    || component.getType() == Object.class
                                    || Throwable.class.isAssignableFrom(component.getType())),
                    () -> "queued/diagnostic carrier retains forbidden type: " + carrier.getName());
        }

        var forbiddenNames = List.of(
                MinecraftServer.class.getName(),
                ServerLevel.class.getName(),
                ServerPlayer.class.getName(),
                Entity.class.getName(),
                Throwable.class.getName());
        assertTrue(Arrays.stream(ServerSlot.class.getDeclaredFields())
                .noneMatch(field -> forbiddenNames.stream()
                        .anyMatch(name -> field.getGenericType().getTypeName().contains(name))));

        var p5Types = recursivelyDeclaredTypes(p5TopLevelClasses());
        assertTrue(p5Types.stream().flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .noneMatch(field -> Throwable.class.isAssignableFrom(field.getType())),
                "P5 retains a Throwable field");
        assertTrue(Arrays.stream(ServerSlot.P9ActiveDiagnostic.class.getDeclaredFields())
                .noneMatch(field -> Entity.class.isAssignableFrom(field.getType())
                        || ServerPlayer.class.isAssignableFrom(field.getType())
                        || Throwable.class.isAssignableFrom(field.getType())
                        || java.util.Collection.class.isAssignableFrom(field.getType())
                        || Map.class.isAssignableFrom(field.getType())),
                "P9 active diagnostic retains a forbidden live or variable collection field");
        assertTrue(Arrays.stream(ServerSlot.P9TerminalDiagnostic.class.getRecordComponents())
                .noneMatch(component -> Entity.class.isAssignableFrom(component.getType())
                        || ServerPlayer.class.isAssignableFrom(component.getType())
                        || Throwable.class.isAssignableFrom(component.getType())
                        || java.util.Collection.class.isAssignableFrom(component.getType())
                        || Map.class.isAssignableFrom(component.getType())),
                "P9 terminal diagnostic retains a forbidden live or variable collection value");

        var forbiddenServiceRetention = Set.of(
                P5ServerRuntimeConfig.class,
                P5RawServerConfigSpec.class,
                IConfigSpec.class,
                AtomicReference.class,
                Supplier.class);
        assertTrue(Arrays.stream(SkillRuntimeService.class.getDeclaredFields())
                .noneMatch(field -> forbiddenServiceRetention.stream()
                        .anyMatch(forbidden -> forbidden.isAssignableFrom(field.getType()))),
                "runtime service retains config/raw/provider state");
    }

    @Test
    void scheduleDrainAndChildPublicationSourceOrderRemainClosed() throws Exception {
        var source = Files.readString(SERVICE_SOURCE);
        var compact = source.replaceAll("\\s+", " ");
        assertInOrder(
                compact,
                "baseTick = Math.addExact(slot.runtimeTick, 1L);",
                "scheduledTick = Math.addExact(baseTick, schedule.delayTicks());",
                "deadlineTick = Math.addExact(baseTick, schedule.deadlineHorizonTicks());",
                "if (scheduledTick > deadlineTick)");
        var deadline = section(
                source, "boolean deadlineExpired(", "void observeExpired(");
        assertTrue(deadline.contains("return slot.runtimeTick > event.deadlineRuntimeTick();"),
                "deadline must remain inclusive");
        assertFalse(deadline.contains("slot.runtimeTick >= event.deadlineRuntimeTick()"),
                "deadline equality must still execute");

        var drain = section(source, "private void drain(", "private void dispatchClaimed(");
        assertInOrder(
                drain,
                "slot.executionsThisTick == slot.limits.executionsPerServerPerTick()",
                "var event = slot.queue.peek();",
                "instance.cancellationRequested",
                "deadlineExpired(slot, event)",
                "var decision = executionDecision(",
                "claim(slot, event, instance, attribution);");
        var decision = section(
                source, "static RuntimeBudgetDecision executionDecision(",
                "static void claim(");
        assertInOrder(
                decision,
                "instanceExecutions == limits.executionsPerSkillInstancePerTick()",
                "attributionExecutions == limits.executionsPerAttributionPerTick()",
                "RuntimeBudgetDecision.EXECUTE");
        var decisionDelegate = section(
                source,
                "private RuntimeBudgetDecision executionDecision(",
                "RuntimeBudgetDecision decideExecution(");
        assertInOrder(
                decisionDelegate,
                "slot.limits",
                "instance.executionsThisTick",
                "attribution.executionsThisTick",
                "instance.attribution");

        var children = section(
                source, "private RuntimeExecutionOutcome processCompletedPlan(",
                "private ChildReservation reserveForPort(");
        assertInOrder(
                children,
                "childCount > slot.limits.directChildrenPerEvent()",
                "zeroDelayCount > slot.limits.zeroDelayChildrenPerEvent()",
                "!stableTokensMatch(slot.token, child.origin(), child.target())",
                "parent.depth() == slot.limits.maximumDepth()",
                "RuntimeScheduleRejectionReason.DELAY_OUT_OF_RANGE",
                "RuntimeScheduleRejectionReason.DEADLINE_OUT_OF_RANGE",
                "scheduledTicks[index] = Math.addExact(",
                "requestedDeadlines[index] = Math.addExact(",
                "Math.min(parent.deadlineRuntimeTick(), requestedDeadlines[index])",
                "instance.lifetimeEvents + childCount > slot.limits.eventsPerSkillInstance()",
                "var pendingBreak = pendingBreak(",
                "childCount > reservation.capacity()",
                "var published = new RuntimeEvent[childCount]",
                "for (var child : published)",
                "releaseCurrentReservation(slot, instance, attribution);");
        assertFalse(children.contains("dispatchClaimed("),
                "child plans must reenter the queue, not recurse");

        var cancellation = section(
                source, "RuntimeCancellationResult cancel(", "void handleRuntimePost(");
        assertInOrder(
                cancellation,
                "!slot.token.equals(serverToken(handle))",
                "!cancellationBudgetAvailable(",
                "slot.cancellationsThisTick++;",
                "if (handle instanceof RuntimeCancellationToken token)",
                "slot.eventIndex.get(token.eventId())",
                "slot.currentEvent == indexed",
                "removeExactQueuedOrDeferred(slot, indexed)");
    }

    @Test
    void wrongSlotAttributionAndGenericRootReferencesPrecedeCapacityAndPending()
            throws Exception {
        var source = Files.readString(SERVICE_SOURCE);
        var admission = section(
                source, "RuntimeAdmissionResult admitRoot(",
                "RuntimeCancellationResult cancel(");
        var stableShape = section(
                source,
                "private static Optional<InvalidEventReason> validateRootStableShape(",
                "private static Optional<InvalidEventReason> validateRootDefinitionShape(");
        var attributionSlotCheck = stableShape.indexOf(
                "!slot.token.equals(spec.budgetAttribution().server())");
        var attributionSlotFailure = stableShape.indexOf(
                "InvalidEventReason.INVALID_BUDGET_ATTRIBUTION", attributionSlotCheck);
        var shape = admission.indexOf("validateRootStableShape(");
        var stableTokens = admission.indexOf("stableTokensMatch(");
        var wrongServer = admission.indexOf(
                "RuntimeReferenceFailureReason.WRONG_SERVER", stableTokens);
        var resolution = firstIndex(
                admission,
                "resolveLoadedReferences(",
                "referenceResolver.resolve(");
        var serverCapacity = admission.indexOf("slot.instances.size()");
        var attributionCapacity = admission.indexOf("activeAttribution");
        var attributionPending = admission.indexOf("var attributionPending");
        var serverPending = admission.indexOf(
                "slot.committedPending + slot.reservedPending");

        assertAll(
                () -> assertTrue(attributionSlotCheck >= 0),
                () -> assertTrue(attributionSlotFailure > attributionSlotCheck,
                        "wrong-slot attribution must map to INVALID_BUDGET_ATTRIBUTION"),
                () -> assertTrue(stableShape.contains(
                        "spec.schedule().delayTicks() != 0")),
                () -> assertTrue(stableShape.contains(
                        "spec.schedule().deadlineHorizonTicks() != 100")),
                () -> assertTrue(stableShape.contains(
                        "spec.schedule().persistence()")),
                () -> assertTrue(shape >= 0),
                () -> assertTrue(stableTokens > shape,
                        "generic stable origin/target tokens must follow root shape validation"),
                () -> assertTrue(wrongServer > stableTokens),
                () -> assertTrue(resolution > wrongServer,
                        "generic loaded-reference resolution must follow token validation"),
                () -> assertTrue(serverCapacity > resolution,
                        "reference failure must win over active server capacity"),
                () -> assertTrue(attributionCapacity > serverCapacity),
                () -> assertTrue(attributionPending > resolution,
                        "reference failure must win over attribution pending breaker"),
                () -> assertTrue(serverPending > attributionPending));
    }

    @Test
    void childPlayerOriginAttributionMismatchIsTypedBeforeConstruction()
            throws Exception {
        var source = Files.readString(SERVICE_SOURCE);
        var childShape = section(
                source,
                "private static Optional<InvalidEventReason> validateChildShape(",
                "private static boolean stableTokensMatch(");
        assertInOrder(
                childShape,
                "child.origin() instanceof PlayerOrigin playerOrigin",
                "inheritedAttribution instanceof PlayerRuntimeBudgetAttribution playerAttribution",
                "!playerOrigin.player().equals(playerAttribution.playerId())",
                "InvalidEventReason.INVALID_REFERENCE_SHAPE");

        var children = section(
                source,
                "private RuntimeExecutionOutcome processCompletedPlan(",
                "private ChildReservation reserveForPort(");
        assertInOrder(
                children,
                "validateChildShape(",
                "new RuntimeExecutionOutcome.InvalidEvent(structural.orElseThrow())",
                "var published = new RuntimeEvent[childCount]",
                "published[index] = new RuntimeEvent(");
    }

    @Test
    void childSchedulePrecedenceUsesWholePlanPassesBeforeAnyPublication()
            throws Exception {
        var source = Files.readString(SERVICE_SOURCE);
        var children = section(
                source, "private RuntimeExecutionOutcome processCompletedPlan(",
                "private ChildReservation reserveForPort(");
        var reasons = List.of(
                "RuntimeScheduleRejectionReason.DELAY_OUT_OF_RANGE",
                "RuntimeScheduleRejectionReason.DEADLINE_OUT_OF_RANGE",
                "RuntimeScheduleRejectionReason.DELAY_OVERFLOW",
                "RuntimeScheduleRejectionReason.DEADLINE_OVERFLOW",
                "RuntimeScheduleRejectionReason.DEADLINE_BEFORE_SCHEDULED_TICK");
        var owningPasses = new HashSet<Integer>();
        var previousReason = -1;
        for (var reason : reasons) {
            var reasonIndex = children.indexOf(reason);
            assertTrue(reasonIndex > previousReason,
                    () -> "missing/out-of-order child schedule reason: " + reason);
            var owningPass = children.lastIndexOf("for (", reasonIndex);
            assertTrue(owningPass >= 0, () -> "schedule reason has no whole-plan pass: " + reason);
            owningPasses.add(owningPass);
            previousReason = reasonIndex;
        }
        var lastScheduleReason = previousReason;
        assertAll(
                () -> assertEquals(reasons.size(), owningPasses.size(),
                        "each schedule precedence coordinate needs a distinct global child pass"),
                () -> assertTrue(children.contains("scheduledTicks")),
                () -> assertTrue(children.contains("requestedDeadlines")),
                () -> assertTrue(children.indexOf("var published = new RuntimeEvent[childCount]")
                        > lastScheduleReason));
    }

    @Test
    void invariantFaultLagBreakerAndStoppingOrdersAreFailClosed() throws Exception {
        var source = Files.readString(SERVICE_SOURCE);
        var drain = section(source, "private void drain(", "private void dispatchClaimed(");
        var drainRuntimeCatch = section(
                drain, "catch (RuntimeException primary)", "catch (Error primary)");
        var drainErrorCatch = section(
                drain, "catch (Error primary)", "finally {");
        assertTrue(drainRuntimeCatch.contains("throw preserveRuntimeFault(slot, primary);"));
        assertTrue(drainErrorCatch.contains("throw preserveErrorFault(slot, primary);"));

        var cancellation = section(
                source, "RuntimeCancellationResult cancel(", "void handleRuntimePost(");
        var cancellationRuntimeCatch = section(
                cancellation, "catch (RuntimeException primary)", "catch (Error primary)");
        var cancellationErrorCatch = cancellation.substring(
                cancellation.indexOf("catch (Error primary)"));
        assertTrue(cancellationRuntimeCatch.contains(
                "throw preserveRuntimeFault(slot, primary);"));
        assertTrue(cancellationErrorCatch.contains(
                "throw preserveErrorFault(slot, primary);"));
        var runtimePreservation = section(
                source,
                "RuntimeException preserveRuntimeFault(",
                "Error preserveErrorFault(");
        var errorPreservation = section(
                source,
                "Error preserveErrorFault(",
                "static void enterFaultAfterRuntimeException(");
        assertInOrder(
                runtimePreservation,
                "enterFaultAfterRuntimeException(",
                "return primary;");
        assertInOrder(
                errorPreservation,
                "enterFaultAfterError(",
                "return primary;");
        var runtimeFault = section(
                source, "static void enterFaultAfterRuntimeException(",
                "static void enterFaultAfterError(");
        var errorFault = section(
                source, "static void enterFaultAfterError(",
                "static int clearSlotNormal(");
        assertInOrder(
                runtimeFault,
                "slot.state = ServerSlot.State.FAULTED",
                "clearSlotAfterRuntimeException(server, slot)");
        assertInOrder(
                errorFault,
                "slot.state = ServerSlot.State.FAULTED",
                "clearSlotAfterError(slot)");

        var claim = section(source, "static void claim(",
                "static void terminalizeCurrent(");
        assertAll(
                () -> assertTrue(claim.contains("maximumLagTicksThisTick"),
                        "execution claim must recompute exact lag"),
                () -> assertTrue(claim.contains("schedulingLag(slot.runtimeTick, event)")),
                () -> assertTrue(claim.contains("observeInstanceOffender(instance)")),
                () -> assertTrue(claim.contains("observeAttributionOffender(attribution")));

        var defer = section(source, "private static void defer(", "static void finishDeferred(");
        assertAll(
                () -> assertTrue(defer.contains("schedulingLag(slot.runtimeTick, event)")),
                () -> assertTrue(defer.contains("maximumLagTicksThisTick")),
                () -> assertTrue(defer.contains("slot.deferred[slot.deferredCount++] = event")));

        var expiry = section(source, "static void observeExpiry(",
                "static void observeOutcome(");
        assertAll(
                () -> assertTrue(expiry.contains("maximumLagTicksThisTick")),
                () -> assertTrue(expiry.contains("schedulingLag(slot.runtimeTick, event)")));

        var dispatch = section(source, "private void dispatchClaimed(",
                "private DetachedInvocation invokeRuntimeBoundary(");
        assertInOrder(
                dispatch,
                "invokeRuntimeBoundary(",
                "finishPort(",
                "if (slot.state != ServerSlot.State.RUNNING)",
                "terminalizeCurrent(",
                "observeOutcome(",
                "observeBreaker(");
        var invocation = section(
                source,
                "private DetachedInvocation invokeRuntimeBoundary(",
                "private RuntimeExecutionOutcome finishPort(");
        assertInOrder(
                invocation,
                "resolution = referenceResolver.resolve(server, event)",
                "if (slot.state != ServerSlot.State.RUNNING)",
                "if (!server.isRunning() || server.isStopped())",
                "if (instance.cancellationRequested)",
                "resolution instanceof RuntimeReferenceResolutionOutcome.Resolved",
                "isCurrentP9AuthenticatedActor(",
                "reserveForPort(slot, instance, attribution, event)",
                "context = new RuntimeExecutionContext(",
                "() -> {",
                "p9ReloadCloseRequested.get()",
                "actorForGuard != null",
                "isCurrentP9AuthenticatedActor(",
                "return runtimeExecutionGuardDecision(slot, instance, event)",
                "new RuntimeProjectileContinuationOpener(",
                "slot.diagnostics.portInvocationsThisTick++",
                "executionPort.execute(event, context)",
                "finally {",
                "context = null",
                "resolvedReferences = null",
                "resolution = null");
        var finishPort = section(
                source,
                "private RuntimeExecutionOutcome finishPort(",
                "RuntimeExecutionOutcome referenceFailureOutcome(");
        assertAll(
                () -> assertFalse(finishPort.contains("ResolvedRuntimeReferenceContext")),
                () -> assertFalse(finishPort.contains("RuntimeExecutionContext")),
                () -> assertFalse(finishPort.contains("RuntimeReferenceResolutionOutcome")),
                () -> assertTrue(finishPort.contains("PortInvocation")),
                () -> assertTrue(finishPort.contains("RuntimePortOutcome.Rejected")));
        var terminal = dispatch.indexOf("terminalizeCurrent(");
        var breakerRecord = dispatch.indexOf("observeBreaker(");
        var observeOutcome = dispatch.indexOf("observeOutcome(");
        var stoppingGuard = dispatch.indexOf("slot.state != ServerSlot.State.RUNNING");
        var stoppingReturn = dispatch.indexOf("return;", stoppingGuard);
        assertAll(
                () -> assertTrue(terminal >= 0),
                () -> assertTrue(breakerRecord > terminal,
                        "child breaker diagnostic must follow terminal cleanup"),
                () -> assertTrue(stoppingGuard >= 0
                                && stoppingReturn > stoppingGuard
                                && stoppingReturn < observeOutcome,
                        "stopping must suppress post-clear outcome observation"));

        var childPlan = section(
                source, "private RuntimeExecutionOutcome processCompletedPlan(",
                "private ChildReservation reserveForPort(");
        assertFalse(childPlan.contains("recordBreaker("),
                "child-plan processing cannot record before current terminal cleanup");
    }

    private static boolean isRaw(Class<?> erased, Type generic) {
        return erased.getTypeParameters().length > 0 && !(generic instanceof ParameterizedType);
    }

    private static boolean isPackagePrivate(int modifiers) {
        return !Modifier.isPublic(modifiers)
                && !Modifier.isProtected(modifiers)
                && !Modifier.isPrivate(modifiers);
    }

    private static List<String> recordComponentSignatures(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(component -> component.getType().getTypeName() + " " + component.getName())
                .toList();
    }

    private static Set<String> declaredFieldSignatures(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(field -> field.getType().getTypeName() + " " + field.getName())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<String> declaredMethodSignatures(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(method -> method.getReturnType().getTypeName()
                        + " "
                        + method.getName()
                        + "("
                        + Arrays.stream(method.getParameterTypes())
                                .map(Class::getTypeName)
                                .collect(java.util.stream.Collectors.joining(","))
                        + ")")
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static List<Class<?>> p5TopLevelClasses() throws Exception {
        var classes = new ArrayList<Class<?>>();
        for (var source : p5Sources()) {
            var matcher = TOP_LEVEL_DECLARATION.matcher(Files.readString(source));
            while (matcher.find()) {
                classes.add(Class.forName(
                        "com.yo1no.gramarye." + matcher.group(1),
                        false,
                        P5RuntimeStaticGateTest.class.getClassLoader()));
            }
        }
        return List.copyOf(classes);
    }

    private static List<Class<?>> recursivelyDeclaredTypes(List<Class<?>> roots) {
        var all = new ArrayList<Class<?>>();
        var pending = new ArrayList<>(roots);
        while (!pending.isEmpty()) {
            var type = pending.removeLast();
            all.add(type);
            pending.addAll(Arrays.asList(type.getDeclaredClasses()));
        }
        return List.copyOf(all);
    }

    private static List<Path> p5Sources() throws IOException {
        try (var paths = Files.list(ROOT_PACKAGE)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> path.getFileName().toString().startsWith("P5")
                            || path.getFileName().toString().equals("SkillRuntimeService.java"))
                    .sorted()
                    .toList();
        }
    }

    private static String p5SourceText() throws IOException {
        var combined = new StringBuilder();
        for (var source : p5Sources()) {
            combined.append(Files.readString(source)).append('\n');
        }
        return combined.toString();
    }

    private static String productionJavaSource() throws IOException {
        var combined = new StringBuilder();
        try (var paths = Files.walk(PROJECT_ROOT.resolve("src/main/java"))) {
            for (var source : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                combined.append(Files.readString(source)).append('\n');
            }
        }
        return combined.toString();
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        var from = 0;
        while (true) {
            var found = source.indexOf(fragment, from);
            if (found < 0) {
                return count;
            }
            count++;
            from = found + fragment.length();
        }
    }

    private static String section(String source, String start, String end) {
        var first = source.indexOf(start);
        var last = source.indexOf(end, first + start.length());
        assertTrue(first >= 0 && last > first,
                () -> "source section unavailable: " + start + " -> " + end);
        return source.substring(first, last);
    }

    private static String methodSource(String source, String signature) {
        var start = source.indexOf(signature);
        assertTrue(start >= 0, () -> "source method unavailable: " + signature);
        var bodyStart = source.indexOf('{', start + signature.length());
        assertTrue(bodyStart >= 0, () -> "source method body unavailable: " + signature);
        var depth = 0;
        for (var index = bodyStart; index < source.length(); index++) {
            var current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return source.substring(start, index + 1);
            }
        }
        throw new AssertionError("unterminated source method: " + signature);
    }

    private static int firstIndex(String source, String... candidates) {
        var first = Integer.MAX_VALUE;
        for (var candidate : candidates) {
            var found = source.indexOf(candidate);
            if (found >= 0) {
                first = Math.min(first, found);
            }
        }
        return first == Integer.MAX_VALUE ? -1 : first;
    }

    private static void assertInOrder(String source, String... fragments) {
        var cursor = -1;
        for (var fragment : fragments) {
            var found = source.indexOf(fragment, cursor + 1);
            assertTrue(found > cursor, () -> "missing/out-of-order source fragment: " + fragment);
            cursor = found;
        }
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("project root unavailable");
        }
        return current;
    }
}
