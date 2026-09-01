package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforgespi.language.IConfigurable;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageLoader;
import net.neoforged.neoforgespi.locating.ForgeFeature;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;

final class P5RuntimeConfigurationTest {
    @Test
    void rawSpecWritesExactDefaultsAndPublishesAnImmutableSnapshot() {
        var state = unavailableState();
        var spec = new P5RawServerConfigSpec(state);
        var raw = CommentedConfig.inMemory();

        assertFalse(spec.isEmpty());
        assertTrue(spec.isCorrect(raw));
        spec.correct(raw);

        for (var key : P5RuntimeLimitKey.values()) {
            assertEquals(
                    P5RuntimeLimitValidation.defaultValue(key),
                    raw.<Integer>get(P5RawServerConfigSpec.rawPath(key)));
        }
        spec.acceptRawConfig(raw);
        var requested = assertInstanceOf(P5RuntimeLimitLoadState.Requested.class, state.get());
        var snapshot = P5ServerRuntimeConfig.snapshotCandidate(requested);

        assertAll(
                () -> assertEquals(256, snapshot.pendingEventsPerSkillInstance()),
                () -> assertEquals(1_024, snapshot.pendingEventsPerAttribution()),
                () -> assertEquals(4_096, snapshot.pendingEventsPerServer()),
                () -> assertEquals(32, snapshot.activeSkillInstancesPerAttribution()),
                () -> assertEquals(128, snapshot.activeSkillInstancesPerServer()),
                () -> assertEquals(64, snapshot.rootAdmissionsPerTick()),
                () -> assertEquals(64, snapshot.executionsPerSkillInstancePerTick()),
                () -> assertEquals(128, snapshot.executionsPerAttributionPerTick()),
                () -> assertEquals(512, snapshot.executionsPerServerPerTick()),
                () -> assertEquals(512, snapshot.eventsPerSkillInstance()),
                () -> assertEquals(32, snapshot.maximumDepth()),
                () -> assertEquals(32, snapshot.directChildrenPerEvent()),
                () -> assertEquals(16, snapshot.zeroDelayChildrenPerEvent()),
                () -> assertEquals(12_000, snapshot.maximumDelayTicks()),
                () -> assertEquals(12_000, snapshot.maximumDeadlineHorizonTicks()),
                () -> assertEquals(128, snapshot.cancellationsPerTick()),
                () -> assertEquals(511, snapshot.descendantsPerSkillInstance()),
                () -> assertEquals(128, snapshot.definitionLeasesPerServer()),
                () -> assertEquals(192, snapshot.runtimeBudgetAttributionStatesPerServer()));

        raw.set(P5RawServerConfigSpec.rawPath(
                P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE), 1);
        assertEquals(256, requested.limits().pendingEventsPerSkillInstance());
        assertEquals(256, snapshot.pendingEventsPerSkillInstance());
    }

    @Test
    void exactMinimumTupleIsAccepted() {
        var state = unavailableState();
        var spec = new P5RawServerConfigSpec(state);
        var raw = rawConfig(minimumValues());

        spec.acceptRawConfig(raw);

        var requested = assertInstanceOf(P5RuntimeLimitLoadState.Requested.class, state.get());
        var snapshot = P5ServerRuntimeConfig.snapshotCandidate(requested);
        assertAll(
                () -> assertEquals(1, snapshot.pendingEventsPerSkillInstance()),
                () -> assertEquals(1, snapshot.pendingEventsPerAttribution()),
                () -> assertEquals(1, snapshot.pendingEventsPerServer()),
                () -> assertEquals(1, snapshot.activeSkillInstancesPerAttribution()),
                () -> assertEquals(1, snapshot.activeSkillInstancesPerServer()),
                () -> assertEquals(1, snapshot.rootAdmissionsPerTick()),
                () -> assertEquals(1, snapshot.executionsPerSkillInstancePerTick()),
                () -> assertEquals(1, snapshot.executionsPerAttributionPerTick()),
                () -> assertEquals(2, snapshot.executionsPerServerPerTick()),
                () -> assertEquals(1, snapshot.eventsPerSkillInstance()),
                () -> assertEquals(0, snapshot.maximumDepth()),
                () -> assertEquals(0, snapshot.directChildrenPerEvent()),
                () -> assertEquals(0, snapshot.zeroDelayChildrenPerEvent()),
                () -> assertEquals(0, snapshot.maximumDelayTicks()),
                () -> assertEquals(0, snapshot.maximumDeadlineHorizonTicks()),
                () -> assertEquals(1, snapshot.cancellationsPerTick()),
                () -> assertEquals(0, snapshot.descendantsPerSkillInstance()),
                () -> assertEquals(1, snapshot.definitionLeasesPerServer()),
                () -> assertEquals(2, snapshot.runtimeBudgetAttributionStatesPerServer()));
    }

