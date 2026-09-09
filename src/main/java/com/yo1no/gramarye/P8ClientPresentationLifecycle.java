package com.yo1no.gramarye;

import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Client-only event wiring for the one P8 presentation state owner. */
final class P8ClientPresentationLifecycle {
    private final P8ClientPresentationState state;

    P8ClientPresentationLifecycle(
            P8ClientPresentationState state, IEventBus modBus) {
        this.state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(modBus, "modBus")
                .addListener(this::onRegisterClientReloadListeners);
        NeoForge.EVENT_BUS.addListener(
                ClientPlayerNetworkEvent.LoggingIn.class, this::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class, this::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, this::onClientLevelLoad);
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, this::onClientLevelUnload);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, this::onClientPostTick);
    }

    private void onRegisterClientReloadListeners(
            RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new P8ResourceReloadListener(state));
    }

    private void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn ignored) {
        state.onConnectionOpened();
        if (Minecraft.getInstance().level != null) {
            state.onWorldLoaded();
        }
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut ignored) {
        state.onLoggedOut();
    }

    private void onClientLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel) {
            state.onWorldLoaded();
        }
    }

    private void onClientLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            state.onWorldUnloaded(Minecraft.getInstance().level != null);
        }
    }

    private void onClientPostTick(ClientTickEvent.Post ignored) {
        state.onClientPostTick();
    }

    private static final class P8ResourceReloadListener
            extends SimplePreparableReloadListener<P8ClientResourceIndex> {
        private final P8ClientPresentationState state;

        private P8ResourceReloadListener(P8ClientPresentationState state) {
            this.state = state;
        }

        @Override
        protected P8ClientResourceIndex prepare(
                ResourceManager resourceManager, ProfilerFiller profiler) {
            Objects.requireNonNull(resourceManager, "resourceManager");
            Objects.requireNonNull(profiler, "profiler");
            return P8ClientResourceIndex.empty();
        }

        @Override
        protected void apply(
                P8ClientResourceIndex replacement,
                ResourceManager resourceManager,
                ProfilerFiller profiler) {
            Objects.requireNonNull(resourceManager, "resourceManager");
            Objects.requireNonNull(profiler, "profiler");
            state.onResourceIndexApplied(replacement);
        }
    }
}
