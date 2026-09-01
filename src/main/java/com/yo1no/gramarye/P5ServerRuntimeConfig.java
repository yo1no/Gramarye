package com.yo1no.gramarye;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

final class P5ServerRuntimeConfig {
    static final String CONFIG_FILE_NAME = "gramarye-server.toml";

    private final AtomicReference<P5RuntimeLimitLoadState> nextSlotState =
            new AtomicReference<>(P5RuntimeLimitLoadState.Unavailable.INSTANCE);
    private final AtomicReference<Optional<P5RuntimeReloadDisposition>> reloadDisposition =
            new AtomicReference<>(Optional.empty());
    private final P5RawServerConfigSpec rawSpec;

    P5ServerRuntimeConfig(IEventBus modBus, ModContainer exactContainer) {
        Objects.requireNonNull(modBus, "modBus");
        Objects.requireNonNull(exactContainer, "exactContainer");
        if (!Gramarye.MOD_ID.equals(exactContainer.getModId())) {
            throw registrationMismatch();
        }
        rawSpec = new P5RawServerConfigSpec(nextSlotState);
        exactContainer.registerConfig(ModConfig.Type.SERVER, rawSpec, CONFIG_FILE_NAME);
        modBus.addListener(this::handleRuntimeConfigReloading);
        modBus.addListener(this::handleRuntimeConfigUnloading);
    }

    P5RuntimeLimits snapshotForStarted() {
        var candidate = nextSlotState.get();
        return snapshotCandidate(candidate);
    }

    Optional<P5RuntimeReloadDisposition> latestReloadDisposition() {
        return reloadDisposition.get();
    }

    void handleRuntimeConfigReloading(ModConfigEvent.Reloading event) {
        Objects.requireNonNull(event, "event");
        if (!matchesRuntimeConfig(rawSpec, event.getConfig())) {
            return;
        }
        var acceptedState = nextSlotState.get();
        reloadDisposition.set(Optional.of(reloadDispositionFor(acceptedState)));
    }

    void handleRuntimeConfigUnloading(ModConfigEvent.Unloading event) {
        Objects.requireNonNull(event, "event");
        if (!matchesRuntimeConfig(rawSpec, event.getConfig())) {
            return;
        }
        nextSlotState.set(P5RuntimeLimitLoadState.Unavailable.INSTANCE);
        reloadDisposition.set(Optional.empty());
    }

    static P5RuntimeLimits snapshotCandidate(P5RuntimeLimitLoadState candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return switch (candidate) {
            case P5RuntimeLimitLoadState.Requested requested ->
                    P5RuntimeLimits.fromRequested(requested.limits());
            case P5RuntimeLimitLoadState.Invalid invalid ->
                    throw new P5RuntimeConfigurationException(invalid.failure());
            case P5RuntimeLimitLoadState.Unavailable ignored ->
                    throw new P5RuntimeConfigurationException(
                            P5RuntimeLimitValidation.unavailableFailure());
        };
    }

    static P5RuntimeReloadDisposition reloadDispositionFor(P5RuntimeLimitLoadState acceptedState) {
        Objects.requireNonNull(acceptedState, "acceptedState");
        return acceptedState instanceof P5RuntimeLimitLoadState.Requested
                ? P5RuntimeReloadDisposition.DEFERRED_UNTIL_NEXT_SERVER_SLOT
                : P5RuntimeReloadDisposition.INVALID_FOR_NEXT_SERVER_SLOT;
    }

    static boolean matchesRuntimeConfig(IConfigSpec expectedSpec, ModConfig config) {
        Objects.requireNonNull(expectedSpec, "expectedSpec");
        return config != null
                && config.getSpec() == expectedSpec
                && config.getType() == ModConfig.Type.SERVER
                && Gramarye.MOD_ID.equals(config.getModId())
                && CONFIG_FILE_NAME.equals(config.getFileName());
    }

    static IllegalStateException registrationMismatch() {
        return new IllegalStateException("P5_RUNTIME_CONFIG_SPEC_REGISTRATION_MISMATCH");
    }
}

final class P5RawServerConfigSpec implements IConfigSpec {
    private static final P5RuntimeLimitKey[] ORDERED_KEYS = P5RuntimeLimitKey.values();

    private final AtomicReference<P5RuntimeLimitLoadState> nextSlotState;