    @Test
    void exactHardMaximumTupleAndLongRawValuesAreAccepted() {
        var hardValues = hardValues();
        var state = unavailableState();
        var spec = new P5RawServerConfigSpec(state);
        var raw = CommentedConfig.inMemory();
        for (var key : P5RuntimeLimitKey.values()) {
            raw.set(P5RawServerConfigSpec.rawPath(key), (long) hardValues[key.ordinal()]);
        }

        spec.acceptRawConfig(raw);

        var requested = assertInstanceOf(P5RuntimeLimitLoadState.Requested.class, state.get());
        var snapshot = P5ServerRuntimeConfig.snapshotCandidate(requested);
        assertAll(
                () -> assertRequestedValues(requested.limits(), hardValues),
                () -> assertSnapshotValues(snapshot, hardValues));
    }

    @Test
    void everyRawFieldIsCopiedExactlyIntoTheImmutableNextSlotSnapshot() {
        var rawValues = new int[] {
            5, 12, 20, 3, 8, 7, 2, 4, 6, 9, 3, 4, 2, 7, 11, 5
        };
        var state = unavailableState();
        var spec = new P5RawServerConfigSpec(state);
        var raw = CommentedConfig.inMemory();
        for (var key : P5RuntimeLimitKey.values()) {
            var value = rawValues[key.ordinal()];
            raw.set(P5RawServerConfigSpec.rawPath(key),
                    key.ordinal() % 2 == 0 ? Integer.valueOf(value) : Long.valueOf(value));
        }

        spec.acceptRawConfig(raw);

        var requested = assertInstanceOf(P5RuntimeLimitLoadState.Requested.class, state.get());
        var snapshot = P5ServerRuntimeConfig.snapshotCandidate(requested);
        assertAll(
                () -> assertRequestedValues(requested.limits(), rawValues),
                () -> assertSnapshotValues(snapshot, rawValues),
                () -> assertEquals(8, snapshot.descendantsPerSkillInstance()),
                () -> assertEquals(8, snapshot.definitionLeasesPerServer()),
                () -> assertEquals(15, snapshot.runtimeBudgetAttributionStatesPerServer()));

        for (var key : P5RuntimeLimitKey.values()) {
            raw.set(P5RawServerConfigSpec.rawPath(key),
                    P5RuntimeLimitValidation.defaultValue(key));
        }
        assertAll(
                () -> assertRequestedValues(requested.limits(), rawValues),
                () -> assertSnapshotValues(snapshot, rawValues));
    }

    @Test
    void rawDecodeUsesDeclarationOrderForMissingTypeAndRangeFailures() {
        var state = unavailableState();
        var spec = new P5RawServerConfigSpec(state);
        var missingFirst = rawConfig(hardValues());
        missingFirst.remove(P5RawServerConfigSpec.rawPath(
                P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE));
        missingFirst.set(P5RawServerConfigSpec.rawPath(
                P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION), "wrong");

        spec.acceptRawConfig(missingFirst);
        assertFailure(
                state.get(),
                P5RuntimeConfigurationFailureReason.MISSING_REQUIRED_VALUE,
                P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE,
                Optional.empty());

        var wrongType = rawConfig(hardValues());
        wrongType.set(P5RawServerConfigSpec.rawPath(
                P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE), 256.0D);
        spec.acceptRawConfig(wrongType);
        assertFailure(
                state.get(),
                P5RuntimeConfigurationFailureReason.WRONG_VALUE_TYPE,
                P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE,
                Optional.empty());

        var orderedRange = rawConfig(hardValues());
        orderedRange.set(P5RawServerConfigSpec.rawPath(
                P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION), 1_025L);
        orderedRange.set(P5RawServerConfigSpec.rawPath(
                P5RuntimeLimitKey.PENDING_EVENTS_PER_SERVER), 0L);
        spec.acceptRawConfig(orderedRange);
        assertFailure(
                state.get(),
                P5RuntimeConfigurationFailureReason.ABOVE_HARD_MAXIMUM,
                P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION,
                Optional.empty());
    }

