package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class P11ClientTransitionStateTest {
    @Test
    void nativeFirstActionLatchesBeforeTransportAndCannotDuplicate() {
        var client = death();
        var first = client.firstTry(Kind.DEATH);
        assertEquals(1, first.requestSeq());
        assertTrue(client.pending());
        assertTrue(client.blocksCast());
        assertNull(client.firstTry(Kind.DEATH));
        assertNull(client.firstTry(Kind.END));
        assertEquals(first.requestSeq(), client.status().requestSeq());
    }

    @Test
    void refusalNeedsMatchingMayTryThenNeutralAndFreshOperation() {
        var client = death();
        client.firstTry(Kind.DEATH);
        assertTrue(client.accept(play(1, 1, 2, Kind.DEATH, Outcome.NOT_STARTED,
                Availability.WAIT_NOTIFY, Reason.ACTIVE_CONTEXT, 0)));
        client.activationNeutral();
        assertFalse(client.retryArmed());
        assertNull(client.retry());
        assertTrue(client.accept(play(1, 1, 3, Kind.DEATH, Outcome.NOT_STARTED,
                Availability.MAY_TRY, Reason.NONE, 0)));
        assertFalse(client.retryArmed());
        assertNull(client.retry());
        client.activationNeutral();
        assertEquals(2, client.retry().requestSeq());
        assertNull(client.retry());
        assertFalse(client.accept(play(1, 1, 4, Kind.DEATH, Outcome.NOT_STARTED,
                Availability.MAY_TRY, Reason.NONE, 0)));
    }

    @Test
    void frameAloneNeverSettlesOrReleasesCastGate() {
        var client = death();
        client.firstTry(Kind.DEATH);
        assertTrue(client.accept(play(1, 1, 2, Kind.DEATH, Outcome.NATIVE_FRAME,
                Availability.DISABLED, Reason.NONE, 11)));
        client.frameApplied(1, 1, 11, false);
        assertFalse(client.settled());
        assertTrue(client.pending());
        assertTrue(client.blocksCast());
        assertTrue(client.accept(play(1, 1, 3, Kind.DEATH, Outcome.COMPLETED,
                Availability.DISABLED, Reason.NONE, 11)));
        assertTrue(client.settled());
        assertFalse(client.blocksCast());
        assertEquals(11, client.boundActor());
    }

    @Test
    void terminalBeforeClientFrameStillWaitsForExactAppliedPacket() {
        var client = death();
        client.firstTry(Kind.DEATH);
        client.accept(play(1, 1, 2, Kind.DEATH, Outcome.NATIVE_FRAME,
                Availability.DISABLED, Reason.NONE, 11));
        client.accept(play(1, 1, 3, Kind.DEATH, Outcome.COMPLETED,
                Availability.DISABLED, Reason.NONE, 11));
        client.frameApplied(1, 1, 12, false);
        client.frameApplied(2, 1, 11, false);
        client.frameApplied(1, 2, 11, false);
        client.frameApplied(1, 1, 11, true);
        assertFalse(client.settled());
        client.frameApplied(1, 1, 11, false);
        assertTrue(client.settled());
        assertFalse(client.accept(play(1, 1, 4, Kind.DEATH, Outcome.NOT_STARTED,
                Availability.MAY_TRY, Reason.NONE, 0)));
    }

    @Test
    void unknownDoesNotBecomeFreshFromTimeoutOrLaterRefusal() {
        var client = death();
        client.firstTry(Kind.DEATH);
        client.accept(play(1, 1, 2, Kind.DEATH, Outcome.UNKNOWN,
                Availability.DISABLED, Reason.STATUS_UNAVAILABLE, 0));
        for (int i = 0; i < 20; i++) {
            client.activationNeutral();
            assertNotNull(client.status());
            assertNull(client.retry());
            assertNull(client.firstTry(Kind.DEATH));
        }
        assertFalse(client.accept(play(1, 1, 3, Kind.DEATH, Outcome.NOT_STARTED,
                Availability.MAY_TRY, Reason.NONE, 0)));
        assertTrue(client.blocksCast());
    }

    @Test
    void successorFirstNativeTriggerRetainsOneIntentButCannotSettleOldRequest() {
        var client = death();
        client.firstTry(Kind.DEATH);
        client.accept(play(1, 1, 2, Kind.DEATH, Outcome.NATIVE_FRAME,
                Availability.DISABLED, Reason.NONE, 11));
        var successor = new State(Scope.PLAY, 7, 2, 11, 0, 4, Kind.END,
                Outcome.BINDING, Availability.DISABLED, Reason.NONE, 0);
        assertTrue(client.accept(successor));
        assertSame(successor, client.successor());
        assertNull(client.firstTry(Kind.END));
        client.frameApplied(1, 1, 11, false);
        assertEquals(1, client.current().sceneSerial());
        var early = client.firstTry(Kind.END);
        assertEquals(2, early.requestSeq());
        assertEquals(2, early.sceneSerial());
        assertEquals(11, early.actorGeneration());
        assertEquals(2, client.successorRequest());
        assertNull(client.firstTry(Kind.END));
        assertEquals(1, client.status().sceneSerial());
        assertEquals(1, client.status().requestSeq());
        client.accept(play(1, 1, 5, Kind.DEATH, Outcome.COMPLETED,
                Availability.DISABLED, Reason.NONE, 11));
        assertSame(successor, client.current());
        assertNull(client.successor());
        assertEquals(2, client.request());
        assertTrue(client.pending());
        assertNull(client.firstTry(Kind.END));
        // The old attempt can make the early TRY busy/drop. Its activated BINDING
        // cannot erase local n; STATUS proves NOT_STARTED before any manual retry.
        assertFalse(client.accept(new State(Scope.PLAY, 7, 2, 11, 0, 6, Kind.END,
                Outcome.BINDING, Availability.DISABLED, Reason.NONE, 0)));
        assertEquals(2, client.status().requestSeq());
        assertTrue(client.accept(new State(Scope.PLAY, 7, 2, 11, 2, 7, Kind.END,
                Outcome.NOT_STARTED, Availability.WAIT_NOTIFY, Reason.CONTROL_DISPATCH_BUSY, 0)));
        assertNull(client.retry());
        assertTrue(client.accept(new State(Scope.PLAY, 7, 2, 11, 2, 8, Kind.END,
                Outcome.NOT_STARTED, Availability.MAY_TRY, Reason.NONE, 0)));
        assertNull(client.retry());
        client.activationNeutral();
        assertEquals(3, client.retry().requestSeq());
    }

    @Test
    void successorActivationNeverManufacturesNativeTriggerOrChangesReservedScene() {
        var client = death();
        client.firstTry(Kind.DEATH);
        client.accept(play(1, 1, 2, Kind.DEATH, Outcome.NATIVE_FRAME,
                Availability.DISABLED, Reason.NONE, 11));
        var successor = new State(Scope.PLAY, 7, 2, 11, 0, 3, Kind.END,
                Outcome.BINDING, Availability.DISABLED, Reason.NONE, 0);
        client.accept(successor);
        assertTrue(client.untriggeredSuccessor(Kind.END));
        assertFalse(client.accept(new State(Scope.PLAY, 7, 3, 11, 0, 4, Kind.END,
                Outcome.BINDING, Availability.DISABLED, Reason.NONE, 0)));
        assertFalse(client.accept(new State(Scope.PLAY, 7, 2, 11, 2, 4, Kind.END,
                Outcome.RUNNING, Availability.DISABLED, Reason.NONE, 0)));
        client.frameApplied(1, 1, 11, false);
        client.accept(play(1, 1, 5, Kind.DEATH, Outcome.COMPLETED,
                Availability.DISABLED, Reason.NONE, 11));
        assertSame(successor, client.current());
        assertEquals(0, client.request());
        assertFalse(client.pending());
        assertNull(client.status());
        assertTrue(client.accept(new State(Scope.PLAY, 7, 2, 11, 0, 6, Kind.END,
                Outcome.BINDING, Availability.DISABLED, Reason.NONE, 0)));
        assertFalse(client.pending());
        assertNull(client.status());
        assertEquals(2, client.firstTry(Kind.END).requestSeq());
    }

    @Test
    void faultDiscardsAlreadySubmittedSuccessorLatchWithoutReplayingIt() {
        var client = death();
        client.firstTry(Kind.DEATH);
        client.accept(play(1, 1, 2, Kind.DEATH, Outcome.NATIVE_FRAME,
                Availability.DISABLED, Reason.NONE, 11));
        client.frameApplied(1, 1, 11, false);
        client.accept(new State(Scope.PLAY, 7, 2, 11, 0, 3, Kind.END,
                Outcome.BINDING, Availability.DISABLED, Reason.NONE, 0));
        assertNotNull(client.firstTry(Kind.END));
        client.accept(play(1, 1, 4, Kind.DEATH, Outcome.FAULT,
                Availability.DISABLED, Reason.NATIVE_FAILURE, 0));
        assertNull(client.successor());
        assertEquals(0, client.successorRequest());
        assertNull(client.firstTry(Kind.END));
        assertEquals(1, client.status().sceneSerial());
    }

    @Test
    void faultDropsSuccessorWithoutAutoExecutingIt() {
        var client = death();
        client.firstTry(Kind.DEATH);
        client.accept(play(1, 1, 2, Kind.DEATH, Outcome.NATIVE_FRAME,
                Availability.DISABLED, Reason.NONE, 11));
        client.accept(new State(Scope.PLAY, 7, 2, 11, 0, 3, Kind.DEATH,
                Outcome.BINDING, Availability.DISABLED, Reason.NONE, 0));
        client.accept(play(1, 1, 4, Kind.DEATH, Outcome.FAULT,
                Availability.DISABLED, Reason.NATIVE_FAILURE, 0));
        assertNull(client.successor());
        assertTrue(client.blocksCast());
        assertNull(client.firstTry(Kind.DEATH));
    }

    @Test
    void joinKeepsActorlessReceiptAcrossConfigurationToPreplay() {
        var client = new P11ClientTransitionState();
        assertTrue(client.accept(new State(Scope.CONFIG, 7, 1, 0, 0, 1, Kind.JOIN,
                Outcome.PENDING, Availability.DISABLED, Reason.NONE, 0)));
        assertTrue(client.pending());
        assertEquals(0, client.status().requestSeq());
        assertTrue(client.accept(new State(Scope.PREPLAY, 7, 1, 0, 0, 2, Kind.JOIN,
                Outcome.NATIVE_FRAME, Availability.DISABLED, Reason.NONE, 10)));
        assertFalse(client.accept(new State(Scope.CONFIG, 7, 1, 0, 0, 3, Kind.JOIN,
                Outcome.RUNNING, Availability.DISABLED, Reason.NONE, 0)));
        client.frameApplied(1, 0, 10, true);
        assertTrue(client.blocksCast());
        assertTrue(client.accept(new State(Scope.PREPLAY, 7, 1, 0, 0, 4, Kind.JOIN,
                Outcome.COMPLETED, Availability.DISABLED, Reason.NONE, 10)));
        assertTrue(client.settled());
    }

    @Test
    void enterConfigurationUsesStartAndConfigTerminalNotActorFrame() {
        var client = new P11ClientTransitionState();
        client.accept(new State(Scope.PLAY, 7, 1, 10, 0, 1, Kind.ENTER_CONFIG,
                Outcome.PENDING, Availability.DISABLED, Reason.NONE, 0));
        var terminal = new State(Scope.CONFIG, 7, 1, 0, 0, 2, Kind.ENTER_CONFIG,
                Outcome.COMPLETED, Availability.DISABLED, Reason.NONE, 0);
        assertFalse(client.accept(terminal));
        client.configurationStarted();
        assertTrue(client.accept(terminal));
        assertTrue(client.settled());
        assertEquals(0, client.boundActor());
        assertTrue(client.accept(new State(Scope.CONFIG, 7, 2, 0, 0, 3, Kind.RETURN_TO_WORLD,
                Outcome.PENDING, Availability.DISABLED, Reason.NONE, 0)));
        assertTrue(client.pending());
    }

    @Test
    void staleConnectionSceneVersionAndForeignActorCannotReplaceCurrent() {
        var client = death();
        assertFalse(client.accept(new State(Scope.PLAY, 8, 1, 10, 0, 2, Kind.DEATH,
                Outcome.BINDING, Availability.DISABLED, Reason.NONE, 0)));
        assertFalse(client.accept(play(1, 0, 1, Kind.DEATH, Outcome.BINDING,
                Availability.DISABLED, Reason.NONE, 0)));
        assertFalse(client.accept(new State(Scope.PLAY, 7, 2, 99, 0, 2, Kind.DEATH,
                Outcome.BINDING, Availability.DISABLED, Reason.NONE, 0)));
        assertEquals(1, client.current().sceneSerial());
        assertNull(client.successor());
    }

    @Test
    void maximumSequenceIsSentOnceAndNeverWrapsOrResetsAtSceneChange() {
        var client = new P11ClientTransitionState(Long.MAX_VALUE - 1);
        client.accept(play(1, 0, 1, Kind.DEATH, Outcome.BINDING,
                Availability.DISABLED, Reason.NONE, 0));
        assertEquals(Long.MAX_VALUE, client.firstTry(Kind.DEATH).requestSeq());
        client.accept(play(1, Long.MAX_VALUE, 2, Kind.DEATH, Outcome.NOT_STARTED,
                Availability.MAY_TRY, Reason.NONE, 0));
        client.activationNeutral();
        assertNull(client.retry());
        assertFalse(client.retryArmed());
        assertEquals(Long.MAX_VALUE, client.status().requestSeq());
        assertThrows(IllegalArgumentException.class, () -> new P11ClientTransitionState(-1));
    }

    @Test
    void nativeHooksPreserveUniqueFrameChatKeepaliveAndSenderAnchors() throws Exception {
        var listener = source("mixin/P11ClientPacketListenerMixin.java");
        assertTrue(listener.contains("lambda$handleGameEvent$7()V"));
        assertTrue(listener.contains("Packet;handle(Lnet/minecraft/network/PacketListener;)V"));
        assertTrue(listener.contains("P11ClientTransitions.endMember(scope, normal)"));
        assertTrue(listener.contains("P11ClientTransitions.endBundle(scope)"));
        assertTrue(listener.contains("Connection;isEncrypted()Z"));
        assertTrue(listener.contains("setKeyPair(Lnet/minecraft/world/entity/player/ProfileKeyPair;)V"));
        assertTrue(listener.contains("(ZLjava/lang/Runnable;)Lnet/minecraft/client/gui/screens/WinScreen;"));
        assertTrue(listener.contains("(Lnet/minecraft/network/chat/Component;Z)Lnet/minecraft/client/gui/screens/DeathScreen;"));
        assertFalse(listener.contains("sendDeferredPackets"));
        assertFalse(listener.contains("prepareKeyPair()"));
        var controller = source("P11ClientTransitions.java");
        assertTrue(controller.contains("connection.getPacketListener() instanceof ICommonPacketListener"));
        assertTrue(controller.contains("expected.packet instanceof ClientboundCustomPayloadPacket"));
        assertTrue(controller.contains("member.packet != packet"));
        assertTrue(controller.contains("payload.state() == state.current()"));
        assertTrue(controller.contains("freshPress = action == GLFW.GLFW_PRESS"));
        assertFalse(controller.contains("GLFW.GLFW_REPEAT"));
        assertFalse(controller.contains("getConnection().send("));
        assertFalse(controller.contains("new ServerboundClientCommandPacket"));
        assertFalse(controller.contains("ThreadLocal"));
        assertTrue(controller.contains("Minecraft.getInstance().player != player"));
        assertTrue(controller.contains("access.p11$matchesNativeScene(successor.connectionEpoch(), successor.sceneSerial())"));
        var labels = source("mixin/P11ClientSceneScreenMixin.java");
        assertTrue(labels.contains("@Mixin({DeathScreen.class, WinScreen.class})"));
        assertTrue(labels.contains("@Unique private long p11$connectionEpoch;"));
        assertTrue(labels.contains("@Unique private long p11$sceneSerial;"));
        assertFalse(labels.contains("Serverbound"));
        assertFalse(labels.contains("private Screen"));
    }

    @Test
    void screensDoNotPauseOrOwnRequestAndInputKeepsRecoveryFlush() throws Exception {
        for (var name : new String[]{"P11ClientTransitionScreen.java", "P11ClientLeaveScreen.java"}) {
            var screen = source(name);
            assertTrue(screen.contains("boolean isPauseScreen() { return false; }"));
            assertFalse(screen.contains("extends ConfirmScreen"));
            assertFalse(screen.contains("private long"));
            assertFalse(screen.contains("PacketDistributor"));
        }
        var input = source("magic/network/P9ClientCastInput.java");
        assertTrue(input.contains("transitionSuppressionGeneration != observedTransition"));
        assertTrue(input.contains("P11ClientTransitions.blocksCast()"));
        assertTrue(input.contains("GateDecision.TRANSITION_PENDING"));
        assertTrue(input.contains("dropAllPendingClicks();"));
        assertFalse(input.contains("KeyMapping.releaseAll"));
    }

    @Test
    void actorlessOwnScreensPreserveNativeTransportWithoutDoubleTickingIntegratedPendingConnection() throws Exception {
        var controller = source("P11ClientTransitions.java");
        var tick = controller.substring(controller.indexOf("private static void tick("),
                controller.indexOf("private static void send("));
        assertActorlessTickOwnership(tick);
        assertThrows(AssertionError.class, () -> assertActorlessTickOwnership(
                tick.replace("access.p11$pendingConnection() == exact", "access.p11$pendingConnection() != exact")));
        assertThrows(AssertionError.class, () -> assertActorlessTickOwnership(
                tick.replace("!play.p11$actualLoginAllowed()", "play.p11$actualLoginAllowed()")));
        assertThrows(AssertionError.class, () -> assertActorlessTickOwnership(
                tick.replace("observed.handleDisconnection();", "")));
        var nativeAccess = source("mixin/P11ClientMinecraftMixin.java");
        assertTrue(nativeAccess.contains("@Shadow private Connection pendingConnection;"));
        assertTrue(nativeAccess.contains("public Connection p11$pendingConnection() { return pendingConnection; }"));
        assertFalse(nativeAccess.contains("pendingConnection ="));
        assertFalse(controller.contains("sendDeferredPackets("));
    }

    @Test
    void nativeScreenScopeDefersAllReconciliationUntilOutermostNormalReturn() throws Exception {
        var controller = source("P11ClientTransitions.java");
        assertNativeScreenDeferral(controller);
        assertThrows(AssertionError.class, () -> assertNativeScreenDeferral(
                controller.replace("if (nativeScreenDepth > 0)", "if (nativeScreenDepth > 1)")));
        assertThrows(AssertionError.class, () -> assertNativeScreenDeferral(
                controller.replace("if (nativeScreenDepth != 0) { return; }", "")));
        assertThrows(AssertionError.class, () -> assertNativeScreenDeferral(
                controller.replace("if (!normal) { return; }", "")));
        assertThrows(AssertionError.class, () -> assertNativeScreenDeferral(
                controller.replace("else { screenChanged(); }", "else { reconcileScreen(); }")));
    }

    @Test
    void nativeScreenWrapperCallsOriginalOnceAndDoesNotRestoreOnExceptionalUnwind() throws Exception {
        var mixin = source("mixin/P11ClientMinecraftMixin.java");
        assertNativeScreenWrapper(mixin);
        assertThrows(AssertionError.class, () -> assertNativeScreenWrapper(
                mixin.replace("original.call(requested);", "original.call(requested); original.call(requested);")));
        assertThrows(AssertionError.class, () -> assertNativeScreenWrapper(
                mixin.replace("endScreenChange(tracked, normal)", "endScreenChange(tracked, true)")));
        assertThrows(AssertionError.class, () -> assertNativeScreenWrapper(
                mixin.replace("} finally {", "} catch (RuntimeException failure) {")));
    }

    @Test
    void screenScopeIsNotResetByDisconnectOrAnInputGlobalReset() throws Exception {
        var controller = source("P11ClientTransitions.java");
        var disconnected = controller.substring(controller.indexOf("public static void disconnected()"),
                controller.indexOf("public static boolean allowActorFunctions("));
        assertFalse(disconnected.contains("nativeScreenDepth"));
        assertFalse(disconnected.contains("screenReconcileDeferred"));
        var scope = controller.substring(controller.indexOf("public static boolean beginScreenChange()"),
                controller.indexOf("public static void screenChanged()"));
        assertFalse(scope.contains("setScreen("));
        assertFalse(scope.contains("KeyMapping"));
        assertFalse(scope.contains("mouseHandler"));
        assertFalse(scope.contains("ThreadLocal"));
        assertFalse(scope.contains("catch ("));
    }

    private static void assertNativeScreenDeferral(String source) {
        assertTrue(source.contains("private static int nativeScreenDepth;"));
        assertTrue(source.contains("private static boolean screenReconcileDeferred;"));
        var begin = source.substring(source.indexOf("public static boolean beginScreenChange()"),
                source.indexOf("public static void endScreenChange("));
        assertTrue(begin.contains("if (!installed || !Minecraft.getInstance().isSameThread()) { return false; }"));
        assertTrue(begin.contains("nativeScreenDepth == Integer.MAX_VALUE"));
        assertTrue(begin.contains("nativeScreenDepth++;"));
        var end = source.substring(source.indexOf("public static void endScreenChange("),
                source.indexOf("public static void screenChanged()"));
        assertTrue(end.contains("if (!tracked) { return; }"));
        assertTrue(end.contains("nativeScreenDepth--;"));
        assertTrue(end.contains("if (nativeScreenDepth != 0) { return; }"));
        assertTrue(end.contains("screenReconcileDeferred = false;"));
        assertTrue(end.contains("if (!normal) { return; }"));
        assertTrue(end.contains("if (deferred) { reconcileScreen(); }"));
        assertTrue(end.contains("else { screenChanged(); }"));
        assertTrue(end.indexOf("nativeScreenDepth--;") < end.indexOf("if (nativeScreenDepth != 0)"));
        assertTrue(end.indexOf("screenReconcileDeferred = false;") < end.indexOf("if (!normal)"));
        assertTrue(end.indexOf("if (!normal)") < end.indexOf("reconcileScreen();"));
        var reconcile = source.substring(source.indexOf("private static void reconcileScreen()"),
                source.indexOf("private static void tick("));
        assertTrue(reconcile.contains("if (nativeScreenDepth > 0) {\n            screenReconcileDeferred = true;\n            return;\n        }"));
        assertTrue(reconcile.indexOf("if (nativeScreenDepth > 0)") < reconcile.indexOf("Minecraft.getInstance()"));
        assertTrue(reconcile.contains("if (preserveSuccessorScreen(screen)) { return; }"));
    }

    private static void assertNativeScreenWrapper(String source) {
        var wrapper = source.substring(source.indexOf("@WrapMethod(method = \"setScreen("),
                source.indexOf("@Inject(method = \"disconnect("));
        assertTrue(wrapper.contains("setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"));
        assertTrue(wrapper.contains("require = 1, expect = 1, allow = 1"));
        assertTrue(wrapper.contains("boolean tracked = P11ClientTransitions.beginScreenChange();"));
        assertTrue(wrapper.contains("boolean normal = false;"));
        assertEquals(1, wrapper.split(java.util.regex.Pattern.quote("original.call(requested);"), -1).length - 1);
        assertTrue(wrapper.contains("} finally {\n            P11ClientTransitions.endScreenChange(tracked, normal);"));
        assertTrue(wrapper.indexOf("original.call(requested);") < wrapper.indexOf("normal = true;"));
        assertFalse(wrapper.contains("catch ("));
        assertFalse(source.contains("p11$restorePendingScreen"));
        assertFalse(wrapper.contains("screenChanged();"));
    }

    private static void assertActorlessTickOwnership(String source) {
        assertTrue(source.contains("minecraft.level != null"));
        assertTrue(source.contains("minecraft.screen instanceof P11ClientTransitionScreen"));
        assertTrue(source.contains("minecraft.screen instanceof P11ClientLeaveScreen"));
        assertTrue(source.contains("exact != connection"));
        assertTrue(source.contains("listener.getConnection() != exact"));
        assertTrue(source.contains("if (access.p11$pendingConnection() == exact) { return false; }"));
        assertTrue(source.contains("listener.protocol() == ConnectionProtocol.CONFIGURATION"));
        assertTrue(source.contains("!play.p11$actualLoginAllowed()"));
        assertTrue(source.contains("if (ownsTransport && observed.isConnected())"));
        assertTrue(source.contains("observed.tick();"));
        assertTrue(source.contains("if (connection != observed) { return; }"));
        assertTrue(source.contains("if (ownsTransport) { observed.handleDisconnection(); }"));
        assertTrue(source.indexOf("observed.handleDisconnection();")
                < source.indexOf("if (connection == observed) { disconnected(); }"));
    }

    private static P11ClientTransitionState death() {
        var client = new P11ClientTransitionState();
        assertTrue(client.accept(play(1, 0, 1, Kind.DEATH, Outcome.BINDING,
                Availability.DISABLED, Reason.NONE, 0)));
        return client;
    }

    private static State play(long scene, long request, long version, Kind kind,
            Outcome outcome, Availability availability, Reason reason, long target) {
        return new State(Scope.PLAY, 7, scene, 10, request, version, kind,
                outcome, availability, reason, target);
    }

    private static String source(String name) throws Exception {
        var root = Path.of("").toAbsolutePath().normalize();
        while (root != null && !Files.isRegularFile(root.resolve("build.gradle"))) { root = root.getParent(); }
        if (root == null) { throw new IllegalStateException("project root not found"); }
        return Files.readString(root.resolve("src/main/java/com/yo1no/gramarye").resolve(name));
    }
}
