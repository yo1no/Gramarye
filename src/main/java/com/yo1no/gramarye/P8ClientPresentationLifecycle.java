package com.yo1no.gramarye;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Client-only event wiring for the one P8 presentation state owner. */
final class P8ClientPresentationLifecycle {
    private final P8ClientPresentationState state;
    private final P8ClientPresentationExecution execution;
    private boolean pendingWorldLoad;
    private long pendingWorldLoadGeneration;

    P8ClientPresentationLifecycle(
            P8ClientPresentationState state,
            P8ClientPresentationExecution execution,
            IEventBus modBus) {
        this(state, execution);
        Objects.requireNonNull(modBus, "modBus")
                .addListener(this::onRegisterClientReloadListeners);
        NeoForge.EVENT_BUS.addListener(
                ClientPlayerNetworkEvent.LoggingIn.class, this::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class, this::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, this::onClientLevelLoad);
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, this::onClientLevelUnload);
        NeoForge.EVENT_BUS.addListener(
                EntityLeaveLevelEvent.class, this::onEntityLeaveLevel);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, this::onClientPreTick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, this::onClientPostTick);
        NeoForge.EVENT_BUS.addListener(
                RenderLevelStageEvent.class, this::onRenderLevelStage);
    }

    private P8ClientPresentationLifecycle(
            P8ClientPresentationState state,
            P8ClientPresentationExecution execution) {
        this.state = Objects.requireNonNull(state, "state");
        this.execution = Objects.requireNonNull(execution, "execution");
    }

    private void onRegisterClientReloadListeners(
            RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new P8ResourceReloadListener(state));
    }

    private void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Throwable primaryFailure = null;
        P8ClientDispatchTask catalogDrain = null;
        boolean catalogDrainClaimed = false;
        try {
            clearStalePendingWorldLoad();
            var eventConnection = event.getConnection();
            var eventPlayer = event.getPlayer();
            var eventPlayListener = eventPlayer.connection;
            if (!isCurrentLoginWitness(
                    eventConnection, eventPlayListener, eventPlayer)) {
                consumeMaintenance(state.maintainTransportLiveness());
                return;
            }
            var result = state.onConnectionOpened(
                    eventConnection, eventPlayListener);
            catalogDrain = consumeOpenMaintenanceAndSelectDrain(result);
            if (!result.opened()) {
                return;
            }
            var publishedGeneration = result.publishedGeneration();
            if (!state.isCurrentPublishedPlayGeneration(publishedGeneration)
                    || !isCurrentLoginWitness(
                            eventConnection, eventPlayListener, eventPlayer)) {
                return;
            }
            clearPendingWorldLoadUnlessGeneration(publishedGeneration);
            if (Minecraft.getInstance().level != null) {
                state.onWorldLoaded();
            }
            if (!state.isCurrentPublishedPlayGeneration(publishedGeneration)
                    || !isCurrentLoginWitness(
                            eventConnection, eventPlayListener, eventPlayer)) {
                return;
            }
            if (catalogDrain != null) {
                catalogDrainClaimed = true;
                catalogDrain.run();
            }
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
        } finally {
            var unclaimedCatalogDrain = catalogDrain != null && !catalogDrainClaimed
                    ? catalogDrain
                    : null;
            finishLifecycle(
                    primaryFailure,
                    () -> {
                        if (unclaimedCatalogDrain != null) {
                            unclaimedCatalogDrain.releaseAfterFailedEnqueue();
                        }
                    },
                    this::clearStalePendingWorldLoad);
        }
    }

    P8ClientDispatchTask consumeOpenMaintenanceAndSelectDrain(
            P8ClientConnectionOpenResult result) {
        Objects.requireNonNull(result, "result");
        consumeMaintenance(result.maintenance());
        return result.opened() ? result.catalogDrain().orElse(null) : null;
    }

    static void finishLifecycle(
            Throwable primaryFailure, Runnable... cleanupActions) {
        var terminalFailure = primaryFailure;
        for (var cleanupAction : cleanupActions) {
            try {
                Objects.requireNonNull(cleanupAction, "cleanupAction").run();
            } catch (RuntimeException | Error secondaryFailure) {
                if (terminalFailure == null) {
                    terminalFailure = secondaryFailure;
                } else if (terminalFailure != secondaryFailure) {
                    terminalFailure.addSuppressed(secondaryFailure);
                }
            }
        }
        if (terminalFailure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (terminalFailure instanceof Error errorFailure) {
            throw errorFailure;
        }
        if (terminalFailure != null) {
            throw new IllegalArgumentException(
                    "P8 lifecycle failures must be unchecked", terminalFailure);
        }
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Throwable primaryFailure = null;
        try {
            clearStalePendingWorldLoad();
            var eventPlayer = event.getPlayer();
            ICommonPacketListener eventPlayListener = eventPlayer == null
                    ? null
                    : eventPlayer.connection;
            consumeMaintenance(state.onLoggedOut(
                    event.getConnection(), eventPlayListener));
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
        } finally {
            finishLifecycle(primaryFailure, this::clearStalePendingWorldLoad);
        }
    }

    private void onClientLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel) {
            // During a dimension change the new level's Load event precedes the
            // old level's Unload event. Each event independently advances the
            // world generation; this token only tells the later Unload that a
            // current world remains and never retains either level.
            Throwable primaryFailure = null;
            try {
                clearStalePendingWorldLoad();
                var publishedGeneration = state.connectionGeneration();
                if (state.worldReady()
                        && state.isCurrentPublishedPlayGeneration(
                                publishedGeneration)) {
                    pendingWorldLoad = true;
                    pendingWorldLoadGeneration = publishedGeneration;
                }
                state.onWorldLoaded();
            } catch (RuntimeException | Error failure) {
                primaryFailure = failure;
            } finally {
                finishLifecycle(primaryFailure, this::clearStalePendingWorldLoad);
            }
        }
    }

    private void onClientLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            Throwable primaryFailure = null;
            try {
                clearStalePendingWorldLoad();
                var currentLevel = Minecraft.getInstance().level;
                if (pendingWorldLoad
                        && state.isCurrentPublishedPlayGeneration(
                                pendingWorldLoadGeneration)) {
                    clearPendingWorldLoad();
                    state.onWorldUnloaded(true);
                } else if (state.worldReady()
                        && (currentLevel == null || event.getLevel() == currentLevel)) {
                    state.onWorldUnloaded(false);
                }
            } catch (RuntimeException | Error failure) {
                primaryFailure = failure;
            } finally {
                finishLifecycle(primaryFailure, this::clearStalePendingWorldLoad);
            }
        }
    }

    private void onClientPreTick(ClientTickEvent.Pre ignored) {
        Throwable primaryFailure = null;
        try {
            clearStalePendingWorldLoad();
            consumeMaintenance(state.maintainTransportLiveness());
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
        } finally {
            finishLifecycle(primaryFailure, this::clearStalePendingWorldLoad);
        }
    }

    private static boolean isCurrentLoginWitness(
            Connection eventConnection,
            ICommonPacketListener eventPlayListener,
            net.minecraft.client.player.LocalPlayer eventPlayer) {
        var minecraft = Minecraft.getInstance();
        return minecraft.player == eventPlayer
                && minecraft.getConnection() == eventPlayListener
                && eventPlayListener.getConnection() == eventConnection
                && eventConnection.getPacketListener() == eventPlayListener;
    }

    void consumeMaintenance(P8ClientTransportMaintenanceResult result) {
        Objects.requireNonNull(result, "result");
        var invalidated = result.invalidatedPublishedGeneration();
        if (invalidated.isPresent()
                && pendingWorldLoad
                && pendingWorldLoadGeneration == invalidated.getAsLong()) {
            clearPendingWorldLoad();
        }
    }

    private void clearPendingWorldLoadUnlessGeneration(long generation) {
        if (pendingWorldLoad && pendingWorldLoadGeneration != generation) {
            clearPendingWorldLoad();
        }
    }

    private void clearStalePendingWorldLoad() {
        if (pendingWorldLoad
                && !state.isCurrentPublishedPlayGeneration(
                        pendingWorldLoadGeneration)) {
            clearPendingWorldLoad();
        }
    }

    private void clearPendingWorldLoad() {
        pendingWorldLoad = false;
        pendingWorldLoadGeneration = 0L;
    }

    private void onClientPostTick(ClientTickEvent.Post ignored) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()
                || minecraft.level != null
                        && !minecraft.level.tickRateManager().runsNormally()) {
            return;
        }
        state.onClientPostTick();
    }

    private void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ClientLevel
                && event.getLevel() == Minecraft.getInstance().level) {
            execution.onEntityUnavailable(event.getEntity().getId());
        }
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            var snapshot = execution.trailRenderSnapshot();
            try {
                P8ClientTrailRenderer.render(snapshot, event);
            } catch (RuntimeException failure) {
                execution.onRenderRuntimeFailure(snapshot);
            } catch (Error failure) {
                throw execution.renderErrorAfterCleanup(snapshot, failure);
            }
        }
    }

    private static final class P8ResourceReloadListener
            extends SimplePreparableReloadListener<PreparedResourceIndex> {
        private final P8ClientPresentationState state;
        private final Object publicationLock = new Object();
        private Object latestPrepare;

        private P8ResourceReloadListener(P8ClientPresentationState state) {
            this.state = state;
        }

        @Override
        protected PreparedResourceIndex prepare(
                ResourceManager resourceManager, ProfilerFiller profiler) {
            Objects.requireNonNull(resourceManager, "resourceManager");
            Objects.requireNonNull(profiler, "profiler");
            var identity = new Object();
            synchronized (publicationLock) {
                latestPrepare = identity;
            }
            try {
                var resources = resourceManager.listResources("", ignored -> true);
                var inspectedIds = new ArrayList<ResourceLocation>(
                        PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES);
                var resourceIds = resources.keySet().iterator();
                while (inspectedIds.size()
                                < PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES
                        && resourceIds.hasNext()) {
                    // Locked MultiPackResourceManager returns a TreeMap, so encounter
                    // order is already the required lexical effective-resource order.
                    inspectedIds.add(resourceIds.next());
                }
                var boundedIds = inspectedIds.stream()
                        .filter(id -> id.toString().getBytes(StandardCharsets.UTF_8).length
                                <= PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES)
                        .toList();
                return new PreparedResourceIndex(
                        identity,
                        new P8ClientResourceIndex(
                                boundedIds,
                                resources.size()
                                                > PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES
                                        || boundedIds.size() != inspectedIds.size()));
            } catch (RuntimeException | Error failure) {
                synchronized (publicationLock) {
                    if (latestPrepare == identity) {
                        latestPrepare = null;
                    }
                }
                throw failure;
            }
        }

        @Override
        protected void apply(
                PreparedResourceIndex replacement,
                ResourceManager resourceManager,
                ProfilerFiller profiler) {
            Objects.requireNonNull(replacement, "replacement");
            Objects.requireNonNull(resourceManager, "resourceManager");
            Objects.requireNonNull(profiler, "profiler");
            synchronized (publicationLock) {
                if (latestPrepare != replacement.identity()) {
                    return;
                }
                // Claim exactly once before invoking availability/cleanup work.
                // A newer platform prepare can now publish its own token without
                // waiting behind a cooperative third-party factory call.
                latestPrepare = null;
            }
            state.onResourceIndexApplied(replacement.index());
        }
    }

    private record PreparedResourceIndex(
            Object identity, P8ClientResourceIndex index) {
        private PreparedResourceIndex {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(index, "index");
        }
    }
}