    @Test
    void belowMinimumAndAboveHardMaximumFailWithoutClamping() {
        for (var key : P5RuntimeLimitKey.values()) {
            var belowState = unavailableState();
            var below = rawConfig(minimumValues());
            below.set(
                    P5RawServerConfigSpec.rawPath(key),
                    P5RuntimeLimitValidation.minimum(key) - 1);
            new P5RawServerConfigSpec(belowState).acceptRawConfig(below);
            assertFailure(
                    belowState.get(),
                    P5RuntimeConfigurationFailureReason.BELOW_MINIMUM,
                    key,
                    Optional.empty());

            var aboveState = unavailableState();
            var above = rawConfig(hardValues());
            above.set(
                    P5RawServerConfigSpec.rawPath(key),
                    (long) P5RuntimeLimitValidation.hardMaximum(key) + 1L);
            new P5RawServerConfigSpec(aboveState).acceptRawConfig(above);
            assertFailure(
                    aboveState.get(),
                    P5RuntimeConfigurationFailureReason.ABOVE_HARD_MAXIMUM,
                    key,
                    Optional.empty());
        }

        var zeroMinimumKeys = List.of(
                P5RuntimeLimitKey.MAXIMUM_DEPTH,
                P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT,
                P5RuntimeLimitKey.ZERO_DELAY_CHILDREN_PER_EVENT,
                P5RuntimeLimitKey.MAXIMUM_DELAY_TICKS,
                P5RuntimeLimitKey.MAXIMUM_DEADLINE_HORIZON_TICKS);
        for (var key : P5RuntimeLimitKey.values()) {
            assertEquals(zeroMinimumKeys.contains(key),
                    P5RuntimeLimitValidation.minimum(key) == 0,
                    key.name());
        }
    }

