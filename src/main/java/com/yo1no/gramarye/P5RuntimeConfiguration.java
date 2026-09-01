package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;
import java.util.Optional;

enum P5RuntimeReloadDisposition {
    DEFERRED_UNTIL_NEXT_SERVER_SLOT,
    INVALID_FOR_NEXT_SERVER_SLOT
}

record P5RuntimeRequestedLimits(
        int pendingEventsPerSkillInstance,
        int pendingEventsPerAttribution,
        int pendingEventsPerServer,
        int activeSkillInstancesPerAttribution,
        int activeSkillInstancesPerServer,
        int rootAdmissionsPerTick,
        int executionsPerSkillInstancePerTick,
        int executionsPerAttributionPerTick,
        int executionsPerServerPerTick,
        int eventsPerSkillInstance,
        int maximumDepth,
        int directChildrenPerEvent,
        int zeroDelayChildrenPerEvent,
        int maximumDelayTicks,
        int maximumDeadlineHorizonTicks,
        int cancellationsPerTick) {
    P5RuntimeRequestedLimits {
        P5RuntimeLimitValidation.requireValid(P5RuntimeLimitValidation.values(
                pendingEventsPerSkillInstance,
                pendingEventsPerAttribution,
                pendingEventsPerServer,
                activeSkillInstancesPerAttribution,
                activeSkillInstancesPerServer,
                rootAdmissionsPerTick,
                executionsPerSkillInstancePerTick,
                executionsPerAttributionPerTick,
                executionsPerServerPerTick,
                eventsPerSkillInstance,
                maximumDepth,
                directChildrenPerEvent,
                zeroDelayChildrenPerEvent,
                maximumDelayTicks,
                maximumDeadlineHorizonTicks,
                cancellationsPerTick));
    }
}

record P5RuntimeLimits(
        int pendingEventsPerSkillInstance,
        int pendingEventsPerAttribution,
        int pendingEventsPerServer,
        int activeSkillInstancesPerAttribution,
        int activeSkillInstancesPerServer,
        int rootAdmissionsPerTick,
        int executionsPerSkillInstancePerTick,
        int executionsPerAttributionPerTick,
        int executionsPerServerPerTick,
        int eventsPerSkillInstance,
        int maximumDepth,
        int directChildrenPerEvent,
        int zeroDelayChildrenPerEvent,
        int maximumDelayTicks,
        int maximumDeadlineHorizonTicks,
        int cancellationsPerTick,
        int descendantsPerSkillInstance,
        int definitionLeasesPerServer,
        int runtimeBudgetAttributionStatesPerServer) {
    P5RuntimeLimits {
        var requestedValues = P5RuntimeLimitValidation.values(
                pendingEventsPerSkillInstance,
                pendingEventsPerAttribution,
                pendingEventsPerServer,
                activeSkillInstancesPerAttribution,
                activeSkillInstancesPerServer,
                rootAdmissionsPerTick,
                executionsPerSkillInstancePerTick,
                executionsPerAttributionPerTick,
                executionsPerServerPerTick,
                eventsPerSkillInstance,
                maximumDepth,
                directChildrenPerEvent,
                zeroDelayChildrenPerEvent,
                maximumDelayTicks,
                maximumDeadlineHorizonTicks,
                cancellationsPerTick);
        P5RuntimeLimitValidation.requireValid(requestedValues);
        var expected = P5RuntimeLimitValidation.deriveOrThrow(requestedValues);
        if (descendantsPerSkillInstance != expected.descendantsPerSkillInstance()
                || definitionLeasesPerServer != expected.definitionLeasesPerServer()
                || runtimeBudgetAttributionStatesPerServer
                        != expected.runtimeBudgetAttributionStatesPerServer()) {
            throw new IllegalArgumentException("P5_RUNTIME_LIMIT_DERIVATION_MISMATCH");
        }
    }

    static P5RuntimeLimits fromRequested(P5RuntimeRequestedLimits requested) {
        Objects.requireNonNull(requested, "requested");
        var values = P5RuntimeLimitValidation.values(requested);
        P5RuntimeLimitValidation.requireValid(values);
        var derived = P5RuntimeLimitValidation.deriveOrThrow(values);
        return new P5RuntimeLimits(
                requested.pendingEventsPerSkillInstance(),
                requested.pendingEventsPerAttribution(),
                requested.pendingEventsPerServer(),
                requested.activeSkillInstancesPerAttribution(),
                requested.activeSkillInstancesPerServer(),
                requested.rootAdmissionsPerTick(),
                requested.executionsPerSkillInstancePerTick(),
                requested.executionsPerAttributionPerTick(),
                requested.executionsPerServerPerTick(),
                requested.eventsPerSkillInstance(),
                requested.maximumDepth(),
                requested.directChildrenPerEvent(),
                requested.zeroDelayChildrenPerEvent(),
                requested.maximumDelayTicks(),
                requested.maximumDeadlineHorizonTicks(),
                requested.cancellationsPerTick(),
                derived.descendantsPerSkillInstance(),
                derived.definitionLeasesPerServer(),
                derived.runtimeBudgetAttributionStatesPerServer());
    }
}

