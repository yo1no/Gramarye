package com.yo1no.gramarye.magic.network;

import com.mojang.blaze3d.platform.InputConstants;
import com.yo1no.gramarye.Gramarye;
import java.util.Objects;
import java.util.OptionalLong;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client-only owner of P9 discrete cast input and its per-connection sequence. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
final class P9ClientCastInput {
    private static final int CAST_SLOT = 0;
    private static final P9ClientCastInput OWNER = new P9ClientCastInput();

    private final KeyMapping castKey = new KeyMapping(
            "key.gramarye.cast",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.gramarye");
    private boolean keyMappingRegistered;
    private boolean senderSessionAvailable;
    private boolean pendingFlushRequired = true;
    private final OutgoingSequence outgoingSequence = new OutgoingSequence();

    private P9ClientCastInput() {}

    static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        OWNER.registerKeyMappingOnce(event);
    }

    static void onConnectionOpened() {
        OWNER.handleConnectionOpened();
    }

    static void onConnectionClosed() {
        OWNER.handleConnectionClosed();
    }

    static void onClientWorldLoaded() {
        OWNER.markContextTransition();
    }

    static void onClientWorldUnloaded() {
        OWNER.markContextTransition();
    }

    static void onPlayerContextReplaced() {
        OWNER.markContextTransition();
    }

    @SubscribeEvent
    static void onClientPostTick(ClientTickEvent.Post ignored) {
        OWNER.handleClientPostTick();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onScreenOpening(ScreenEvent.Opening event) {
        OWNER.handleScreenOpening(event);
    }

    @SubscribeEvent
    static void onPauseChanged(ClientPauseChangeEvent.Post event) {
        OWNER.handlePauseChanged(event);
    }

    @SubscribeEvent
    static void onRenderFrame(RenderFrameEvent.Pre ignored) {
        OWNER.handleRenderFrame();
    }

    private void registerKeyMappingOnce(RegisterKeyMappingsEvent event) {
        Objects.requireNonNull(event, "event");
        if (keyMappingRegistered) {
            throw new P7SemanticInvariantException(
                    "P9 cast key mapping is already registered");
        }
        event.register(castKey);
        keyMappingRegistered = true;
    }

    private void handleConnectionOpened() {
        requireClientThread();
        outgoingSequence.reset();
        senderSessionAvailable = true;
        pendingFlushRequired = true;
    }

    private void handleConnectionClosed() {
        requireClientThread();
        senderSessionAvailable = false;
        outgoingSequence.reset();
        pendingFlushRequired = true;
        dropAllPendingClicks();
    }

    private void markContextTransition() {
        requireClientThread();
        pendingFlushRequired = true;
    }

    private void handleScreenOpening(ScreenEvent.Opening event) {
        Objects.requireNonNull(event, "event");
        requireClientThread();
        if (!event.isCanceled() && event.getNewScreen() != null) {
            pendingFlushRequired = true;
        }
    }

    private void handlePauseChanged(ClientPauseChangeEvent.Post event) {
        Objects.requireNonNull(event, "event");
        requireClientThread();
        if (event.isPaused()) {
            pendingFlushRequired = true;
        }
    }

    private void handleRenderFrame() {
        requireClientThread();
        if (!Minecraft.getInstance().isWindowActive()) {
            pendingFlushRequired = true;
        }
    }

    private void handleClientPostTick() {
        if (!keyMappingRegistered) {
            return;
        }
        requireClientThread();
        var minecraft = Minecraft.getInstance();
        var gate = evaluateGates(liveGateProbe(minecraft, !pendingFlushRequired));
        if (gate != GateDecision.ALLOW
                && gate != GateDecision.PENDING_FLUSH_REQUIRED) {
            pendingFlushRequired = true;
            dropAllPendingClicks();
            return;
        }
        if (gate == GateDecision.PENDING_FLUSH_REQUIRED) {
            dropAllPendingClicks();
            if (evaluateGates(liveGateProbe(minecraft, true)) == GateDecision.ALLOW) {
                pendingFlushRequired = false;
            }
            return;
        }

        // KeyMapping's pending-click counter is a finite int; this bound prevents an
        // open-ended send loop without changing the one-attempt-per-click contract.
        for (int remaining = Integer.MAX_VALUE;
                remaining > 0 && castKey.consumeClick();
                remaining--) {
            if (evaluateGates(liveGateProbe(minecraft, true)) != GateDecision.ALLOW) {
                pendingFlushRequired = true;
                dropAllPendingClicks();
                return;
            }
            outgoingSequence.sendNext(P9ClientCastInput::sendPayload);
        }
    }

    private GateProbe liveGateProbe(Minecraft minecraft, boolean noPendingFlush) {
        return new LiveGateProbe(
                minecraft,
                senderSessionAvailable,
                noPendingFlush);
    }

    private static void sendPayload(CastIntentPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    static GateDecision evaluateGates(GateProbe probe) {
        Objects.requireNonNull(probe, "probe");
        if (!probe.worldPresent()) {
            return GateDecision.NO_WORLD;
        }
        if (!probe.playerPresent()) {
            return GateDecision.NO_PLAYER;
        }
        if (!probe.playConnectionPresent()) {
            return GateDecision.NO_PLAY_CONNECTION;
        }
        if (!probe.senderSessionAvailable()) {
            return GateDecision.SENDER_SESSION_UNAVAILABLE;
        }
        if (!probe.screenAbsent()) {
            return GateDecision.SCREEN_OPEN;
        }
        if (!probe.windowActive()) {
            return GateDecision.WINDOW_INACTIVE;
        }
        if (!probe.clientNotPaused()) {
            return GateDecision.CLIENT_PAUSED;
        }
        return probe.noPendingFlush()
                ? GateDecision.ALLOW
                : GateDecision.PENDING_FLUSH_REQUIRED;
    }

    private void dropAllPendingClicks() {
        while (castKey.consumeClick()) {
            // consumeClick removes exactly one queued press from this mapping.
        }
    }

    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new P7SemanticInvariantException(
                    "P9 cast input mutation is off the client thread");
        }
    }