    @Test
    void relationsFailInTheirExactOrderedKeyShapes() {
        var cases = new RelationCase[] {
            relationCase(
                    changed(hardValues(),
                            P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE, 2,
                            P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION, 1),
                    P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE,
                    P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION),
            relationCase(
                    changed(hardValues(),
                            P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE, 1,
                            P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION, 2,
                            P5RuntimeLimitKey.PENDING_EVENTS_PER_SERVER, 1),
                    P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION,
                    P5RuntimeLimitKey.PENDING_EVENTS_PER_SERVER),
            relationCase(
                    changed(hardValues(),
                            P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION, 2,
                            P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER, 1),
                    P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION,
                    P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER),
            relationCase(
                    changed(hardValues(),
                            P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE, 1,
                            P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION, 1,
                            P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION, 2),
                    P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION,
                    P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION),
            relationCase(
                    changed(hardValues(),
                            P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE, 1,
                            P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION, 1,
                            P5RuntimeLimitKey.PENDING_EVENTS_PER_SERVER, 1,
                            P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION, 1,
                            P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER, 2),
                    P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER,
                    P5RuntimeLimitKey.PENDING_EVENTS_PER_SERVER),
            relationCase(
                    changed(hardValues(),
                            P5RuntimeLimitKey.EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK, 2,
                            P5RuntimeLimitKey.EXECUTIONS_PER_ATTRIBUTION_PER_TICK, 1),
                    P5RuntimeLimitKey.EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK,
                    P5RuntimeLimitKey.EXECUTIONS_PER_ATTRIBUTION_PER_TICK),
            relationCase(
                    changed(hardValues(),
                            P5RuntimeLimitKey.EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK, 1,
                            P5RuntimeLimitKey.EXECUTIONS_PER_ATTRIBUTION_PER_TICK, 2,
                            P5RuntimeLimitKey.EXECUTIONS_PER_SERVER_PER_TICK, 2),
                    P5RuntimeLimitKey.EXECUTIONS_PER_ATTRIBUTION_PER_TICK,
                    P5RuntimeLimitKey.EXECUTIONS_PER_SERVER_PER_TICK),
            relationCase(
                    changed(hardValues(),
                            P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE, 2,
                            P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE, 1),
                    P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE,
                    P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE),
            relationCase(
                    changed(hardValues(),
                            P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE, 1,
                            P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE, 1,
                            P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT, 1,
                            P5RuntimeLimitKey.ZERO_DELAY_CHILDREN_PER_EVENT, 0),
                    P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT,
                    P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE),
            relationCase(
                    changed(hardValues(),
                            P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT, 0,
                            P5RuntimeLimitKey.ZERO_DELAY_CHILDREN_PER_EVENT, 1),
                    P5RuntimeLimitKey.ZERO_DELAY_CHILDREN_PER_EVENT,
                    P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT),
            relationCase(
                    changed(hardValues(),
                            P5RuntimeLimitKey.MAXIMUM_DEPTH, 0,
                            P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT, 1,
                            P5RuntimeLimitKey.ZERO_DELAY_CHILDREN_PER_EVENT, 0),
                    P5RuntimeLimitKey.MAXIMUM_DEPTH,
                    P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT),
            relationCase(
                    changed(hardValues(),
                            P5RuntimeLimitKey.MAXIMUM_DELAY_TICKS, 1,
                            P5RuntimeLimitKey.MAXIMUM_DEADLINE_HORIZON_TICKS, 0),
                    P5RuntimeLimitKey.MAXIMUM_DELAY_TICKS,
                    P5RuntimeLimitKey.MAXIMUM_DEADLINE_HORIZON_TICKS)
        };

        for (var relationCase : cases) {
            var state = unavailableState();
            new P5RawServerConfigSpec(state).acceptRawConfig(rawConfig(relationCase.values()));
            assertFailure(
                    state.get(),
                    P5RuntimeConfigurationFailureReason.RELATION_VIOLATION,
                    relationCase.primary(),
                    Optional.of(relationCase.related()));
        }
    }

    @Test
    void invalidAndUnavailableCandidatesFailClosed() {
        var invalidFailure = P5RuntimeLimitValidation.singleKeyFailure(
                P5RuntimeConfigurationFailureReason.BELOW_MINIMUM,
                P5RuntimeLimitKey.CANCELLATIONS_PER_TICK);
        var invalidException = assertThrows(
                P5RuntimeConfigurationException.class,
                () -> P5ServerRuntimeConfig.snapshotCandidate(
                        new P5RuntimeLimitLoadState.Invalid(invalidFailure)));
        assertEquals(invalidFailure, invalidException.failure());

        var unavailableException = assertThrows(
                P5RuntimeConfigurationException.class,
                () -> P5ServerRuntimeConfig.snapshotCandidate(
                        P5RuntimeLimitLoadState.Unavailable.INSTANCE));
        assertEquals(
                new P5RuntimeConfigurationFailure(
                        P5RuntimeConfigurationFailureReason.CONFIG_UNAVAILABLE,
                        Optional.empty(),
                        Optional.empty()),
                unavailableException.failure());

        var badRequested = hardValues();
        badRequested[P5RuntimeLimitKey.CANCELLATIONS_PER_TICK.ordinal()] = 0;
        assertThrows(P5RuntimeConfigurationException.class, () -> requestedLimits(badRequested));
    }