sealed interface P5RuntimeLimitLoadState
        permits P5RuntimeLimitLoadState.Requested,
                P5RuntimeLimitLoadState.Invalid,
                P5RuntimeLimitLoadState.Unavailable {
    record Requested(P5RuntimeRequestedLimits limits) implements P5RuntimeLimitLoadState {
        public Requested {
            Objects.requireNonNull(limits, "limits");
        }
    }

    record Invalid(P5RuntimeConfigurationFailure failure) implements P5RuntimeLimitLoadState {
        public Invalid {
            Objects.requireNonNull(failure, "failure");
        }
    }

    enum Unavailable implements P5RuntimeLimitLoadState {
        INSTANCE
    }
}

final class P5RuntimeLimitValidation {
    private static final P5RuntimeLimitKey[] ORDERED_KEYS = P5RuntimeLimitKey.values();
    private static final int REQUESTED_LIMIT_COUNT = 16;
    private static final int MAX_PENDING_EVENTS_PER_ATTRIBUTION = requireEqualHardMaximum(
            MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_PLAYER,
            MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_NON_PLAYER_DOMAIN);
    private static final int MAX_EXECUTIONS_PER_ATTRIBUTION_PER_TICK = requireEqualHardMaximum(
            MagicSafetyCeilings.MAX_EXECUTIONS_PER_PLAYER_PER_TICK,
            MagicSafetyCeilings.MAX_EXECUTIONS_PER_NON_PLAYER_DOMAIN_PER_TICK);

    private P5RuntimeLimitValidation() {
    }

    static int[] values(P5RuntimeRequestedLimits requested) {
        Objects.requireNonNull(requested, "requested");
        return values(
                requested.pendingEventsPerSkillInstance(),
                requested.pendingEventsPerAttribution(),
                requested.pendingEventsPerServer(),
                requested.activeSkillInstancesPerAttribution(),
                requested.activeSkillInstancesPerServer(),
                requested.rootAdmissionsPerTick(),
                requested.executionsPerSkillInstancePerTick(),
                requested.executionsPerAttributionPerTick(),
                requested.executionsPerServerPerTick(),
                requested.eventsPerSkillInstance(),
                requested.maximumDepth(),
                requested.directChildrenPerEvent(),
                requested.zeroDelayChildrenPerEvent(),
                requested.maximumDelayTicks(),
                requested.maximumDeadlineHorizonTicks(),
                requested.cancellationsPerTick());
    }

    static int[] values(
            int pendingEventsPerSkillInstance,
            int pendingEventsPerAttribution,
            int pendingEventsPerServer,
            int activeSkillInstancesPerAttribution,
            int activeSkillInstancesPerServer,
            int rootAdmissionsPerTick,
            int executionsPerSkillInstancePerTick,
            int executionsPerAttributionPerTick,
            int executionsPerServerPerTick,
            int eventsPerSkillInstance,
            int maximumDepth,
            int directChildrenPerEvent,
            int zeroDelayChildrenPerEvent,
            int maximumDelayTicks,
            int maximumDeadlineHorizonTicks,
            int cancellationsPerTick) {
        return new int[] {
            pendingEventsPerSkillInstance,
            pendingEventsPerAttribution,
            pendingEventsPerServer,
            activeSkillInstancesPerAttribution,
            activeSkillInstancesPerServer,
            rootAdmissionsPerTick,
            executionsPerSkillInstancePerTick,
            executionsPerAttributionPerTick,
            executionsPerServerPerTick,
            eventsPerSkillInstance,
            maximumDepth,
            directChildrenPerEvent,
            zeroDelayChildrenPerEvent,
            maximumDelayTicks,
            maximumDeadlineHorizonTicks,
            cancellationsPerTick
        };
    }

