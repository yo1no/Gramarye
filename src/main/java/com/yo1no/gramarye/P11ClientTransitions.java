package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;
import org.lwjgl.glfw.GLFW;

/** Client-only closed native bridges. This class never supplies server execution authority. */
public final class P11ClientTransitions {
    private static P11ClientTransitionState state = new P11ClientTransitionState();
    private static Connection connection;
    private static Bundle bundle;
    private static Member member;
    private static int statusDelay;
    private static boolean installed;
    private static int activationKey = -1;
    private static boolean freshPress;
    private static long inputSuppressionGeneration;
    private static boolean inputGenerationExhausted;
    private static int nativeScreenDepth;
    private static boolean screenReconcileDeferred;

    private P11ClientTransitions() { }

    static void install() {
        if (installed) { throw new IllegalStateException("P11_CLIENT_ALREADY_INSTALLED"); }
        installed = true;
        P11ClientTransitionDispatch.install(P11ClientTransitions::handle);
        NeoForge.EVENT_BUS.addListener(P11ClientTransitions::tick);
    }

    private static void handle(State update, Connection exact, ICommonPacketListener listener) {
        requireThread();
        if (!exact.isConnected() || exact.getPacketListener() != listener
                || listener.getConnection() != exact) { return; }
        if (connection != exact) {
            if (connection != null && connection.isConnected()) { return; }
            connection = exact;
            state = new P11ClientTransitionState();
            statusDelay = 20;
        }
        boolean wasBlocked = state.blocksCast();
        if (state.accept(update)) {
            if (!wasBlocked && state.blocksCast()) { suppressInput(); }
            reconcileScreen();
        }
    }

    public static boolean replaceRespawn(LocalPlayer player) {
        requireThread();
        if (!negotiated(player.connection)) { return false; }
        requireScene(player.connection);
        if (Minecraft.getInstance().player != player) { return true; }
        Request request = state.firstTry(Kind.DEATH);
        if (request != null) {
            send(request);
            KeyMapping.resetToggleKeys();
        }
        reconcileScreen();
        return true;
    }

    public static boolean replaceEndRespawn(ClientPacketListener listener) {
        requireThread();
        if (!negotiated(listener)) { return false; }
        requireScene(listener);
        if (state.untriggeredSuccessor(Kind.END)
                && !preserveSuccessorScreen(Minecraft.getInstance().screen)) { return true; }
        Request request = state.firstTry(Kind.END);
        if (request != null) { send(request); }
        reconcileScreen();
        return true;
    }

    private static void requireScene(ClientPacketListener listener) {
        if (listener.getConnection() != connection
                || connection.getPacketListener() != listener || state.current() == null) {
            throw new IllegalStateException("P11_NATIVE_SENDER_WITHOUT_SCENE");
        }
    }

    static void retry() {
        requireThread();
        if (!transportMatches()) { return; }
        Request request = state.retry();
        if (request != null) { send(request); }
        reconcileScreen();
    }

    static State view() { return state.current(); }
    static boolean retryArmed() { return state.retryArmed() && transportMatches(); }