    @Test
    void correctRejectsNonemptyInputAndAcceptNullPublishesUnavailable() {
        var state = unavailableState();
        var spec = new P5RawServerConfigSpec(state);
        var raw = rawConfig(hardValues());

        var failure = assertThrows(IllegalStateException.class, () -> spec.correct(raw));
        assertEquals("P5_RUNTIME_CONFIG_CORRECT_NONEMPTY", failure.getMessage());

        spec.acceptRawConfig(raw);
        assertInstanceOf(P5RuntimeLimitLoadState.Requested.class, state.get());
        spec.acceptConfig(null);
        assertEquals(P5RuntimeLimitLoadState.Unavailable.INSTANCE, state.get());
    }

    @Test
    void reloadChangesOnlyTheNextSlotCandidate() {
        var state = unavailableState();
        var spec = new P5RawServerConfigSpec(state);
        spec.acceptRawConfig(rawConfig(hardValues()));
        var activeSnapshot = P5ServerRuntimeConfig.snapshotCandidate(state.get());
        var activeSlot = new ServerSlot(new RuntimeServerToken(1L), activeSnapshot);
        activeSlot.committedPending = 4;
        activeSlot.rootAdmissionsThisTick = 2;
        activeSlot.executionsThisTick = 3;
        activeSlot.cancellationsThisTick = 2;

        spec.acceptRawConfig(rawConfig(minimumValues()));
        assertEquals(
                P5RuntimeReloadDisposition.DEFERRED_UNTIL_NEXT_SERVER_SLOT,
                P5ServerRuntimeConfig.reloadDispositionFor(state.get()));
        var nextSnapshot = P5ServerRuntimeConfig.snapshotCandidate(state.get());

        assertAll(
                () -> assertSame(activeSnapshot, activeSlot.limits),
                () -> assertSnapshotValues(activeSlot.limits, hardValues()),
                () -> assertSnapshotValues(nextSnapshot, minimumValues()),
                () -> assertEquals(0L, activeSlot.runtimeTick),
                () -> assertEquals(0, activeSlot.queue.size()),
                () -> assertEquals(0, activeSlot.instances.size()),
                () -> assertEquals(0, activeSlot.leases.size()),
                () -> assertEquals(4, activeSlot.committedPending),
                () -> assertEquals(2, activeSlot.rootAdmissionsThisTick),
                () -> assertEquals(3, activeSlot.executionsThisTick),
                () -> assertEquals(2, activeSlot.cancellationsThisTick));

        spec.acceptRawConfig(CommentedConfig.inMemory());
        assertEquals(
                P5RuntimeReloadDisposition.INVALID_FOR_NEXT_SERVER_SLOT,
                P5ServerRuntimeConfig.reloadDispositionFor(state.get()));
        spec.acceptConfig(null);
        assertEquals(
                P5RuntimeReloadDisposition.INVALID_FOR_NEXT_SERVER_SLOT,
                P5ServerRuntimeConfig.reloadDispositionFor(state.get()));
    }

    @Test
    void loweredProductSnapshotKeepsFixedPhysicalScratchAndRingCapacities() {
        var limits = P5RuntimeLimits.fromRequested(requestedLimits(minimumValues()));
        var slot = new ServerSlot(new RuntimeServerToken(1L), limits);

        assertAll(
                () -> assertSame(limits, slot.limits),
                () -> assertEquals(4_096, slot.deferred.length),
                () -> assertEquals(4_096, slot.cleanupScratch.length),
                () -> assertEquals(256, slot.diagnostics.breakerRing.length),
                () -> assertEquals(4, slot.diagnostics.breakerTotals.length),
                () -> assertEquals(0, slot.committedPending),
                () -> assertEquals(0, slot.reservedPending),
                () -> assertEquals(0, slot.currentReservationCount));
    }

