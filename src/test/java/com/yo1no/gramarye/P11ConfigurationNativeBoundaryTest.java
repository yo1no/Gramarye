package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerPlayerConnection;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkChannel;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;
import org.junit.jupiter.api.Test;

/** Native API/field contracts and the real field-region owner; not a substitute for Netty handoff evidence. */
final class P11ConfigurationNativeBoundaryTest {
    @Test
    void reconfigurationInheritanceRequiresRunningEnterSceneAndExactEstablishedTypes() {
        var running = enterConfiguration(P11TransitionProtocol.Outcome.RUNNING);
        var setup = negotiatedSetup();
        var before = Map.of(ConnectionProtocol.CONFIGURATION, Map.copyOf(setup.getChannels(ConnectionProtocol.CONFIGURATION)),
                ConnectionProtocol.PLAY, Map.copyOf(setup.getChannels(ConnectionProtocol.PLAY)));
        assertTrue(negotiationMatches(running, 10, ConnectionType.NEOFORGE,
                ConnectionType.NEOFORGE, ConnectionType.NEOFORGE, setup));
        assertEquals(before, setup.channels(), "the guard does not mutate the negotiated channels");
        assertFalse(negotiationMatches(null, 10, ConnectionType.NEOFORGE,
                ConnectionType.NEOFORGE, ConnectionType.NEOFORGE, setup));
        for (var outcome : new P11TransitionProtocol.Outcome[]{P11TransitionProtocol.Outcome.PENDING,
                P11TransitionProtocol.Outcome.COMPLETED, P11TransitionProtocol.Outcome.UNKNOWN}) {
            assertFalse(negotiationMatches(enterConfiguration(outcome), 10, ConnectionType.NEOFORGE,
                    ConnectionType.NEOFORGE, ConnectionType.NEOFORGE, setup));
        }
        assertFalse(negotiationMatches(running, 0, ConnectionType.NEOFORGE,
                ConnectionType.NEOFORGE, ConnectionType.NEOFORGE, setup));
        assertFalse(negotiationMatches(running, 11, ConnectionType.NEOFORGE,
                ConnectionType.NEOFORGE, ConnectionType.NEOFORGE, setup));
        assertFalse(negotiationMatches(running, 10, ConnectionType.OTHER,
                ConnectionType.NEOFORGE, ConnectionType.NEOFORGE, setup));
        assertFalse(negotiationMatches(running, 10, ConnectionType.NEOFORGE,
                ConnectionType.OTHER, ConnectionType.NEOFORGE, setup));
        assertFalse(negotiationMatches(running, 10, ConnectionType.NEOFORGE,
                ConnectionType.NEOFORGE, ConnectionType.OTHER, setup));
        assertFalse(negotiationMatches(running, 10, ConnectionType.NEOFORGE,
                ConnectionType.NEOFORGE, null, setup));
        var join = new P11TransitionProtocol.State(P11TransitionProtocol.Scope.CONFIG, 1, 1, 0, 0, 1,
                P11TransitionProtocol.Kind.JOIN, P11TransitionProtocol.Outcome.PENDING,
                P11TransitionProtocol.Availability.DISABLED, P11TransitionProtocol.Reason.NONE, 0);
        assertFalse(negotiationMatches(join, 10, ConnectionType.NEOFORGE,
                ConnectionType.NEOFORGE, ConnectionType.NEOFORGE, setup));
        var death = new P11TransitionProtocol.State(P11TransitionProtocol.Scope.PLAY, 1, 2, 10, 1, 2,
                P11TransitionProtocol.Kind.DEATH, P11TransitionProtocol.Outcome.RUNNING,
                P11TransitionProtocol.Availability.DISABLED, P11TransitionProtocol.Reason.NONE, 0);
        assertFalse(negotiationMatches(death, 10, ConnectionType.NEOFORGE,
                ConnectionType.NEOFORGE, ConnectionType.NEOFORGE, setup));
    }

