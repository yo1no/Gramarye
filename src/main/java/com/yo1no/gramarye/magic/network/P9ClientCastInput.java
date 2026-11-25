package com.yo1no.gramarye.magic.network;

import com.mojang.blaze3d.platform.InputConstants;
import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.P11ClientTransitions;
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
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
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
    private long transitionSuppressionGeneration;
    private final OutgoingSequence outgoingSequence = new OutgoingSequence();
    private final P9FreshPressState freshPresses = new P9FreshPressState();
    private InputConstants.Key observedKey = castKey.getKey();
    private KeyModifier observedModifier = castKey.getKeyModifier();
    private IKeyConflictContext observedConflictContext = castKey.getKeyConflictContext();

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

    @SubscribeEvent
    static void onKeyInput(InputEvent.Key event) {
        OWNER.recordPhysicalAction(event.getAction(), OWNER.castKey.matches(event.getKey(), event.getScanCode()));
    }

    @SubscribeEvent
    static void onMouseInput(InputEvent.MouseButton.Post event) {
        OWNER.recordPhysicalAction(event.getAction(), OWNER.castKey.matchesMouse(event.getButton()));
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
        markPendingFlush();
    }

    private void handleConnectionClosed() {
        requireClientThread();
        senderSessionAvailable = false;
        outgoingSequence.reset();
        markPendingFlush();
    }

    private void markContextTransition() {
        requireClientThread();
        markPendingFlush();
    }

    private void handleScreenOpening(ScreenEvent.Opening event) {
        Objects.requireNonNull(event, "event");
        requireClientThread();
        if (!event.isCanceled() && event.getNewScreen() != null) {
            markPendingFlush();
        }
    }

    private void handlePauseChanged(ClientPauseChangeEvent.Post event) {
        Objects.requireNonNull(event, "event");
        requireClientThread();
        if (event.isPaused()) {
            markPendingFlush();
        }
    }

    private void handleRenderFrame() {
        requireClientThread();
        if (!Minecraft.getInstance().isWindowActive()) {
            markPendingFlush();
        }
    }

    private void markPendingFlush() {
        pendingFlushRequired = true;
        freshPresses.invalidate();
    }

    private void observeBinding() {
        if (!observedKey.equals(castKey.getKey()) || observedModifier != castKey.getKeyModifier()
                || observedConflictContext != castKey.getKeyConflictContext()) {
            observedKey = castKey.getKey();
            observedModifier = castKey.getKeyModifier();
            observedConflictContext = castKey.getKeyConflictContext();
            markPendingFlush();
        }
    }

    private void recordPhysicalAction(int action, boolean mapped) {
        if (!keyMappingRegistered) { return; }
        requireClientThread();
        observeBinding();
        if (!mapped) { return; }
        boolean eligible = !pendingFlushRequired && castKey.isConflictContextAndModifierActive()
                && evaluateGates(liveGateProbe(Minecraft.getInstance(), true)) == GateDecision.ALLOW;
        freshPresses.action(action, eligible);
        if (!eligible && action != GLFW.GLFW_RELEASE) { markPendingFlush(); }
    }

    private void handleClientPostTick() {
        if (!keyMappingRegistered) { return; }
        requireClientThread();
        observeBinding();
        var minecraft = Minecraft.getInstance();
        long observedTransition = P11ClientTransitions.inputSuppressionGeneration();
        if (transitionSuppressionGeneration != observedTransition) {
            transitionSuppressionGeneration = observedTransition;
            markPendingFlush();
        }
        var gate = evaluateGates(liveGateProbe(minecraft, !pendingFlushRequired));
        // One bounded drain in this Post, before any send. Callback events never drain or send.
        var drained = drainPendingClicks();
        if (gate != GateDecision.ALLOW && gate != GateDecision.PENDING_FLUSH_REQUIRED) {
            markPendingFlush();
            return;
        }
        if (gate == GateDecision.PENDING_FLUSH_REQUIRED) {
            freshPresses.invalidate();
            if (drained.empty() && evaluateGates(liveGateProbe(minecraft, true)) == GateDecision.ALLOW) {
                pendingFlushRequired = false;
            }
            return;
        }
        int attempts = freshPresses.takeEligible(drained.clicks(), drained.empty());
        if (attempts < 0) { markPendingFlush(); return; }
        for (int index = 0; index < attempts; index++) {
            observeBinding();
            if (evaluateGates(liveGateProbe(minecraft, !pendingFlushRequired)) != GateDecision.ALLOW) {
                markPendingFlush();
                return;
            }
            outgoingSequence.sendNext(P9ClientCastInput::sendPayload);
        }
    }

    private P9FreshPressState.Drain drainPendingClicks() {
        return P9FreshPressState.drain(castKey::consumeClick);
    }

    private GateProbe liveGateProbe(Minecraft minecraft, boolean noPendingFlush) {
        return new LiveGateProbe(
                minecraft,
                senderSessionAvailable,
                transitionSuppressionGeneration,
                noPendingFlush);
    }

    private static void sendPayload(CastCurrentSpellPayload payload) {
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
        if (!probe.noTransitionPending()) {
            return GateDecision.TRANSITION_PENDING;
        }
        return probe.noPendingFlush()
                ? GateDecision.ALLOW
                : GateDecision.PENDING_FLUSH_REQUIRED;
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

        boolean noTransitionPending();

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
        TRANSITION_PENDING,
        PENDING_FLUSH_REQUIRED,
        ALLOW
    }

    @FunctionalInterface
    interface PayloadSender {
        void send(CastCurrentSpellPayload payload);
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
            var payload = new CastCurrentSpellPayload(sequence);
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
            long transitionSuppressionGeneration,
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

        @Override
        public boolean noTransitionPending() {
            return !P11ClientTransitions.blocksCast()
                    && transitionSuppressionGeneration
                            == P11ClientTransitions.inputSuppressionGeneration();
        }
    }
}