    @Test
    void realNeoForgeRegistrationReloadUnloadAndUnrelatedEventsStayExact() {
        var container = new CapturingModContainer(Gramarye.MOD_ID);
        var runtimeConfig = new P5ServerRuntimeConfig(container.eventBus(), container);
        var registeredConfig = container.registeredConfig();
        var rawSpec = assertInstanceOf(
                P5RawServerConfigSpec.class, container.registeredSpec());

        assertAll(
                () -> assertEquals(1, container.registrationCount()),
                () -> assertSame(ModConfig.Type.SERVER, container.registeredType()),
                () -> assertSame(rawSpec, registeredConfig.getSpec()),
                () -> assertSame(rawSpec, container.registeredSpec()),
                () -> assertEquals(
                        P5ServerRuntimeConfig.CONFIG_FILE_NAME,
                        container.registeredFileName()),
                () -> assertSame(ModConfig.Type.SERVER, registeredConfig.getType()),
                () -> assertEquals(Gramarye.MOD_ID, registeredConfig.getModId()),
                () -> assertEquals(
                        P5ServerRuntimeConfig.CONFIG_FILE_NAME,
                        registeredConfig.getFileName()),
                () -> assertTrue(runtimeConfig.latestReloadDisposition().isEmpty()));

        rawSpec.acceptRawConfig(rawConfig(hardValues()));
        var activeSlotSnapshot = runtimeConfig.snapshotForStarted();
        rawSpec.acceptRawConfig(rawConfig(minimumValues()));
        container.eventBus().post(new ModConfigEvent.Reloading(registeredConfig));
        var nextSlotSnapshot = runtimeConfig.snapshotForStarted();

        assertAll(
                () -> assertEquals(
                        Optional.of(P5RuntimeReloadDisposition.DEFERRED_UNTIL_NEXT_SERVER_SLOT),
                        runtimeConfig.latestReloadDisposition()),
                () -> assertEquals(4_096, activeSlotSnapshot.pendingEventsPerServer()),
                () -> assertEquals(1, nextSlotSnapshot.pendingEventsPerServer()));

        var unrelatedConfig = container.registerUnrelatedServerConfig();
        container.eventBus().post(new ModConfigEvent.Reloading(unrelatedConfig));
        container.eventBus().post(new ModConfigEvent.Unloading(unrelatedConfig));
        assertAll(
                () -> assertEquals(
                        Optional.of(P5RuntimeReloadDisposition.DEFERRED_UNTIL_NEXT_SERVER_SLOT),
                        runtimeConfig.latestReloadDisposition()),
                () -> assertEquals(1, runtimeConfig.snapshotForStarted().pendingEventsPerServer()));

        rawSpec.acceptRawConfig(CommentedConfig.inMemory());
        container.eventBus().post(new ModConfigEvent.Reloading(registeredConfig));
        var invalid = assertThrows(
                P5RuntimeConfigurationException.class, runtimeConfig::snapshotForStarted);
        assertAll(
                () -> assertEquals(
                        Optional.of(P5RuntimeReloadDisposition.INVALID_FOR_NEXT_SERVER_SLOT),
                        runtimeConfig.latestReloadDisposition()),
                () -> assertEquals(
                        P5RuntimeConfigurationFailureReason.MISSING_REQUIRED_VALUE,
                        invalid.failure().reason()),
                () -> assertEquals(
                        Optional.of(P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE),
                        invalid.failure().primaryKey()));

        container.eventBus().post(new ModConfigEvent.Unloading(registeredConfig));
        var unavailable = assertThrows(
                P5RuntimeConfigurationException.class, runtimeConfig::snapshotForStarted);
        assertAll(
                () -> assertTrue(runtimeConfig.latestReloadDisposition().isEmpty()),
                () -> assertEquals(
                        P5RuntimeConfigurationFailureReason.CONFIG_UNAVAILABLE,
                        unavailable.failure().reason()),
                () -> assertTrue(unavailable.failure().primaryKey().isEmpty()),
                () -> assertTrue(unavailable.failure().relatedKey().isEmpty()));
    }

    private static AtomicReference<P5RuntimeLimitLoadState> unavailableState() {
        return new AtomicReference<>(P5RuntimeLimitLoadState.Unavailable.INSTANCE);
    }

    private static int[] hardValues() {
        var values = new int[P5RuntimeLimitKey.values().length];
        for (var key : P5RuntimeLimitKey.values()) {
            values[key.ordinal()] = P5RuntimeLimitValidation.hardMaximum(key);
        }
        return values;
    }