    @Test
    void reconfigurationCannotTurnAbsentPartialOrWrongVersionChannelsIntoNegotiation() {
        var running = enterConfiguration(P11TransitionProtocol.Outcome.RUNNING);
        assertFalse(negotiationMatches(running, 10, ConnectionType.NEOFORGE,
                ConnectionType.NEOFORGE, ConnectionType.NEOFORGE, null));
        assertFalse(negotiationMatches(running, 10, ConnectionType.NEOFORGE,
                ConnectionType.NEOFORGE, ConnectionType.NEOFORGE, NetworkPayloadSetup.empty()));
        for (var protocol : new ConnectionProtocol[]{ConnectionProtocol.CONFIGURATION, ConnectionProtocol.PLAY}) {
            for (var id : Set.of(P11TransitionRequestPayload.TYPE.id(), P11TransitionStatePayload.TYPE.id())) {
                var missing = negotiatedSetup();
                missing.getChannels(protocol).remove(id);
                assertFalse(negotiationMatches(running, 10, ConnectionType.NEOFORGE,
                        ConnectionType.NEOFORGE, ConnectionType.NEOFORGE, missing));
                var wrongVersion = negotiatedSetup();
                wrongVersion.getChannels(protocol).put(id, new NetworkChannel(id, "different-version"));
                assertFalse(negotiationMatches(running, 10, ConnectionType.NEOFORGE,
                        ConnectionType.NEOFORGE, ConnectionType.NEOFORGE, wrongVersion));
                var wrongId = negotiatedSetup();
                var otherId = id.equals(P11TransitionRequestPayload.TYPE.id())
                        ? P11TransitionStatePayload.TYPE.id() : P11TransitionRequestPayload.TYPE.id();
                wrongId.getChannels(protocol).put(id, new NetworkChannel(otherId, P11TransitionWire.REGISTRAR_VERSION));
                assertFalse(negotiationMatches(running, 10, ConnectionType.NEOFORGE,
                        ConnectionType.NEOFORGE, ConnectionType.NEOFORGE, wrongId));
            }
        }
    }

    @Test
    void reconfigurationWritesOnlyThePinnedSubclassPrivateFieldsAfterConstructorReturn() throws Exception {
        var type = net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl.class;
        assertEquals(ConnectionType.class, type.getDeclaredField("connectionType").getType());
        assertTrue(Modifier.isPrivate(type.getDeclaredField("connectionType").getModifiers()));
        assertEquals(boolean.class, type.getDeclaredField("initializedConnection").getType());
        assertTrue(Modifier.isPrivate(type.getDeclaredField("initializedConnection").getModifiers()));
        var hook = source("mixin/P11ClientConfigurationMixin.java");
        assertNegotiationConstructorHook(hook);
        assertThrows(AssertionError.class, () -> assertNegotiationConstructorHook(hook.replace("at = @At(\"RETURN\")", "at = @At(\"HEAD\")")));
        assertThrows(AssertionError.class, () -> assertNegotiationConstructorHook(hook.replace(
                "if (P11ClientTransitions.canInheritConfigurationNegotiation(minecraft, connection, cookie))", "if (true)")));
        assertThrows(AssertionError.class, () -> assertNegotiationConstructorHook(hook.replace(
                "connectionType = cookie.connectionType();", "connectionType = ConnectionType.NEOFORGE;")));
        var controller = section(source("P11ClientTransitions.java"),
                "public static boolean canInheritConfigurationNegotiation(", "private static void requireThread()");
        for (var check : Set.of("!installed", "minecraft != Minecraft.getInstance()", "!minecraft.isSameThread()",
                "exact != connection", "!exact.isConnected()", "exact.getPacketListener() instanceof ClientPacketListener previous",
                "previous.getConnection() != exact", "!state.pending()", "state.current(), state.boundActor()",
                "ChannelAttributes.getConnectionType(exact)", "ChannelAttributes.getPayloadSetup(exact)")) {
            assertTrue(controller.contains(check), check);
        }
        for (var forbidden : Set.of("setPayloadSetup(", "setConnectionType(", "initializeNeoForgeConnection(",
                "initializeOtherConnection(", "send(", "hasChannel(", "new State(", "state.accept(", "setupInboundProtocol(")) {
            assertFalse(controller.contains(forbidden), forbidden);
        }
    }