    interface GateProbe {
        boolean worldPresent();

        boolean playerPresent();

        boolean playConnectionPresent();

        boolean senderSessionAvailable();

        boolean screenAbsent();

        boolean windowActive();

        boolean clientNotPaused();

        boolean noPendingFlush();
    }

    enum GateDecision {
        NO_WORLD,
        NO_PLAYER,
        NO_PLAY_CONNECTION,
        SENDER_SESSION_UNAVAILABLE,
        SCREEN_OPEN,
        WINDOW_INACTIVE,
        CLIENT_PAUSED,
        PENDING_FLUSH_REQUIRED,
        ALLOW
    }

    @FunctionalInterface
    interface PayloadSender {
        void send(CastIntentPayload payload);
    }

    static final class OutgoingSequence {
        private IntentSequenceState state;

        OutgoingSequence() {
            this(IntentSequenceState.initial());
        }

        OutgoingSequence(long expectedSequence) {
            this(IntentSequenceState.expecting(expectedSequence));
        }

        private OutgoingSequence(IntentSequenceState state) {
            this.state = Objects.requireNonNull(state, "state");
        }

        void reset() {
            state = IntentSequenceState.initial();
        }

        OptionalLong expectedNext() {
            return state.expectedNext();
        }

        boolean sendNext(PayloadSender sender) {
            Objects.requireNonNull(sender, "sender");
            var expectedNext = state.expectedNext();
            if (expectedNext.isEmpty()) {
                return false;
            }
            var sequence = expectedNext.getAsLong();
            var payload = new CastIntentPayload(new CastIntent(
                    sequence, CAST_SLOT, CastInputKind.CAST, 0, null, null));
            var decision = state.evaluate(sequence);
            if (!decision.sequenceConsumed()
                    || decision.classification()
                            != IntentSequenceState.Classification.ACCEPTED) {
                throw new P7SemanticInvariantException(
                        "current P9 outgoing sequence was not accepted");
            }
            state = decision.nextState();
            sender.send(payload);
            return true;
        }
    }

    private record LiveGateProbe(
            Minecraft minecraft,
            boolean senderSessionAvailable,
            boolean noPendingFlush) implements GateProbe {
        private LiveGateProbe {
            Objects.requireNonNull(minecraft, "minecraft");
        }

        @Override
        public boolean worldPresent() {
            return minecraft.level != null;
        }

        @Override
        public boolean playerPresent() {
            return minecraft.player != null;
        }

        @Override
        public boolean playConnectionPresent() {
            return minecraft.getConnection() != null;
        }

        @Override
        public boolean screenAbsent() {
            return minecraft.screen == null;
        }

        @Override
        public boolean windowActive() {
            return minecraft.isWindowActive();
        }

        @Override
        public boolean clientNotPaused() {
            return !minecraft.isPaused();
        }
    }
}