    static Optional<P5RuntimeConfigurationFailure> firstFailure(int[] values) {
        requireComplete(values);
        for (var key : ORDERED_KEYS) {
            var value = values[key.ordinal()];
            if (value < minimum(key)) {
                return Optional.of(singleKeyFailure(
                        P5RuntimeConfigurationFailureReason.BELOW_MINIMUM, key));
            }
            if (value > hardMaximum(key)) {
                return Optional.of(singleKeyFailure(
                        P5RuntimeConfigurationFailureReason.ABOVE_HARD_MAXIMUM, key));
            }
        }

        var relationFailure = firstRelationFailure(values);
        if (relationFailure.isPresent()) {
            return relationFailure;
        }
        return firstDerivationFailure(values);
    }

    static void requireValid(int[] values) {
        var failure = firstFailure(values);
        if (failure.isPresent()) {
            throw new P5RuntimeConfigurationException(failure.get());
        }
    }

    static DerivedLimits deriveOrThrow(int[] values) {
        requireComplete(values);
        int descendants;
        try {
            descendants = Math.subtractExact(
                    value(values, P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE), 1);
        } catch (ArithmeticException overflow) {
            throw new P5RuntimeConfigurationException(derivationFailure(
                    P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE, Optional.empty()));
        }

        int definitionLeases;
        try {
            definitionLeases = Math.toIntExact((long) value(
                    values, P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER));
        } catch (ArithmeticException overflow) {
            throw new P5RuntimeConfigurationException(derivationFailure(
                    P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER, Optional.empty()));
        }

        int attributionStates;
        try {
            attributionStates = Math.addExact(
                    value(values, P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER),
                    value(values, P5RuntimeLimitKey.ROOT_ADMISSIONS_PER_TICK));
        } catch (ArithmeticException overflow) {
            throw new P5RuntimeConfigurationException(derivationFailure(
                    P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER,
                    Optional.of(P5RuntimeLimitKey.ROOT_ADMISSIONS_PER_TICK)));
        }
        return new DerivedLimits(descendants, definitionLeases, attributionStates);
    }

    static int minimum(P5RuntimeLimitKey key) {
        Objects.requireNonNull(key, "key");
        return switch (key) {
            case EXECUTIONS_PER_SERVER_PER_TICK -> 2;
            case MAXIMUM_DEPTH,
                    DIRECT_CHILDREN_PER_EVENT,
                    ZERO_DELAY_CHILDREN_PER_EVENT,
                    MAXIMUM_DELAY_TICKS,
                    MAXIMUM_DEADLINE_HORIZON_TICKS -> 0;
            case PENDING_EVENTS_PER_SKILL_INSTANCE,
                    PENDING_EVENTS_PER_ATTRIBUTION,
                    PENDING_EVENTS_PER_SERVER,
                    ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION,
                    ACTIVE_SKILL_INSTANCES_PER_SERVER,
                    ROOT_ADMISSIONS_PER_TICK,
                    EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK,
                    EXECUTIONS_PER_ATTRIBUTION_PER_TICK,
                    EVENTS_PER_SKILL_INSTANCE,
                    CANCELLATIONS_PER_TICK -> 1;
        };
    }

    static int defaultValue(P5RuntimeLimitKey key) {
        return hardMaximum(key);
    }