    private static void assertNegotiationConstructorHook(String hook) {
        assertTrue(hook.contains("@Mixin(ClientConfigurationPacketListenerImpl.class)"));
        assertTrue(hook.contains("<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/network/Connection;Lnet/minecraft/client/multiplayer/CommonListenerCookie;)V"));
        assertTrue(hook.contains("at = @At(\"RETURN\"), require = 1, expect = 1, allow = 1"));
        assertTrue(hook.contains("if (P11ClientTransitions.canInheritConfigurationNegotiation(minecraft, connection, cookie))"));
        assertTrue(hook.contains("connectionType = cookie.connectionType();"));
        assertTrue(hook.contains("initializedConnection = true;"));
        assertEquals(1, occurrences(hook, "connectionType ="));
        assertEquals(1, occurrences(hook, "initializedConnection ="));
        assertFalse(hook.contains("public ") || hook.contains("@Accessor") || hook.contains("@Overwrite")
                || hook.contains("implements ") || hook.contains("send(") || hook.contains("catch ("));
    }

    private static P11TransitionProtocol.State enterConfiguration(P11TransitionProtocol.Outcome outcome) {
        return new P11TransitionProtocol.State(P11TransitionProtocol.Scope.PLAY, 1, 2, 10, 0, 2,
                P11TransitionProtocol.Kind.ENTER_CONFIG, outcome, P11TransitionProtocol.Availability.DISABLED,
                outcome == P11TransitionProtocol.Outcome.UNKNOWN ? P11TransitionProtocol.Reason.STATUS_UNAVAILABLE
                        : P11TransitionProtocol.Reason.NONE, 0);
    }

    private static boolean negotiationMatches(P11TransitionProtocol.State state, long actor, ConnectionType cookie,
            ConnectionType previous, ConnectionType negotiated, NetworkPayloadSetup setup) {
        return P11ClientTransitions.configurationNegotiationMatches(state, actor, cookie, previous, negotiated, setup);
    }

    private static NetworkPayloadSetup negotiatedSetup() {
        var channels = new HashMap<ConnectionProtocol, Map<net.minecraft.resources.ResourceLocation, NetworkChannel>>();
        for (var protocol : new ConnectionProtocol[]{ConnectionProtocol.CONFIGURATION, ConnectionProtocol.PLAY}) {
            var values = new HashMap<net.minecraft.resources.ResourceLocation, NetworkChannel>();
            for (var id : Set.of(P11TransitionRequestPayload.TYPE.id(), P11TransitionStatePayload.TYPE.id())) {
                values.put(id, new NetworkChannel(id, P11TransitionWire.REGISTRAR_VERSION));
            }
            channels.put(protocol, values);
        }
        return new NetworkPayloadSetup(channels);
    }

