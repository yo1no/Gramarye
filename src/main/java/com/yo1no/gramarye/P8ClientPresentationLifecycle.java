package com.yo1no.gramarye;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Client-only event wiring for the one P8 presentation state owner. */
final class P8ClientPresentationLifecycle {
    private final P8ClientPresentationState state;
    private final P8ClientPresentationExecution execution;
    private boolean pendingWorldLoad;

    P8ClientPresentationLifecycle(
            P8ClientPresentationState state,
            P8ClientPresentationExecution execution,
            IEventBus modBus) {
        this.state = Objects.requireNonNull(state, "state");
        this.execution = Objects.requireNonNull(execution, "execution");
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
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, this::onClientPostTick);
        NeoForge.EVENT_BUS.addListener(
                RenderLevelStageEvent.class, this::onRenderLevelStage);
    }

    private void onRegisterClientReloadListeners(
            RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new P8ResourceReloadListener(state));
    }

    private void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn ignored) {
        pendingWorldLoad = false;
        RuntimeException primaryRuntimeFailure = null;
        Error primaryFailure = null;
        try {
            state.onConnectionOpened();
        } catch (RuntimeException failure) {
            primaryRuntimeFailure = failure;
        } catch (Error failure) {
            primaryFailure = failure;
        }
        if (Minecraft.getInstance().level != null) {
            try {
                state.onWorldLoaded();
            } catch (RuntimeException failure) {
                if (primaryRuntimeFailure == null && primaryFailure == null) {
                    primaryRuntimeFailure = failure;
                }
            } catch (Error failure) {
                if (primaryFailure == null) {
                    primaryFailure = failure;
                }
            }
        }
        if (primaryFailure != null) {
            throw primaryFailure;
        }
        if (primaryRuntimeFailure != null) {
            throw primaryRuntimeFailure;
        }
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut ignored) {
        try {
            state.onLoggedOut();
        } finally {
            pendingWorldLoad = false;
        }
    }

    private void onClientLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel) {
            // During a dimension change the new level's Load event precedes the
            // old level's Unload event. Each event independently advances the
            // world generation; this token only tells the later Unload that a
            // current world remains and never retains either level.
            pendingWorldLoad = state.worldReady();
            state.onWorldLoaded();
        }
    }

    private void onClientLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            var currentLevel = Minecraft.getInstance().level;
            if (pendingWorldLoad) {
                pendingWorldLoad = false;
                state.onWorldUnloaded(true);
            } else if (state.worldReady()
                    && (currentLevel == null || event.getLevel() == currentLevel)) {
                state.onWorldUnloaded(false);
            }
        }
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