    static int hardMaximum(P5RuntimeLimitKey key) {
        Objects.requireNonNull(key, "key");
        return switch (key) {
            case PENDING_EVENTS_PER_SKILL_INSTANCE ->
                    MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SKILL_INSTANCE;
            case PENDING_EVENTS_PER_ATTRIBUTION -> MAX_PENDING_EVENTS_PER_ATTRIBUTION;
            case PENDING_EVENTS_PER_SERVER ->
                    MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SERVER;
            case ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION ->
                    MagicSafetyCeilings.MAX_ACTIVE_SKILL_INSTANCES_PER_BUDGET_ATTRIBUTION;
            case ACTIVE_SKILL_INSTANCES_PER_SERVER ->
                    MagicSafetyCeilings.MAX_ACTIVE_LINEAGES_PER_SERVER;
            case ROOT_ADMISSIONS_PER_TICK ->
                    MagicSafetyCeilings.MAX_ROOT_ADMISSIONS_PER_TICK;
            case EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK ->
                    MagicSafetyCeilings.MAX_EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK;
            case EXECUTIONS_PER_ATTRIBUTION_PER_TICK ->
                    MAX_EXECUTIONS_PER_ATTRIBUTION_PER_TICK;
            case EXECUTIONS_PER_SERVER_PER_TICK ->
                    MagicSafetyCeilings.MAX_EXECUTIONS_PER_SERVER_PER_TICK;
            case EVENTS_PER_SKILL_INSTANCE -> MagicSafetyCeilings.MAX_EVENTS_PER_LINEAGE;
            case MAXIMUM_DEPTH -> MagicSafetyCeilings.MAX_DEPTH_PER_LINEAGE;
            case DIRECT_CHILDREN_PER_EVENT ->
                    MagicSafetyCeilings.MAX_DIRECT_CHILDREN_PER_EVENT;
            case ZERO_DELAY_CHILDREN_PER_EVENT ->
                    MagicSafetyCeilings.MAX_ZERO_DELAY_CHILDREN_PER_EVENT;
            case MAXIMUM_DELAY_TICKS -> MagicSafetyCeilings.MAX_DELAY_TICKS;
            case MAXIMUM_DEADLINE_HORIZON_TICKS ->
                    MagicSafetyCeilings.MAX_DEADLINE_HORIZON_TICKS;
            case CANCELLATIONS_PER_TICK ->
                    MagicSafetyCeilings.MAX_CANCELLATIONS_PER_TICK;
        };
    }

    static P5RuntimeConfigurationFailure singleKeyFailure(
            P5RuntimeConfigurationFailureReason reason,
            P5RuntimeLimitKey primaryKey) {
        return new P5RuntimeConfigurationFailure(
                reason, Optional.of(primaryKey), Optional.empty());
    }

    static P5RuntimeConfigurationFailure unavailableFailure() {
        return new P5RuntimeConfigurationFailure(
                P5RuntimeConfigurationFailureReason.CONFIG_UNAVAILABLE,
                Optional.empty(),
                Optional.empty());
    }

    private static Optional<P5RuntimeConfigurationFailure> firstRelationFailure(int[] values) {
        if (value(values, P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE)
                > value(values, P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION)) {
            return relationFailure(
                    P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE,
                    P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION);
        }
        if (value(values, P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION)
                > value(values, P5RuntimeLimitKey.PENDING_EVENTS_PER_SERVER)) {
            return relationFailure(
                    P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION,
                    P5RuntimeLimitKey.PENDING_EVENTS_PER_SERVER);
        }
        if (value(values, P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION)
                > value(values, P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER)) {
            return relationFailure(
                    P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION,
                    P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER);
        }
        if (value(values, P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION)
                > value(values, P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION)) {
            return relationFailure(
                    P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION,
                    P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION);
        }
        if (value(values, P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER)
                > value(values, P5RuntimeLimitKey.PENDING_EVENTS_PER_SERVER)) {
            return relationFailure(
                    P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER,
                    P5RuntimeLimitKey.PENDING_EVENTS_PER_SERVER);
        }
        if (value(values, P5RuntimeLimitKey.EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK)
                > value(values, P5RuntimeLimitKey.EXECUTIONS_PER_ATTRIBUTION_PER_TICK)) {
            return relationFailure(
                    P5RuntimeLimitKey.EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK,
                    P5RuntimeLimitKey.EXECUTIONS_PER_ATTRIBUTION_PER_TICK);
        }
        if (value(values, P5RuntimeLimitKey.EXECUTIONS_PER_ATTRIBUTION_PER_TICK)
                >= value(values, P5RuntimeLimitKey.EXECUTIONS_PER_SERVER_PER_TICK)) {
            return relationFailure(
                    P5RuntimeLimitKey.EXECUTIONS_PER_ATTRIBUTION_PER_TICK,
                    P5RuntimeLimitKey.EXECUTIONS_PER_SERVER_PER_TICK);
        }
        if (value(values, P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE)
                > value(values, P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE)) {
            return relationFailure(
                    P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE,
                    P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE);
        }
        if (value(values, P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT)
                > value(values, P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE) - 1) {
            return relationFailure(
                    P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT,
                    P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE);
        }
        if (value(values, P5RuntimeLimitKey.ZERO_DELAY_CHILDREN_PER_EVENT)
                > value(values, P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT)) {
            return relationFailure(
                    P5RuntimeLimitKey.ZERO_DELAY_CHILDREN_PER_EVENT,
                    P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT);
        }
        if (value(values, P5RuntimeLimitKey.MAXIMUM_DEPTH) == 0
                && value(values, P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT) != 0) {
            return relationFailure(
                    P5RuntimeLimitKey.MAXIMUM_DEPTH,
                    P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT);
        }
        if (value(values, P5RuntimeLimitKey.MAXIMUM_DELAY_TICKS)
                > value(values, P5RuntimeLimitKey.MAXIMUM_DEADLINE_HORIZON_TICKS)) {
            return relationFailure(
                    P5RuntimeLimitKey.MAXIMUM_DELAY_TICKS,
                    P5RuntimeLimitKey.MAXIMUM_DEADLINE_HORIZON_TICKS);
        }
        return Optional.empty();
    }