    @Test
    void parkingIsActualActorlessCommonPlayListenerWithOnlyBoundedRetainedMaterial() {
        assertEquals(ServerCommonPacketListenerImpl.class, P11ParkingPacketListener.class.getSuperclass());
        assertTrue(ServerGamePacketListener.class.isAssignableFrom(P11ParkingPacketListener.class));
        assertFalse(ServerPlayerConnection.class.isAssignableFrom(P11ParkingPacketListener.class));
        var instanceFields = Arrays.stream(P11ParkingPacketListener.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getName() + ":" + field.getType().getSimpleName()).collect(Collectors.toSet());
        assertEquals(Set.of("profile:GameProfile", "information:ClientInformation", "benignDrops:long"), instanceFields);
        assertTrue(Arrays.stream(P11ParkingPacketListener.class.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())));
    }

    @Test
    void nativeFourFieldsAreExactlyTheLockedTypesAndAreNotCookieState() throws Exception {
        assertEquals(long.class, ServerCommonPacketListenerImpl.class.getDeclaredField("keepAliveTime").getType());
        assertEquals(boolean.class, ServerCommonPacketListenerImpl.class.getDeclaredField("keepAlivePending").getType());
        assertEquals(long.class, ServerCommonPacketListenerImpl.class.getDeclaredField("keepAliveChallenge").getType());
        assertEquals(int.class, ServerCommonPacketListenerImpl.class.getDeclaredField("latency").getType());
        assertEquals(Set.of("gameProfile", "latency", "clientInformation", "transferred", "connectionType"),
                Arrays.stream(CommonListenerCookie.class.getRecordComponents()).map(component -> component.getName()).collect(Collectors.toSet()));
    }

    @Test
    void transferAndInstallAreOpaqueAndConnectionExposesNoGeneralListenerSetter() {
        for (var type : Set.of(P11KeepAliveBoundary.Transfer.class, P11KeepAliveBoundary.ProtocolInstall.class)) {
            assertTrue(Arrays.stream(type.getDeclaredConstructors()).allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
            assertTrue(Arrays.stream(type.getDeclaredFields()).allMatch(field -> Modifier.isPrivate(field.getModifiers())));
        }
        assertEquals(Set.of("p11$keepAliveGuard", "p11$installParkedGame"),
                Arrays.stream(P11KeepAliveBoundary.ConnectionAccess.class.getDeclaredMethods()).map(method -> method.getName()).collect(Collectors.toSet()));
        assertEquals(Set.of("p11$configurationCookie", "p11$currentConfigurationTask"),
                Arrays.stream(P11ConfigurationBoundary.Access.class.getDeclaredMethods()).map(method -> method.getName()).collect(Collectors.toSet()));
        assertFalse(P11KeepAliveBoundary.transferFrom(null, null));
        assertFalse(P11KeepAliveBoundary.transferTo(null, null));
    }

    @Test
    void eachRealProtocolInstallRetiresPriorSameEnumAcknowledgements() {
        var connection = new TestConnection();
        var first = new TestCommon(connection);
        install(connection, first);
        long phase = first.phase;
        var second = new TestCommon(connection);
        install(connection, second);
        assertEquals(phase + 1, second.phase);
        var region = P11KeepAliveBoundary.openRegion(first, connection.guard, true, 41);
        try {
            P11KeepAliveBoundary.fields(region);
            assertTrue(P11KeepAliveBoundary.dropped(region), "same CONFIG enum does not revive a retired phase");
        } finally { P11KeepAliveBoundary.end(region); }
        assertNull(P11KeepAliveBoundary.region(first));
    }

    @Test
    void oldListenerAcknowledgementUsesCurrentSamePhaseReceiverButOldTickDoesNot() {
        var connection = new TestConnection();
        var first = new TestCommon(connection);
        install(connection, first);
        var successor = new TestCommon(connection);
        // Models the same-phase scalar provenance preserved by the separate opaque native handoff.
        successor.phase = first.phase;
        connection.current = successor;
        var ack = P11KeepAliveBoundary.openRegion(first, connection.guard, true, 41);
        try {
            assertSame(successor, P11KeepAliveBoundary.fields(ack));
            assertFalse(P11KeepAliveBoundary.dropped(ack));
        } finally { P11KeepAliveBoundary.end(ack); }
        var tick = P11KeepAliveBoundary.openRegion(first, connection.guard, false, 0);
        try {
            P11KeepAliveBoundary.fields(tick);
            assertTrue(P11KeepAliveBoundary.dropped(tick));
        } finally { P11KeepAliveBoundary.end(tick); }
    }

    @Test
    void foreignConnectionAndClosedInstallCannotAuthorizeStateWrites() {
        var connection = new TestConnection();
        var listener = new TestCommon(connection);
        assertThrows(IllegalStateException.class,
                () -> P11KeepAliveBoundary.beginProtocolInstall(new TestConnection(), listener));
        assertThrows(IllegalStateException.class,
                () -> P11KeepAliveBoundary.openRegion(listener, new P11KeepAliveBoundary.Guard(), true, 1));
        var install = P11KeepAliveBoundary.beginProtocolInstall(connection, listener);
        assertThrows(IllegalStateException.class,
                () -> P11KeepAliveBoundary.installedPhase(install, new TestCommon(connection)));
        P11KeepAliveBoundary.endProtocolInstall(install);
        assertThrows(IllegalStateException.class, () -> P11KeepAliveBoundary.installedPhase(install, listener));
    }

    @Test
    void nativeSeamsKeepTwoCallTailAndSeparateCodecResetFromSamePlaySwap() throws Exception {
        var boundary = source("P11ConfigurationBoundary.java");
        assertEquals(1, occurrences(boundary, "list.getPlayerForLogin("));
        assertEquals(1, occurrences(boundary, "list.placeNewPlayer("));
        assertFalse(boundary.contains("handleConfigurationFinished("));
        assertTrue(boundary.contains("P11NativeStorageBoundary.parkedConfiguration("));
        assertTrue(boundary.contains("catch (Exception failure)"));
        assertFalse(boundary.contains("catch (Throwable"));
        var placement = source("mixin/P11ParkingPlacementMixin.java");
        assertTrue(placement.contains("setupInboundProtocol(Lnet/minecraft/network/ProtocolInfo;Lnet/minecraft/network/PacketListener;)V"));
        assertTrue(placement.contains("if (!P11ConfigurationBoundary.installParkedGame("));
        assertTrue(placement.contains("original.call(connection, protocol, listener)"));
        var common = source("mixin/P11KeepAliveCommonMixin.java");
        assertFalse(common.contains("@Overwrite"));
        assertTrue(common.contains("try { original.call(packet); }"));
        assertTrue(common.contains("try { original.call(); }"));
        assertTrue(common.contains("P11KeepAliveBoundary.release(P11KeepAliveBoundary.region(receiver));\n        original.call(receiver, reason)"));
        assertTrue(common.contains("P11KeepAliveBoundary.release(P11KeepAliveBoundary.region(receiver));\n        original.call(receiver, packet)"));
        var connection = source("mixin/P11KeepAliveConnectionMixin.java");
        assertTrue(connection.contains("P11KeepAliveBoundary.allowScheduledKeepAlive("));
        assertTrue(connection.contains("try { original.call(connection, listener); }"));
    }

    @Test
    void inertTransferAllocationPrecedesLockAndProtectedBodyStartsImmediately() throws Exception {
        var source = source("P11KeepAliveBoundary.java");
        var method = source.substring(source.indexOf("static void installGame("), source.indexOf("public static ServerGamePacketListenerImpl transferTarget("));
        assertTrue(method.contains("var transfer = new Transfer(connection, previous, next, guard);\n        guard.lock.lock();\n        try {"));
        assertFalse(method.contains("guard.lock.lock();\n        var transfer"), "allocation Error must not retain the exact connection lock");
        assertTrue(method.contains("} finally {\n            transfer.active = false;\n            guard.lock.unlock();"));
    }

    @Test
    void configurationTerminalObservesTheExactAckCallerOnlyAfterNativePipelineReturns() throws Exception {
        var hook = section(source("mixin/P11LivePlayMixin.java"),
                "@WrapOperation(method = \"handleConfigurationAcknowledged", "\n}");
        assertConfigurationTerminalHook(hook);
        assertThrows(AssertionError.class, () -> assertConfigurationTerminalHook(hook.replace(
                "original.call(connection, protocol, listener);", "")));
        assertThrows(AssertionError.class, () -> assertConfigurationTerminalHook(hook.replace(
                "boolean current = connection.getPacketListener() == previous;", "boolean current = true;")));
        assertThrows(AssertionError.class, () -> assertConfigurationTerminalHook(hook.replace(
                "require = 1, expect = 1, allow = 1", "require = 0, expect = 1, allow = 2")));
        assertThrows(AssertionError.class, () -> assertConfigurationTerminalHook(hook.replace(
                "original.call(connection, protocol, listener);", "P11LiveTransitionBoundary.configurationAcknowledged(previous, null);\n        original.call(connection, protocol, listener);")));
    }

    @Test
    void completedAckReservesKBeforePublishingActorlessWorkWithoutChangingPlayAdmission() throws Exception {
        var service = source("P11LiveTransitionService.java");
        var ack = section(service, "void configurationAcknowledged(", "P11LiveTransitionBoundary.TaskDecision taskStarted(");
        assertAckReservation(ack);
        assertThrows(AssertionError.class, () -> assertAckReservation(ack.replace(
                "if (!entry.waiting) { entry.waiting = true; waiting++; }", "")));
        assertThrows(AssertionError.class, () -> assertAckReservation(ack.replace(
                "entries.get(connection) != entry", "entry == null")));
        assertThrows(AssertionError.class, () -> assertAckReservation(ack.replace(
                "if (!entry.waiting && waiting >= limits.maxWaitingConnections())", "if (false)")));
        var task = section(service, "P11LiveTransitionBoundary.TaskDecision taskStarted(", "void tick()");
        assertTrue(task.contains("rejected = existing != null && existing.configurationCapacityRejected;"));
        assertTrue(task.contains("if (!rejected && !admitted"));
        assertTrue(task.contains("existing != null && existing.waiting"));
        var admission = section(service, "private Ticket admit(", "private void execute(");
        assertFalse(admission.contains("waiting") || admission.contains("maxWaitingConnections"),
                "still-PLAY F0 and native ENTER_CONFIG caller admission do not occupy CONFIG/PREPLAY K");
        var completion = section(service, "private void completeConfigurationAcknowledgement(", "private void earlyTask(");
        assertFalse(completion.contains("waiting--") || completion.contains("waiting = false"),
                "ENTER_CONFIG completion keeps the same K reservation for later RETURN");
    }

    @Test
    void acknowledgementCorrelationUsesTheExactPriorNativeBindingWithoutRosterOrUuidSubstitution() throws Exception {
        var body = section(source("P11IdentityOwner.java"), "synchronized boolean matchesConfigurationPredecessor(",
                "synchronized boolean owns(AccountKey account)");
        assertExactConfigurationPredecessor(body);
        for (var check : Set.of("!current(expected)", "nativeRef.actor == previous.player",
                "nativeRef.actor.connection == previous", "nativeRef.connection == previous.getConnection()",
                "nativeRef.connection == installed.getConnection()", "nativeRef.connection.getPacketListener() == installed")) {
            assertThrows(AssertionError.class, () -> assertExactConfigurationPredecessor(body.replace(check, "true")), check);
        }
        var model = P11IdentityOwner.isolatedModel(1);
        var capture = model.modelActorless(new UUID(0, 1), model.modelConnection()).orElseThrow();
        assertFalse(model.matchesConfigurationPredecessor(capture, null, null));
        assertFalse(model.matchesConfigurationPredecessor(null, null, null));
        var ack = section(source("P11LiveTransitionService.java"), "void configurationAcknowledged(",
                "P11LiveTransitionBoundary.TaskDecision taskStarted(");
        assertTrue(ack.contains("identities.matchesConfigurationPredecessor(bound, previous, installed)"));
        assertTrue(ack.contains("!bound.currentBinding()"));
        assertFalse(ack.contains("previous.player.getUUID()"));
    }

    @Test
    void independentConfigTerminalCannotBeSynthesizedByTheLaterReturnTask() throws Exception {
        var service = source("P11LiveTransitionService.java");
        var completion = section(service, "private void completeConfigurationAcknowledgement(", "private void earlyTask(");
        assertIndependentConfigTerminal(completion);
        assertThrows(AssertionError.class, () -> assertIndependentConfigTerminal(completion.replace(
                "access.p11$keepAlivePhase() != phase", "false")));
        assertThrows(AssertionError.class, () -> assertIndependentConfigTerminal(completion +
                "entry.control.openServerScene(Scope.CONFIG, Kind.RETURN_TO_WORLD);"));
        var task = section(service, "private void initializeConfiguration(", "private void service(");
        assertFalse(task.contains("beginHandoff(") || task.contains("completeHandoff("));
        assertTrue(task.contains("state.scope() != Scope.CONFIG || state.outcome() != Outcome.COMPLETED"));
        assertTrue(task.contains("openServerScene(Scope.CONFIG, Kind.RETURN_TO_WORLD)"));
        var dispatch = section(service, "private void service(", "private void completeConfigurationAcknowledgement(");
        assertTrue(dispatch.indexOf("completeConfigurationAcknowledgement(entry)") < dispatch.indexOf("if (entry.configurationStarting)"));
        assertTrue(dispatch.contains("logicalListener == null || logicalListener.scope() == Scope.PLAY"));
        assertPendingReturnWaitsForCaller(dispatch);
        assertThrows(AssertionError.class, () -> assertPendingReturnWaitsForCaller(dispatch.replace(
                "&& entry.control.executableHeld()", "")));
        assertThrows(AssertionError.class, () -> assertPendingReturnWaitsForCaller(dispatch.replace(
                "if (entry.configurationStarting) {", "if (entry.configurationStarting) {\n            entry.configurationStarting = false;")));
        var rejected = dispatch.substring(0, dispatch.indexOf("if (entry.configurationTerminalPhase != 0) {\n            completeConfigurationAcknowledgement"));
        assertTrue(rejected.contains("entry.configurationTerminalPhase = 0;"));
        assertTrue(rejected.contains("entry.control.retire();"));
        assertTrue(rejected.contains("disconnect(entry, \"gramarye.transition.capacity\");"));
        assertFalse(rejected.contains("sources.") || rejected.contains("waiting--"));
    }

    private static void assertConfigurationTerminalHook(String hook) {
        assertTrue(hook.contains("handleConfigurationAcknowledged(Lnet/minecraft/network/protocol/game/ServerboundConfigurationAcknowledgedPacket;)V"));
        assertTrue(hook.contains("@At(value = \"INVOKE\", target = \"Lnet/minecraft/network/Connection;setupInboundProtocol(Lnet/minecraft/network/ProtocolInfo;Lnet/minecraft/network/PacketListener;)V\")"));
        assertTrue(hook.contains("require = 1, expect = 1, allow = 1"));
        assertTrue(hook.contains("boolean current = connection.getPacketListener() == previous;"));
        int original = hook.indexOf("original.call(connection, protocol, listener);");
        int observed = hook.indexOf("P11LiveTransitionBoundary.configurationAcknowledged(");
        assertTrue(original >= 0 && observed > original);
        assertTrue(hook.contains("if (current) {"));
        assertFalse(hook.contains("finally") || hook.contains("catch (") || hook.contains("returnToWorld("));
    }

    private static void assertAckReservation(String ack) {
        var locked = section(ack, "synchronized (entries) {", "\n        dispatcher.eligible(");
        assertTrue(locked.contains("entries.get(connection) != entry"));
        assertTrue(locked.contains("connection.getPacketListener() != installed"));
        assertTrue(locked.contains("if (!entry.waiting && waiting >= limits.maxWaitingConnections())"));
        assertTrue(locked.contains("entry.configurationCapacityRejected = true;"));
        int reserve = locked.indexOf("if (!entry.waiting) { entry.waiting = true; waiting++; }");
        int publish = locked.indexOf("entry.configurationTerminalPhase = phase;");
        assertTrue(reserve >= 0 && publish > reserve);
        for (var forbidden : Set.of("disconnect(", "requestPump(", "dispatcher.", "identities.", "sources.", "entry.control.")) {
            assertFalse(locked.contains(forbidden), forbidden);
        }
        assertFalse(ack.contains("new TickTask(") || ack.contains("server.execute("));
    }

    private static void assertIndependentConfigTerminal(String completion) {
        assertTrue(completion.contains("access.p11$keepAlivePhase() != phase"));
        assertTrue(completion.contains("identities.bindAuthenticatedActorless(config)"));
        assertTrue(completion.contains("entry.control.bindActorlessHandoff(handoff, bound)"));
        assertTrue(completion.contains("entry.control.completeHandoff(handoff)"));
        assertTrue(completion.contains("notifyLatest(entry);"));
        assertFalse(completion.contains("openServerScene(") || completion.contains("taskPending = true")
                || completion.contains("returnToWorld(") || completion.contains("completeTask("));
    }

    private static void assertPendingReturnWaitsForCaller(String dispatch) {
        int pending = dispatch.indexOf("if (entry.configurationStarting) {");
        int held = dispatch.indexOf("state != null && state.kind() == Kind.ENTER_CONFIG && entry.control.executableHeld()", pending);
        int waiting = dispatch.indexOf("return; // A fast ACK/task", pending);
        int consume = dispatch.indexOf("entry.configurationStarting = false;", pending);
        assertTrue(pending >= 0 && held > pending && waiting > held && consume > waiting);
    }

    private static void assertExactConfigurationPredecessor(String body) {
        for (var check : Set.of("!current(expected)", "references instanceof NativeReferences nativeRef",
                "nativeRef.actor == previous.player", "nativeRef.actor.connection == previous",
                "nativeRef.actor.getServer() == server", "previous.getMainThreadEventLoop() == server",
                "installed.getMainThreadEventLoop() == server", "nativeRef.connection == previous.getConnection()",
                "nativeRef.connection == installed.getConnection()", "nativeRef.connection.getPacketListener() == installed",
                "nativeRef.connection.isConnected()")) { assertTrue(body.contains(check), check); }
        assertFalse(body.contains("getUUID()") || body.contains("getPlayerList()") || body.contains("isSameThread()")
                || body.contains("new ") || body.contains(".put("));
    }

    private static String section(String text, String start, String end) {
        int from = text.indexOf(start);
        assertTrue(from >= 0, start);
        int to = text.indexOf(end, from);
        assertTrue(to > from, end);
        return text.substring(from, to);
    }

    private static String source(String name) throws Exception {
        var directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.isRegularFile(directory.resolve("build.gradle"))) { directory = directory.getParent(); }
        if (directory == null) { throw new IllegalStateException("REPOSITORY_ROOT_NOT_FOUND"); }
        return Files.readString(directory.resolve("src/main/java/com/yo1no/gramarye").resolve(name));
    }

    private static int occurrences(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }

    private static void install(TestConnection connection, TestCommon listener) {
        var install = P11KeepAliveBoundary.beginProtocolInstall(connection, listener);
        try { connection.current = listener; }
        finally { P11KeepAliveBoundary.endProtocolInstall(install); }
    }

    private static final class TestConnection extends Connection implements P11KeepAliveBoundary.ConnectionAccess {
        private final P11KeepAliveBoundary.Guard guard = new P11KeepAliveBoundary.Guard();
        private PacketListener current;
        private TestConnection() { super(PacketFlow.SERVERBOUND); }
        @Override public PacketListener getPacketListener() { return current; }
        @Override public P11KeepAliveBoundary.Guard p11$keepAliveGuard() { return guard; }
        @Override public void p11$installParkedGame(P11KeepAliveBoundary.Transfer transfer) { throw new AssertionError("not a native handoff fixture"); }
    }

    private static final class TestCommon extends ServerConfigurationPacketListenerImpl implements P11KeepAliveBoundary.CommonAccess {
        private long phase;
        private TestCommon(Connection connection) {
            super(null, connection, CommonListenerCookie.createInitial(new GameProfile(new UUID(0, 1), "field-test"), false));
        }
        @Override public long p11$keepAlivePhase() { return phase; }
        @Override public void p11$beginKeepAlivePhase(P11KeepAliveBoundary.ProtocolInstall install) {
            phase = P11KeepAliveBoundary.installedPhase(install, this);
        }
        @Override public boolean p11$ownsKeepAliveChallenge(long challenge) { return false; }
        @Override public void p11$copyKeepAlive(P11KeepAliveBoundary.Transfer transfer) { throw new AssertionError("not a native handoff fixture"); }
        @Override public void p11$receiveKeepAlive(P11KeepAliveBoundary.Transfer transfer, long time, boolean pending, long challenge, int latency) {
            throw new AssertionError("not a native handoff fixture");
        }
    }
}