    P5RawServerConfigSpec(AtomicReference<P5RuntimeLimitLoadState> nextSlotState) {
        this.nextSlotState = Objects.requireNonNull(nextSlotState, "nextSlotState");
        Objects.requireNonNull(nextSlotState.get(), "nextSlotState value");
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public void validateSpec(ModConfig config) {
        if (!P5ServerRuntimeConfig.matchesRuntimeConfig(this, config)) {
            throw P5ServerRuntimeConfig.registrationMismatch();
        }
    }

    @Override
    public boolean isCorrect(UnmodifiableCommentedConfig config) {
        Objects.requireNonNull(config, "config");
        return true;
    }

    @Override
    public void correct(CommentedConfig config) {
        Objects.requireNonNull(config, "config");
        if (!config.isEmpty()) {
            throw new IllegalStateException("P5_RUNTIME_CONFIG_CORRECT_NONEMPTY");
        }
        for (var key : ORDERED_KEYS) {
            config.set(rawPath(key), P5RuntimeLimitValidation.defaultValue(key));
        }
    }

    @Override
    public void acceptConfig(ILoadedConfig loadedConfig) {
        if (loadedConfig == null) {
            nextSlotState.set(P5RuntimeLimitLoadState.Unavailable.INSTANCE);
            return;
        }
        acceptRawConfig(loadedConfig.config());
    }

    void acceptRawConfig(UnmodifiableCommentedConfig rawConfig) {
        Objects.requireNonNull(rawConfig, "rawConfig");
        nextSlotState.set(decode(rawConfig));
    }

    private static P5RuntimeLimitLoadState decode(UnmodifiableCommentedConfig rawConfig) {
        var values = new int[ORDERED_KEYS.length];
        for (var key : ORDERED_KEYS) {
            var path = rawPath(key);
            if (!rawConfig.contains(path)) {
                return invalid(P5RuntimeLimitValidation.singleKeyFailure(
                        P5RuntimeConfigurationFailureReason.MISSING_REQUIRED_VALUE, key));
            }

            var rawValue = rawConfig.getRaw(path);
            long integralValue;
            if (rawValue instanceof Integer integerValue) {
                integralValue = integerValue.longValue();
            } else if (rawValue instanceof Long longValue) {
                integralValue = longValue;
            } else {
                return invalid(P5RuntimeLimitValidation.singleKeyFailure(
                        P5RuntimeConfigurationFailureReason.WRONG_VALUE_TYPE, key));
            }

            if (integralValue < P5RuntimeLimitValidation.minimum(key)) {
                return invalid(P5RuntimeLimitValidation.singleKeyFailure(
                        P5RuntimeConfigurationFailureReason.BELOW_MINIMUM, key));
            }
            if (integralValue > P5RuntimeLimitValidation.hardMaximum(key)) {
                return invalid(P5RuntimeLimitValidation.singleKeyFailure(
                        P5RuntimeConfigurationFailureReason.ABOVE_HARD_MAXIMUM, key));
            }
            values[key.ordinal()] = (int) integralValue;
        }

        var failure = P5RuntimeLimitValidation.firstFailure(values);
        if (failure.isPresent()) {
            return invalid(failure.get());
        }

        try {
            return new P5RuntimeLimitLoadState.Requested(new P5RuntimeRequestedLimits(
                    values[P5RuntimeLimitKey.PENDING_EVENTS_PER_SKILL_INSTANCE.ordinal()],
                    values[P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION.ordinal()],
                    values[P5RuntimeLimitKey.PENDING_EVENTS_PER_SERVER.ordinal()],
                    values[P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION.ordinal()],
                    values[P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER.ordinal()],
                    values[P5RuntimeLimitKey.ROOT_ADMISSIONS_PER_TICK.ordinal()],
                    values[P5RuntimeLimitKey.EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK.ordinal()],
                    values[P5RuntimeLimitKey.EXECUTIONS_PER_ATTRIBUTION_PER_TICK.ordinal()],
                    values[P5RuntimeLimitKey.EXECUTIONS_PER_SERVER_PER_TICK.ordinal()],
                    values[P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE.ordinal()],
                    values[P5RuntimeLimitKey.MAXIMUM_DEPTH.ordinal()],
                    values[P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT.ordinal()],
                    values[P5RuntimeLimitKey.ZERO_DELAY_CHILDREN_PER_EVENT.ordinal()],
                    values[P5RuntimeLimitKey.MAXIMUM_DELAY_TICKS.ordinal()],
                    values[P5RuntimeLimitKey.MAXIMUM_DEADLINE_HORIZON_TICKS.ordinal()],
                    values[P5RuntimeLimitKey.CANCELLATIONS_PER_TICK.ordinal()]));
        } catch (P5RuntimeConfigurationException invalidConfiguration) {
            return invalid(invalidConfiguration.failure());
        }
    }

    static String rawPath(P5RuntimeLimitKey key) {
        Objects.requireNonNull(key, "key");
        return switch (key) {
            case PENDING_EVENTS_PER_SKILL_INSTANCE ->
                    "runtime.pendingEventsPerSkillInstance";
            case PENDING_EVENTS_PER_ATTRIBUTION -> "runtime.pendingEventsPerAttribution";
            case PENDING_EVENTS_PER_SERVER -> "runtime.pendingEventsPerServer";
            case ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION ->
                    "runtime.activeSkillInstancesPerAttribution";
            case ACTIVE_SKILL_INSTANCES_PER_SERVER ->
                    "runtime.activeSkillInstancesPerServer";
            case ROOT_ADMISSIONS_PER_TICK -> "runtime.rootAdmissionsPerTick";
            case EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK ->
                    "runtime.executionsPerSkillInstancePerTick";
            case EXECUTIONS_PER_ATTRIBUTION_PER_TICK ->
                    "runtime.executionsPerAttributionPerTick";
            case EXECUTIONS_PER_SERVER_PER_TICK ->
                    "runtime.executionsPerServerPerTick";
            case EVENTS_PER_SKILL_INSTANCE -> "runtime.eventsPerSkillInstance";
            case MAXIMUM_DEPTH -> "runtime.maximumDepth";
            case DIRECT_CHILDREN_PER_EVENT -> "runtime.directChildrenPerEvent";
            case ZERO_DELAY_CHILDREN_PER_EVENT -> "runtime.zeroDelayChildrenPerEvent";
            case MAXIMUM_DELAY_TICKS -> "runtime.maximumDelayTicks";
            case MAXIMUM_DEADLINE_HORIZON_TICKS ->
                    "runtime.maximumDeadlineHorizonTicks";
            case CANCELLATIONS_PER_TICK -> "runtime.cancellationsPerTick";
        };
    }

    private static P5RuntimeLimitLoadState.Invalid invalid(
            P5RuntimeConfigurationFailure failure) {
        return new P5RuntimeLimitLoadState.Invalid(failure);
    }
}