    private static Optional<P5RuntimeConfigurationFailure> firstDerivationFailure(int[] values) {
        try {
            Math.subtractExact(value(values, P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE), 1);
        } catch (ArithmeticException overflow) {
            return Optional.of(derivationFailure(
                    P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE, Optional.empty()));
        }
        try {
            Math.toIntExact((long) value(
                    values, P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER));
        } catch (ArithmeticException overflow) {
            return Optional.of(derivationFailure(
                    P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER, Optional.empty()));
        }
        try {
            Math.addExact(
                    value(values, P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER),
                    value(values, P5RuntimeLimitKey.ROOT_ADMISSIONS_PER_TICK));
        } catch (ArithmeticException overflow) {
            return Optional.of(derivationFailure(
                    P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER,
                    Optional.of(P5RuntimeLimitKey.ROOT_ADMISSIONS_PER_TICK)));
        }
        return Optional.empty();
    }

    private static Optional<P5RuntimeConfigurationFailure> relationFailure(
            P5RuntimeLimitKey primaryKey,
            P5RuntimeLimitKey relatedKey) {
        return Optional.of(new P5RuntimeConfigurationFailure(
                P5RuntimeConfigurationFailureReason.RELATION_VIOLATION,
                Optional.of(primaryKey),
                Optional.of(relatedKey)));
    }

    private static P5RuntimeConfigurationFailure derivationFailure(
            P5RuntimeLimitKey primaryKey,
            Optional<P5RuntimeLimitKey> relatedKey) {
        return new P5RuntimeConfigurationFailure(
                P5RuntimeConfigurationFailureReason.DERIVATION_OVERFLOW,
                Optional.of(primaryKey),
                relatedKey);
    }

    private static int value(int[] values, P5RuntimeLimitKey key) {
        return values[key.ordinal()];
    }

    private static void requireComplete(int[] values) {
        Objects.requireNonNull(values, "values");
        if (values.length != REQUESTED_LIMIT_COUNT || ORDERED_KEYS.length != REQUESTED_LIMIT_COUNT) {
            throw new IllegalArgumentException("P5_RUNTIME_LIMIT_COUNT_MISMATCH");
        }
    }

    private static int requireEqualHardMaximum(int first, int second) {
        if (first != second) {
            throw new ExceptionInInitializerError("P5_RUNTIME_SHARED_HARD_MAXIMUM_MISMATCH");
        }
        return first;
    }

    record DerivedLimits(
            int descendantsPerSkillInstance,
            int definitionLeasesPerServer,
            int runtimeBudgetAttributionStatesPerServer) {}
}