    static void observeNeutral() {
        var minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        if (!InputConstants.isKeyDown(window, GLFW.GLFW_KEY_ENTER)
                && !InputConstants.isKeyDown(window, GLFW.GLFW_KEY_KP_ENTER)
                && !InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE)
                && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
            state.activationNeutral();
        }
    }

    /** Raw action is observed before Screen folds PRESS and REPEAT into keyPressed. */
    public static void keyboardAction(long window, int key, int action) {
        var minecraft = Minecraft.getInstance();
        if (window != minecraft.getWindow().getWindow() || !protectScreen(minecraft.screen)) {
            activationKey = -1;
            freshPress = false;
            return;
        }
        activationKey = key;
        freshPress = action == GLFW.GLFW_PRESS;
        if (action == GLFW.GLFW_RELEASE) { observeNeutral(); }
    }

    static boolean freshActivation(int key) {
        boolean result = freshPress && activationKey == key;
        freshPress = false;
        return result;
    }

    public static boolean blocksCast() {
        return state.blocksCast() || inputGenerationExhausted;
    }

    public static long inputSuppressionGeneration() { return inputSuppressionGeneration; }

    private static void suppressInput() {
        if (inputSuppressionGeneration == Long.MAX_VALUE) {
            inputGenerationExhausted = true;
        } else {
            inputSuppressionGeneration++;
        }
    }

    static void leaveTransport() {
        requireThread();
        if (connection != null && connection.isConnected()) {
            connection.disconnect(Component.translatable("multiplayer.status.quitting"));
        }
    }

    public static boolean protectScreen(Screen screen) {
        return state.pending() && (screen instanceof P11ClientTransitionScreen
                || screen instanceof P11ClientLeaveScreen);
    }

    /** Call-stack only: defer P11 UI replacement until the native outer screen change returns. */
    public static boolean beginScreenChange() {
        if (!installed || !Minecraft.getInstance().isSameThread()) { return false; }
        if (nativeScreenDepth == Integer.MAX_VALUE) {
            throw new IllegalStateException("P11_CLIENT_SCREEN_DEPTH_EXHAUSTED");
        }
        nativeScreenDepth++;
        return true;
    }

    public static void endScreenChange(boolean tracked, boolean normal) {
        if (!tracked) { return; }
        nativeScreenDepth--;
        if (nativeScreenDepth != 0) { return; }
        boolean deferred = screenReconcileDeferred;
        screenReconcileDeferred = false;
        // Never perform UI work while the original native exception is unwinding.
        if (!normal) { return; }
        if (deferred) { reconcileScreen(); }
        else { screenChanged(); }
    }

    public static void screenChanged() {
        Screen screen = Minecraft.getInstance().screen;
        if (Minecraft.getInstance().isSameThread() && state.pending()
                && (screen == null || state.successor() == null
                        && (screen instanceof DeathScreen
                                || screen instanceof DeathScreen.TitleConfirmScreen))) {
            reconcileScreen();
        }
    }

    /** Tags only original native scene-screen construction; the controller retains no Screen. */
    public static void nativeSceneScreen(ClientPacketListener listener, Screen screen) {
        requireThread();
        State successor = state.successor();
        if (successor == null || listener.getConnection() != connection
                || connection.getPacketListener() != listener
                || !(screen instanceof SceneScreenAccess access)
                || !nativeScreenKind(screen, successor.kind())) { return; }
        access.p11$bindNativeScene(successor.connectionEpoch(), successor.sceneSerial());
    }

    private static boolean preserveSuccessorScreen(Screen screen) {
        State successor = state.successor();
        return successor != null && state.untriggeredSuccessor(successor.kind())
                && nativeScreenKind(screen, successor.kind())
                && screen instanceof SceneScreenAccess access
                && access.p11$matchesNativeScene(successor.connectionEpoch(), successor.sceneSerial());
    }

    private static boolean nativeScreenKind(Screen screen, Kind kind) {
        return kind == Kind.END && screen instanceof WinScreen
                || kind == Kind.DEATH && screen instanceof DeathScreen;
    }

    private static void reconcileScreen() {
        if (nativeScreenDepth > 0) {
            screenReconcileDeferred = true;
            return;
        }
        var minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        State current = state.current();
        if (preserveSuccessorScreen(screen)) { return; }
        // Normal configuration still owns pack prompts/loading. A real refusal or
        // unknown result, not the mere existence of server request0, presents our UI.
        if (current != null && current.scope() != Scope.PLAY && current.requestSeq() == 0
                && current.outcome() == Outcome.PENDING
                && !(screen instanceof P11ClientTransitionScreen)
                && !(screen instanceof P11ClientLeaveScreen)) { return; }
        if (state.settled() || !state.pending() && state.current() != null
                && state.current().outcome() == Outcome.BINDING) {
            if (screen instanceof P11ClientTransitionScreen || screen instanceof P11ClientLeaveScreen) {
                minecraft.setScreen(null);
            }
        } else if (state.pending() && !(screen instanceof ReceivingLevelScreen)
                && !(screen instanceof P11ClientTransitionScreen)
                && !(screen instanceof P11ClientLeaveScreen)) {
            minecraft.setScreen(new P11ClientTransitionScreen());
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        Connection observed = connection;
        if (observed == null) { return; }
        boolean ownsTransport = ownsActorlessTransportTick(Minecraft.getInstance(), observed);
        if (ownsTransport && observed.isConnected()) {
            // ConnectScreen/ServerReconfigScreen normally own this native call.
            // Our actorless status UI replaced them, not the transport listener.
            observed.tick();
        }
        if (connection != observed) { return; }
        if (!observed.isConnected()) {
            if (ownsTransport) { observed.handleDisconnection(); }
            if (connection == observed) { disconnected(); }
            return;
        }
        if (state.retryAvailable()) { observeNeutral(); }
        if (state.pending() && --statusDelay <= 0) {
            statusDelay = 20;
            Request request = state.status();
            if (request != null) { send(request); }
        }
    }

    private static boolean ownsActorlessTransportTick(Minecraft minecraft, Connection exact) {
        if (minecraft.level != null
                || !(minecraft.screen instanceof P11ClientTransitionScreen
                        || minecraft.screen instanceof P11ClientLeaveScreen)
                || exact != connection
                || !(exact.getPacketListener() instanceof ICommonPacketListener listener)
                || listener.getConnection() != exact) { return false; }
        if (!(minecraft instanceof TransportTickAccess access)) {
            throw new IllegalStateException("P11_CLIENT_PENDING_CONNECTION_HOOK_MISSING");
        }
        // Initial integrated login already ticks this field in Minecraft.tick.
        // Native Connect/Reconfig/loading screens and actual PLAY keep their own owners.
        if (access.p11$pendingConnection() == exact) { return false; }
        return listener.protocol() == ConnectionProtocol.CONFIGURATION
                || listener instanceof ClientPacketListener && listener instanceof PlayAccess play
                        && !play.p11$actualLoginAllowed();
    }

    private static void send(Request request) {
        if (connection == null || !connection.isConnected()
                || !(connection.getPacketListener() instanceof ICommonPacketListener listener)) { return; }
        ConnectionProtocol expected = request.scope() == Scope.CONFIG
                ? ConnectionProtocol.CONFIGURATION : ConnectionProtocol.PLAY;
        if (listener.protocol() != expected) { return; }
        listener.send(new ServerboundCustomPayloadPacket(new P11TransitionRequestPayload(request)));
    }

    private static boolean transportMatches() {
        State current = state.current();
        if (current == null || connection == null || !connection.isConnected()
                || !(connection.getPacketListener() instanceof ICommonPacketListener listener)) {
            return false;
        }
        return listener.protocol() == (current.scope() == Scope.CONFIG
                ? ConnectionProtocol.CONFIGURATION : ConnectionProtocol.PLAY);
    }

    public static void disconnected() {
        requireThread();
        connection = null;
        state = new P11ClientTransitionState();
        statusDelay = 20;
        activationKey = -1;
        freshPress = false;
    }

    public static boolean allowActorFunctions(ClientPacketListener listener) {
        return listener.getConnection().getPacketListener() == listener
                && (!negotiated(listener) || listener instanceof PlayAccess access
                        && access.p11$actualLoginAllowed());
    }

    private static boolean negotiated(ClientPacketListener listener) {
        return installed && listener.hasChannel(P11TransitionRequestPayload.TYPE);
    }

    public static Bundle beginBundle(ClientPacketListener listener) {
        if (!Minecraft.getInstance().isSameThread()) { return null; }
        Bundle result = new Bundle(bundle, listener);
        bundle = result;
        return result;
    }

    public static void endBundle(Bundle expected) {
        if (expected != null && bundle == expected) { bundle = expected.previous; }
    }

    public static Member beginMember(ClientPacketListener listener, Packet<?> packet) {
        if (bundle == null || bundle.listener != listener) { return null; }
        State marker = bundle.marker;
        bundle.marker = null;
        Member result = new Member(member, bundle, packet,
                marker != null && matchesFrame(marker, packet) ? marker : null);
        member = result;
        return result;
    }

    public static void endMember(Member expected, boolean normal) {
        if (expected == null || member != expected) { return; }
        if (normal && expected.packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof P11TransitionStatePayload payload
                && payload.state() == state.current()
                && payload.state().outcome() == Outcome.NATIVE_FRAME) {
            expected.owner.marker = payload.state();
        }
        member = expected.previous;
    }

    private static boolean matchesFrame(State marker, Packet<?> packet) {
        boolean login = packet instanceof ClientboundLoginPacket;
        return (login || packet instanceof ClientboundRespawnPacket)
                && state.expectFrame(marker.sceneSerial(), marker.requestSeq(),
                        marker.targetActorGeneration(), login);
    }

    public static void nativeFrameApplied(ClientPacketListener listener, Packet<?> packet) {
        if (member == null || member.owner.listener != listener || member.packet != packet
                || member.marker == null || !member.nativeMaterial
                || listener.getConnection() != connection
                || connection.getPacketListener() != listener) { return; }
        State marker = member.marker;
        state.frameApplied(marker.sceneSerial(), marker.requestSeq(),
                marker.targetActorGeneration(), packet instanceof ClientboundLoginPacket);
        reconcileScreen();
    }

    public static void nativeFrameMaterial(ClientPacketListener listener, Packet<?> packet) {
        if (member != null && member.owner.listener == listener && member.packet == packet
                && member.marker != null) { member.nativeMaterial = true; }
    }

    public static void configurationStarted(ClientPacketListener listener) {
        if (listener.getConnection() == connection) { state.configurationStarted(); }
    }

    /** Read-only constructor guard; neither negotiates channels nor installs a listener. */
    public static boolean canInheritConfigurationNegotiation(Minecraft minecraft, Connection exact,
            CommonListenerCookie cookie) {
        if (!installed || minecraft == null || minecraft != Minecraft.getInstance() || !minecraft.isSameThread()
                || exact == null || exact != connection || !exact.isConnected() || cookie == null
                || !(exact.getPacketListener() instanceof ClientPacketListener previous)
                || previous.getConnection() != exact || !state.pending()) { return false; }
        // Pinned NeoForge 21.1.241 read-only access: hasChannel also accepts ad-hoc
        // registrations, which cannot establish completed required-channel negotiation.
        return configurationNegotiationMatches(state.current(), state.boundActor(),
                cookie.connectionType(), previous.getConnectionType(),
                ChannelAttributes.getConnectionType(exact), ChannelAttributes.getPayloadSetup(exact));
    }

    static boolean configurationNegotiationMatches(State current, long boundActor,
            ConnectionType cookieType, ConnectionType previousType, ConnectionType negotiatedType,
            NetworkPayloadSetup setup) {
        if (current == null || current.scope() != Scope.PLAY || current.kind() != Kind.ENTER_CONFIG
                || current.outcome() != Outcome.RUNNING || boundActor <= 0
                || current.actorGeneration() != boundActor || cookieType != ConnectionType.NEOFORGE
                || previousType != cookieType || negotiatedType != cookieType || setup == null) { return false; }
        for (var protocol : new ConnectionProtocol[]{ConnectionProtocol.CONFIGURATION, ConnectionProtocol.PLAY}) {
            for (var id : new net.minecraft.resources.ResourceLocation[]{
                    P11TransitionRequestPayload.TYPE.id(), P11TransitionStatePayload.TYPE.id()}) {
                var channel = setup.getChannel(protocol, id);
                if (channel == null || !id.equals(channel.id())
                        || !P11TransitionWire.REGISTRAR_VERSION.equals(channel.chosenVersion())) { return false; }
            }
        }
        return true;
    }

    private static void requireThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("P11_CLIENT_OFF_THREAD");
        }
    }

    public interface PlayAccess {
        boolean p11$actualLoginAllowed();
    }

    /** Read-only native tick-owner observation; no setter or alternate connection owner. */
    public interface TransportTickAccess {
        Connection p11$pendingConnection();
    }

    /** A native screen's two scalar presentation labels, not an execution capability. */
    public interface SceneScreenAccess {
        void p11$bindNativeScene(long connectionEpoch, long sceneSerial);
        boolean p11$matchesNativeScene(long connectionEpoch, long sceneSerial);
    }

    /** Native call-stack-only scopes; no public construction or retained packet history. */
    public static final class Bundle {
        private final Bundle previous;
        private final ClientPacketListener listener;
        private State marker;
        private Bundle(Bundle previous, ClientPacketListener listener) {
            this.previous = previous;
            this.listener = listener;
        }
    }

    public static final class Member {
        private final Member previous;
        private final Bundle owner;
        private final Packet<?> packet;
        private final State marker;
        private boolean nativeMaterial;
        private Member(Member previous, Bundle owner, Packet<?> packet, State marker) {
            this.previous = previous;
            this.owner = owner;
            this.packet = packet;
            this.marker = marker;
        }
    }
}