    private static int[] minimumValues() {
        var values = new int[P5RuntimeLimitKey.values().length];
        for (var key : P5RuntimeLimitKey.values()) {
            values[key.ordinal()] = P5RuntimeLimitValidation.minimum(key);
        }
        return values;
    }

    private static int[] changed(
            int[] values,
            P5RuntimeLimitKey firstKey,
            int firstValue) {
        var changed = values.clone();
        changed[firstKey.ordinal()] = firstValue;
        return changed;
    }

    private static int[] changed(
            int[] values,
            P5RuntimeLimitKey firstKey,
            int firstValue,
            P5RuntimeLimitKey secondKey,
            int secondValue) {
        var changed = changed(values, firstKey, firstValue);
        changed[secondKey.ordinal()] = secondValue;
        return changed;
    }

    private static int[] changed(
            int[] values,
            P5RuntimeLimitKey firstKey,
            int firstValue,
            P5RuntimeLimitKey secondKey,
            int secondValue,
            P5RuntimeLimitKey thirdKey,
            int thirdValue) {
        var changed = changed(values, firstKey, firstValue, secondKey, secondValue);
        changed[thirdKey.ordinal()] = thirdValue;
        return changed;
    }

    private static int[] changed(
            int[] values,
            P5RuntimeLimitKey firstKey,
            int firstValue,
            P5RuntimeLimitKey secondKey,
            int secondValue,
            P5RuntimeLimitKey thirdKey,
            int thirdValue,
            P5RuntimeLimitKey fourthKey,
            int fourthValue) {
        var changed = changed(
                values,
                firstKey,
                firstValue,
                secondKey,
                secondValue,
                thirdKey,
                thirdValue);
        changed[fourthKey.ordinal()] = fourthValue;
        return changed;
    }

    private static int[] changed(
            int[] values,
            P5RuntimeLimitKey firstKey,
            int firstValue,
            P5RuntimeLimitKey secondKey,
            int secondValue,
            P5RuntimeLimitKey thirdKey,
            int thirdValue,
            P5RuntimeLimitKey fourthKey,
            int fourthValue,
            P5RuntimeLimitKey fifthKey,
            int fifthValue) {
        var changed = changed(
                values,
                firstKey,
                firstValue,
                secondKey,
                secondValue,
                thirdKey,
                thirdValue,
                fourthKey,
                fourthValue);
        changed[fifthKey.ordinal()] = fifthValue;
        return changed;
    }

    private static CommentedConfig rawConfig(int[] values) {
        var raw = CommentedConfig.inMemory();
        for (var key : P5RuntimeLimitKey.values()) {
            raw.set(P5RawServerConfigSpec.rawPath(key), values[key.ordinal()]);
        }
        return raw;
    }

    private static P5RuntimeRequestedLimits requestedLimits(int[] values) {
        return new P5RuntimeRequestedLimits(
                values[0],
                values[1],
                values[2],
                values[3],
                values[4],
                values[5],
                values[6],
                values[7],
                values[8],
                values[9],
                values[10],
                values[11],
                values[12],
                values[13],
                values[14],
                values[15]);
    }

    private static void assertRequestedValues(
            P5RuntimeRequestedLimits requested,
            int[] expected) {
        assertArrayEquals(expected, P5RuntimeLimitValidation.values(requested));
    }

    private static void assertSnapshotValues(P5RuntimeLimits snapshot, int[] expected) {
        assertArrayEquals(expected, new int[] {
            snapshot.pendingEventsPerSkillInstance(),
            snapshot.pendingEventsPerAttribution(),
            snapshot.pendingEventsPerServer(),
            snapshot.activeSkillInstancesPerAttribution(),
            snapshot.activeSkillInstancesPerServer(),
            snapshot.rootAdmissionsPerTick(),
            snapshot.executionsPerSkillInstancePerTick(),
            snapshot.executionsPerAttributionPerTick(),
            snapshot.executionsPerServerPerTick(),
            snapshot.eventsPerSkillInstance(),
            snapshot.maximumDepth(),
            snapshot.directChildrenPerEvent(),
            snapshot.zeroDelayChildrenPerEvent(),
            snapshot.maximumDelayTicks(),
            snapshot.maximumDeadlineHorizonTicks(),
            snapshot.cancellationsPerTick()
        });
    }

