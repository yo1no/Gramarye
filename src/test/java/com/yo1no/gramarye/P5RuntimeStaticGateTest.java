package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.store.ControlledSkillPin;
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
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
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
                "childCount > reservation.capacity",
                "var published = new RuntimeEvent[childCount]",
                "new EventId(Math.addExact(reservation.eventIdStart, index + 1L))",
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
                "new ServerSlot.InstanceState(");

        var removal = section(
                source, "private static void maybeRemoveInstance(",
                "static void verifyQueuedIdentity(");
        assertInOrder(
                removal,
                "instance.lease.release()",
                "slot.leases.remove(instance.lease.reference)");

        var normalClear = section(
                source, "private static void clearSlotNormal(",
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
                children, "if (pendingBreak != null)", "if (childCount > reservation.capacity)");
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
                source, "private void handleRuntimePost(", "private void handleRuntimeStopping(");
        var advance = section(
                source, "static RuntimeTickAdvanceResult advanceRuntimeTick(",
                "static void observeDrainStop(");
        assertAll(
                () -> assertEquals(1, occurrences(source, "::handleRuntimePost")),
                () -> assertEquals(1, occurrences(source, "advanceRuntimeTick(slot)")),
                () -> assertEquals(1, occurrences(
                        source, "slot.runtimeTick = Math.incrementExact(slot.runtimeTick)")),
                () -> assertTrue(post.contains("advanceRuntimeTick(slot)")),
                () -> assertTrue(advance.contains("RuntimeTickAdvanceResult.EXHAUSTED")),
                () -> assertFalse(source.contains("ServerTickEvent.Pre")));
    }

    @Test
    void v1AuthorityFencedJavaIsByteForByteTheProductionVocabulary() throws Exception {
        var authority = Files.readString(P5_AUTHORITY);
        var heading = authority.indexOf("### 47.3 Exact Java Declarations");
        assertTrue(heading >= 0, "missing V1 exact-declarations heading");
        var fenceStart = authority.indexOf("```java\n", heading);
        assertTrue(fenceStart >= 0, "missing V1 Java fence");
        fenceStart += "```java\n".length();
        var fenceEnd = authority.indexOf("\n```\n\n### 47.4 Result-Family Responsibilities", fenceStart);
        assertTrue(fenceEnd >= 0, "missing unique V1 Java fence terminator");
        var exactDeclarations = authority.substring(fenceStart, fenceEnd) + "\n";
        assertEquals(exactDeclarations, Files.readString(VOCABULARY_SOURCE));
    }

    @Test
    void rootAdmissionHasNoProductionCallerAndEveryP5TopLevelTypeIsNonPublic()
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
        assertEquals(1, callsites, "admitRoot must have its declaration and zero callers");
        assertTrue(Files.readString(SERVICE_SOURCE).contains(
                "RuntimeAdmissionResult admitRoot(MinecraftServer server, RuntimeRootEventSpec spec)"));

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
    void queuedAndDiagnosticStateRetainsNoLiveObjectsThrowableOrConfigProvider()
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
                ServerSlot.BreakerDiagnostic.class);
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
                "childCount > reservation.capacity",
                "var published = new RuntimeEvent[childCount]",
                "for (var child : published)",
                "releaseCurrentReservation(slot, instance, attribution);");
        assertFalse(children.contains("dispatchClaimed("),
                "child plans must reenter the queue, not recurse");

        var cancellation = section(
                source, "RuntimeCancellationResult cancel(", "private void handleRuntimePost(");
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
                source, "RuntimeCancellationResult cancel(", "private void handleRuntimePost(");
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
                "static void clearSlotNormal(");
        assertInOrder(
                runtimeFault,
                "slot.state = ServerSlot.State.FAULTED",
                "clearSlotAfterRuntimeException(slot)");
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
                "reserveForPort(slot, instance, attribution, event)",
                "context = new RuntimeExecutionContext(",
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