    private static void assertFailure(
            P5RuntimeLimitLoadState state,
            P5RuntimeConfigurationFailureReason reason,
            P5RuntimeLimitKey primary,
            Optional<P5RuntimeLimitKey> related) {
        var invalid = assertInstanceOf(P5RuntimeLimitLoadState.Invalid.class, state);
        assertEquals(reason, invalid.failure().reason());
        assertEquals(Optional.of(primary), invalid.failure().primaryKey());
        assertEquals(related, invalid.failure().relatedKey());
    }

    private static RelationCase relationCase(
            int[] values,
            P5RuntimeLimitKey primary,
            P5RuntimeLimitKey related) {
        return new RelationCase(values, primary, related);
    }

    private record RelationCase(
            int[] values,
            P5RuntimeLimitKey primary,
            P5RuntimeLimitKey related) {}

    private static final class CapturingModContainer extends ModContainer {
        private final IEventBus eventBus = BusBuilder.builder().build();
        private final ConfigTracker configTracker = new ConfigTracker();
        private int registrationCount;
        private ModConfig.Type registeredType;
        private IConfigSpec registeredSpec;
        private String registeredFileName;
        private ModConfig registeredConfig;

        CapturingModContainer(String modId) {
            super(new StubModInfo(modId));
        }

        @Override
        public IEventBus getEventBus() {
            return eventBus;
        }

        @Override
        public void registerConfig(
                ModConfig.Type type,
                IConfigSpec configSpec,
                String fileName) {
            registrationCount++;
            registeredType = type;
            registeredSpec = configSpec;
            registeredFileName = fileName;
            registeredConfig = configTracker.registerConfig(
                    type, configSpec, this, fileName);
        }

        IEventBus eventBus() {
            return eventBus;
        }

        int registrationCount() {
            return registrationCount;
        }

        ModConfig.Type registeredType() {
            return registeredType;
        }

        IConfigSpec registeredSpec() {
            return registeredSpec;
        }

        String registeredFileName() {
            return registeredFileName;
        }

        ModConfig registeredConfig() {
            return registeredConfig;
        }

        ModConfig registerUnrelatedServerConfig() {
            var builder = new ModConfigSpec.Builder();
            builder.define("unrelated", true);
            return configTracker.registerConfig(
                    ModConfig.Type.SERVER,
                    builder.build(),
                    this,
                    "unrelated-server.toml");
        }
    }

    private record StubModInfo(String modId) implements IModInfo {
        @Override
        public IModFileInfo getOwningFile() {
            return null;
        }

        @Override
        public IModLanguageLoader getLoader() {
            return null;
        }

        @Override
        public String getModId() {
            return modId;
        }

        @Override
        public String getDisplayName() {
            return modId;
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public ArtifactVersion getVersion() {
            return new DefaultArtifactVersion("1");
        }

        @Override
        public List<? extends ModVersion> getDependencies() {
            return List.of();
        }

        @Override
        public List<? extends ForgeFeature.Bound> getForgeFeatures() {
            return List.of();
        }

        @Override
        public String getNamespace() {
            return modId;
        }

        @Override
        public Map<String, Object> getModProperties() {
            return Map.of();
        }

        @Override
        public Optional<URL> getUpdateURL() {
            return Optional.empty();
        }

        @Override
        public Optional<URL> getModURL() {
            return Optional.empty();
        }

        @Override
        public Optional<String> getLogoFile() {
            return Optional.empty();
        }

        @Override
        public boolean getLogoBlur() {
            return false;
        }

        @Override
        public IConfigurable getConfig() {
            return EmptyConfigurable.INSTANCE;
        }
    }

    private enum EmptyConfigurable implements IConfigurable {
        INSTANCE;

        @Override
        public <T> Optional<T> getConfigElement(String... key) {
            return Optional.empty();
        }

        @Override
        public List<? extends IConfigurable> getConfigList(String... key) {
            return List.of();
        }
    }
}
