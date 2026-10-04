package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Direct account facts and exact native wiring; no fabricated player/authentication evidence. */
final class P11LiveNativeBoundaryTest {
    @Test void nativeCountsArePerAccountAndAllThreeBlockersMustEnd() throws Exception {
        var first = new P11QualifiedSourceOwner.Account(null);
        var second = new P11QualifiedSourceOwner.Account(null);
        first.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] = 2;
        first.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] = 1;
        first.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] = 1;
        second.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] = 1;
        assertEquals(P11QualifiedSourceOwner.ControlGate.ACTIVE_OPERATION, blocker(first));
        assertEquals(P11QualifiedSourceOwner.ControlGate.ACTIVE_CONTEXT, blocker(second));
        first.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] = 0;
        assertEquals(P11QualifiedSourceOwner.ControlGate.ACTIVE_CONTEXT, blocker(first));
        first.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] = 0;
        assertEquals(P11QualifiedSourceOwner.ControlGate.ACTIVE_TRANSITION, blocker(first));
        first.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] = 0;
        assertEquals(P11QualifiedSourceOwner.ControlGate.CLEAR, blocker(first));
        assertEquals(P11QualifiedSourceOwner.ControlGate.ACTIVE_CONTEXT, blocker(second));
    }

    @Test void retainedCreditDirtyAndWriterDutiesDoNotBecomeTransitionBlockers() throws Exception {
        var account = new P11QualifiedSourceOwner.Account(null);
        account.nativeCounts[P11ControlBudgets.Root.NATIVE_CREDIT.ordinal()] = 1;
        account.nativeCounts[P11ControlBudgets.Root.DIRTY.ordinal()] = 1;
        account.nativeCounts[P11ControlBudgets.Root.WRITE.ordinal()] = 1;
        assertEquals(P11QualifiedSourceOwner.ControlGate.CLEAR, blocker(account));
    }

    @Test void sourceObservationCannotEnrollUnmanagedOrUpgradeUnknownMaterial() throws Exception {
        String owner = read("P11QualifiedSourceOwner.java");
        String gate = section(owner, "ControlGate controlGate(", "private static ControlGate nativeBlocker(");
        assertTrue(gate.contains("if (account == null) { return ControlGate.UNMANAGED; }"));
        assertTrue(gate.contains("account.constructorFailed || account.cleanupUnknown"));
        assertTrue(gate.contains("expectedActor != null && body.actor != expectedActor"));
        assertTrue(gate.contains("canCopy(body) && canonicalInputComplete(body)"));
        assertBefore(gate, "&& (!canCopy(body) || !canonicalInputComplete(body))", "var blocker = nativeBlocker(account);");
        assertFalse(gate.contains("candidate(") || gate.contains("captureSource(") || gate.contains("retainRoot(")
                || gate.contains("tryAcquireRoot(") || gate.contains("Summary") || gate.contains("ENGINEERING"));
        String custody = section(owner, "ControlCustody beginControlCustody(", "/** Validate retained canonical input.");
        assertTrue(custody.contains("if (gate == ControlGate.UNMANAGED) { return null; }"));
        assertTrue(custody.contains("if (gate != ControlGate.CLEAR) { throw new SourceUnavailable(); }"));
        assertTrue(custody.contains("retainNativeRoot(body, P11ControlBudgets.Root.TRANSITION)"));
        assertTrue(custody.contains("custody.owner != this"));
        assertTrue(custody.contains("if (custody.closed) { return; }"));
        for (var constructor : P11QualifiedSourceOwner.ControlCustody.class.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        }
    }

    @Test void rawFactoryAndRespawnGuardsPrecedeAllNativeAndSourceWork() throws Exception {
        String source = read("P11NativeStorageBoundary.java");
        String factory = section(source, "public static ServerPlayer loginPlayer(", "private static void closeSelection(");
        assertBefore(factory, "requireFactoryTicket(list, profile, information)", "root.writerOwner(list)");
        assertBefore(factory, "requireFactoryTicket(list, profile, information)", "original.call(profile, information)");
        String respawn = section(source, "public static ServerPlayer respawn(", "public static void copy(");
        assertBefore(respawn, "requireRespawnTicket(list, old, keepEverything)", "lifecycleOwner(old)");
        assertBefore(respawn, "requireRespawnTicket(list, old, keepEverything)", "original.call(old, keepEverything, reason)");
        assertTrue(respawn.contains("finally {") && respawn.contains("COPY.remove();"));
        assertFalse(respawn.contains("return old;") || respawn.contains("return null;"));
    }

    @Test void inactiveCompatibilityRequiresAPresentNonReadySnapshotOfTheExactOwningServer() throws Exception {
        String source = read("P11LiveTransitionBoundary.java");
        assertInactiveObservation(source);
        assertThrows(AssertionError.class, () -> assertInactiveObservation(source.replace(
                "!exact.isSameThread()", "false")));
        assertThrows(AssertionError.class, () -> assertInactiveObservation(source.replace(
                "current.startupState(exact).orElse(null)", "P11StartupLoadState.Unavailable.INSTANCE")));
        assertThrows(AssertionError.class, () -> assertInactiveObservation(source.replace(
                "observed instanceof P11StartupLoadState.Unavailable", "observed == null")));
        var method = P11LiveTransitionBoundary.class.getDeclaredMethod("observedInactive",
                net.minecraft.server.MinecraftServer.class);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        method.setAccessible(true);
        assertEquals(false, method.invoke(null, new Object[] {null}));
    }

    @Test void inactiveCompatibilityDoesNotGrantConfigAdmissionOrAReadyRootsNativeTicket() throws Exception {
        String source = read("P11LiveTransitionBoundary.java");
        String config = section(source, "public static TaskDecision configurationTaskStarted(",
                "public static boolean keepAliveManaged(");
        assertTrue(config.contains("listener.disconnect(") && config.contains("return TaskDecision.WAIT;"));
        assertTrue(config.contains("required().configurationFinished(listener, original, packet)"));
        assertTrue(config.contains("return required().beforeConfigurationFactory(listener);"));
        assertFalse(config.contains("observedInactive("));
        String respawn = section(source, "public static void performRespawn(",
                "static P11LiveTransitionService.Ticket currentNativeAttempt(");
        assertTrue(respawn.contains("if (observedInactive(listener)) { original.call(packet); return; }"));
        assertTrue(respawn.contains("required().performRespawn(listener, packet, original);"));
        assertTrue(respawn.contains("if (observedInactive(listener)) { original.call(); return; }"));
        assertTrue(respawn.contains("required().switchToConfig(listener, original);"));
        String guards = section(source, "public static void requireFactoryTicket(", "static boolean adoptSourceBody(");
        assertTrue(guards.contains("if (observedInactive(list)) { return; }"));
        assertTrue(guards.contains("if (observedInactive(list) && actor.getServer() == list.getServer()) { return; }"));
        assertTrue(guards.contains("required().requireFactory(list, profile, information);"));
        assertTrue(guards.contains("required().requireRespawn(list, actor, keepEverything);"));
        assertFalse(guards.contains("service() == null"));
    }

    @Test void inactiveFrameObservationUsesTheExactNativePlayerListNotPacketTypeOrServiceAbsence() throws Exception {
        var method = P11LiveTransitionBoundary.class.getDeclaredMethod("expectedNativeFrame",
                net.minecraft.server.players.PlayerList.class, net.minecraft.network.protocol.Packet.class);
        assertTrue(Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
        assertThrows(NoSuchMethodException.class, () -> P11LiveTransitionBoundary.class.getDeclaredMethod(
                "expectedNativeFrame", net.minecraft.network.protocol.Packet.class));
        String boundary = read("P11LiveTransitionBoundary.java");
        String frame = section(boundary, "public static void expectedNativeFrame(", "static boolean adoptSourceBody(");
        assertTrue(frame.contains("if (observedInactive(list)) { return; }"));
        assertTrue(frame.contains("required().expectedNativeFrame(packet);"));
        String mixin = section(read("mixin/P11PlayerListMixin.java"),
                "@ModifyExpressionValue(method = \"placeNewPlayer", "@WrapMethod(method = \"load(");
        assertEquals(2, mixin.split(java.util.regex.Pattern.quote(
                "P11LiveTransitionBoundary.expectedNativeFrame((PlayerList) (Object) this, packet);"), -1).length - 1);
        assertEquals(2, mixin.split(java.util.regex.Pattern.quote("return packet;"), -1).length - 1);
    }

    @Test void parkedSourceSelectionRequiresActiveTicketAndAlwaysClosesCallLocalScope() throws Exception {
        String source = section(read("P11NativeStorageBoundary.java"), "public static void parkedConfiguration(",
                "/** Select before the original constructor");
        assertParked(source);
        assertThrows(AssertionError.class, () -> assertParked(source.replace(
                "P11LiveTransitionBoundary.currentNativeAttempt(listener) == null", "false")));
        assertThrows(AssertionError.class, () -> assertParked(source.replace(
                "listener.getConnection().getPacketListener() != listener", "false")));
    }

    @Test void rawPacketWrapperPreservesPacketUtilsAndUnrelatedStatsAction() throws Exception {
        String source = read("mixin/P11LivePlayMixin.java");
        assertTrue(source.contains("handleClientCommand(Lnet/minecraft/network/protocol/game/ServerboundClientCommandPacket;)V"));
        assertTrue(source.contains("!listener.getMainThreadEventLoop().isSameThread()"));
        assertTrue(source.contains("packet.getAction() != ServerboundClientCommandPacket.Action.PERFORM_RESPAWN"));
        assertTrue(source.contains("original.call(packet);\n            return;"));
        assertTrue(source.contains("P11LiveTransitionBoundary.performRespawn(listener, packet, original)"));
        assertTrue(source.contains("@Invoker(\"handleClientCommand\")"));
        assertTrue(source.contains("@Invoker(\"switchToConfig\")"));
        assertFalse(source.contains("resetLastActionTime") || source.contains("wonGame =") || source.contains(".handleClientCommand("));
    }

    @Test void scenePublicationUsesExactOriginalDeathCancellationAndEndFrameAnchors() throws Exception {
        String source = read("mixin/P11ServerPlayerMixin.java");
        assertTrue(source.contains("CommonHooks;onLivingDeath(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)Z"));
        assertBefore(source, "boolean cancelled = original.call(actor, damage);", "if (!cancelled) { P11LiveTransitionBoundary.publishDeath");
        assertTrue(source.contains("@Inject(method = \"showEndCredits()V\""));
        assertTrue(source.contains("ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"));
        assertTrue(source.contains("require = 1, expect = 1, allow = 1"));
    }

    @Test void configurationTickRetirementRequiresTheExactAdmittedOriginalEntry() throws Exception {
        String live = read("P11LiveTransitionService.java");
        String proof = section(live, "boolean configurationTickRetirementAuthorized(",
                "Ticket current(ServerCommonPacketListenerImpl caller)");
        assertConfigurationTickProof(proof);
        for (String guard : new String[] {"stopping || !server.isSameThread()",
                "listener.getMainThreadEventLoop() != server", "ticket != null", "ticket.owner == this",
                "ticket.kind == Kind.ENTER_CONFIG", "ticket.nativeEntryConsumed",
                "ticket.originalActor == listener.player", "listener.player.connection == listener",
                "listener.player.getServer() == server", "ticket.entry.connection == listener.getConnection()",
                "entry(ticket.entry.connection) == ticket.entry",
                "ticket.entry.connection.getPacketListener() == listener", "ticket.entry.connection.isConnected()"}) {
            assertThrows(AssertionError.class, () -> assertConfigurationTickProof(proof.replace(guard, "REMOVED")), guard);
        }
        String current = section(live, "Ticket current(ServerCommonPacketListenerImpl caller)",
                "private Ticket active()");
        assertTrue(current.contains("call.caller == caller") && current.contains("!call.ticket.closed"));
        String boundary = section(read("P11LiveTransitionBoundary.java"),
                "public static boolean configurationTickRetirementAuthorized(",
                "static P11LiveTransitionService.Ticket currentNativeAttempt(");
        assertTrue(boundary.contains("return service != null && service.configurationTickRetirementAuthorized(listener);"));
        assertFalse(boundary.contains("required()") || boundary.contains("new ") || boundary.contains("original.call"));
    }

    @Test void configurationTickLatchIsInsideOriginalAdapterBeforeAnyNativeCallback() throws Exception {
        String mixin = read("mixin/P11LivePlayMixin.java");
        String entry = section(mixin, "private void p11$enterConfiguration(",
                "private boolean p11$retiredConfigurationActor()");
        assertBefore(entry, "P11LiveTransitionBoundary.switchToConfig(listener, args -> {",
                "P11LiveTransitionBoundary.configurationTickRetirementAuthorized(listener)");
        assertBefore(entry, "p11$configurationTickRetired = true;", "return original.call(args);");
        assertEquals(1, entry.split("original\\.call\\(", -1).length - 1);
        assertFalse(entry.contains("catch (") || entry.contains("finally") || entry.contains("return null"));
        assertEquals(1, mixin.split("p11\\$configurationTickRetired = true;", -1).length - 1);
        assertFalse(mixin.contains("p11$configurationTickRetired = false"));
        assertTrue(mixin.contains("@Unique private boolean p11$configurationTickRetired;"));
        String guard = section(mixin, "private boolean p11$retiredConfigurationActor()", "@ModifyExpressionValue(method = \"tick()V\"");
        assertRetiredActor(guard);
        for (String fact : new String[] {"p11$configurationTickRetired", "waitingForSwitchToConfig",
                "player.isRemoved()", "player.hasDisconnected()"}) {
            assertThrows(AssertionError.class, () -> assertRetiredActor(guard.replace(fact, "REMOVED")), fact);
        }
        // It is irreversible old-listener disqualification, not a fresh lookup/permit.
        assertFalse(guard.contains("getPacketListener") || guard.contains("TerminalInterval")
                || guard.contains("service()") || guard.contains("getUUID"));
    }

    @Test void onlyTwoRetiredActorProducersAreSkippedNotTheGameListenerTick() throws Exception {
        String mixin = read("mixin/P11LivePlayMixin.java");
        String hooks = section(mixin, "@ModifyExpressionValue(method = \"tick()V\"",
                "@WrapOperation(method = \"handleConfigurationAcknowledged");
        assertTrue(hooks.contains("ServerGamePacketListenerImpl;ackBlockChangesUpTo:I"));
        assertTrue(hooks.contains("opcode = Opcodes.GETFIELD, ordinal = 0"));
        assertTrue(hooks.contains("return p11$retiredConfigurationActor() ? -1 : original;"));
        assertTrue(hooks.contains("ServerPlayer;doTick()V"));
        assertTrue(hooks.contains("if (!p11$retiredConfigurationActor()) { original.call(actor); }"));
        assertEquals(2, hooks.split("require = 1, expect = 1, allow = 1", -1).length - 1);
        assertFalse(hooks.contains("catch (") || hooks.contains(".send(") || hooks.contains(".disconnect(")
                || hooks.contains("resetLastActionTime") || hooks.contains("ackBlockChangesUpTo ="));
        assertFalse(mixin.contains("@WrapMethod(method = \"tick()V\"") || mixin.contains("cancellable = true"));
    }


    @Test void bossSendObservationIsReadOnlyExactListenerRetirementOnOwningMain() throws Exception {
        var observer = P11LivePlayAccess.class.getDeclaredMethod("p11$configurationActorRetired");
        assertEquals(boolean.class, observer.getReturnType());
        assertTrue(Modifier.isPublic(observer.getModifiers()) && Modifier.isAbstract(observer.getModifiers()));
        assertEquals(0, observer.getParameterCount());
        assertEquals(java.util.Set.of("p11$performRespawn", "p11$switchToConfig", "p11$configurationActorRetired"),
                java.util.Arrays.stream(P11LivePlayAccess.class.getDeclaredMethods())
                        .map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet()));
        assertEquals(3, P11LivePlayAccess.class.getDeclaredMethods().length);
        String mixin = read("mixin/P11LivePlayMixin.java");
        String observation = section(mixin, "public boolean p11$configurationActorRetired()",
                "@WrapMethod(method = \"handleClientCommand");
        assertBossObservation(observation);
        assertThrows(AssertionError.class, () -> assertBossObservation(observation.replace(
                "listener.getMainThreadEventLoop().isSameThread()", "true")));
        assertThrows(AssertionError.class, () -> assertBossObservation(observation.replace(
                "p11$retiredConfigurationActor()", "true")));
        assertRetiredActor(section(mixin, "private boolean p11$retiredConfigurationActor()",
                "@ModifyExpressionValue(method = \"tick()V\""));
    }

    @Test void onlyFourBossSendCallsitesAreGuardedWithoutChangingNativeMembershipOrExceptions() throws Exception {
        String mixin = read("mixin/P11ServerBossEventMixin.java");
        assertBossCallsites(mixin);
        assertThrows(AssertionError.class, () -> assertBossCallsites(mixin.replace(
                "broadcast(Ljava/util/function/Function;)V", "broadcast")));
        assertThrows(AssertionError.class, () -> assertBossCallsites(mixin.replace(
                "allow = 1", "allow = 2")));
        assertThrows(AssertionError.class, () -> assertBossCallsites(mixin.replace(
                "!((P11LivePlayAccess) listener).p11$configurationActorRetired()", "true")));
        assertThrows(AssertionError.class, () -> assertBossCallsites(mixin.replace(
                "original.call(listener, packet);", "original.call(listener, packet); original.call(listener, packet);")));
        assertThrows(AssertionError.class, () -> assertBossCallsites(mixin.replace(
                "original.call(listener, packet);", "try { original.call(listener, packet); } catch (RuntimeException ignored) {}")));
        assertThrows(AssertionError.class, () -> assertBossCallsites(mixin.replace(
                "original.call(listener, packet);", "players.remove(listener.player); original.call(listener, packet);")));
    }

    private static void assertBossObservation(String source) {
        assertTrue(source.contains("var listener = (ServerGamePacketListenerImpl) (Object) this;"));
        assertTrue(source.contains("return listener.getMainThreadEventLoop().isSameThread() && p11$retiredConfigurationActor();"));
        assertFalse(source.contains("getUUID") || source.contains("getPacketListener") || source.contains("new ")
                || source.contains("original.call") || source.contains(" = true") || source.contains("catch ("));
    }

    private static void assertBossCallsites(String source) {
        assertTrue(source.contains("@Mixin(ServerBossEvent.class)"));
        for (String method : new String[] {"broadcast(Ljava/util/function/Function;)V",
                "addPlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
                "removePlayer(Lnet/minecraft/server/level/ServerPlayer;)V", "setVisible(Z)V"}) {
            assertEquals(1, source.split(java.util.regex.Pattern.quote("@WrapOperation(method = \"" + method + "\""), -1).length - 1);
        }
        assertEquals(4, source.split(java.util.regex.Pattern.quote(
                "target = \"Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V\""), -1).length - 1);
        assertEquals(4, source.split("require = 1, expect = 1, allow = 1", -1).length - 1);
        assertEquals(4, source.split(java.util.regex.Pattern.quote("p11$sendToActiveListener(listener, packet, original);"), -1).length - 1);
        assertTrue(source.contains("if (!((P11LivePlayAccess) listener).p11$configurationActorRetired()) {"));
        assertFalse(source.contains("instanceof P11LivePlayAccess"));
        assertEquals(1, source.split(java.util.regex.Pattern.quote("original.call(listener, packet);"), -1).length - 1);
        assertFalse(source.contains("@WrapMethod") || source.contains("@Overwrite") || source.contains("cancellable")
                || source.contains("catch (") || source.contains(".add(") || source.contains(".remove(")
                || source.contains(".clear(") || source.contains("new ") || source.contains("getUUID")
                || source.contains("== listener.player") || source.contains("!= listener.player"));
    }

    private static void assertConfigurationTickProof(String proof) {
        for (String guard : new String[] {"stopping || !server.isSameThread()",
                "listener.getMainThreadEventLoop() != server", "ticket != null", "ticket.owner == this",
                "ticket.kind == Kind.ENTER_CONFIG", "ticket.nativeEntryConsumed",
                "ticket.originalActor == listener.player", "listener.player.connection == listener",
                "listener.player.getServer() == server", "ticket.entry.connection == listener.getConnection()",
                "entry(ticket.entry.connection) == ticket.entry",
                "ticket.entry.connection.getPacketListener() == listener", "ticket.entry.connection.isConnected()"}) {
            assertTrue(proof.contains(guard), guard);
        }
        assertTrue(proof.contains("var ticket = current(listener);"));
        assertFalse(proof.contains("new ") || proof.contains("original.call") || proof.contains(".admit("));
    }

    private static void assertRetiredActor(String guard) {
        assertTrue(guard.contains("return p11$configurationTickRetired && waitingForSwitchToConfig\n"
                + "                && player.isRemoved() && player.hasDisconnected();"));
    }

    @Test void rootReleaseOnlyMarksLaterRecheckAfterActualCountReachesZero() throws Exception {
        String source = section(read("P11QualifiedSourceOwner.java"), "void releaseNativeRoot(",
                "/** May advance v inside");
        assertBefore(source, "if (--account.nativeCounts[index] == 0)", "P11LiveTransitionBoundary.blockersChanged");
        assertBefore(source, "resources.releaseRoot(account.nativeRoots[index]);", "P11LiveTransitionBoundary.blockersChanged");
        assertFalse(source.contains("send(") || source.contains("resume(") || source.contains("performRespawn(")
                || source.contains("execute(") || source.contains("CompletableFuture"));
    }

    @Test void detachedNativeContinuityRequiresLiveCapabilityAndActualRetainedCause() throws Exception {
        String source = read("P11NativeOperationBoundary.java");
        String begin = section(source, "private static OperationScope begin(ServerPlayer actor, Context context)",
                "public static void end(OperationScope");
        assertContinuity(begin);
        assertThrows(AssertionError.class, () -> assertContinuity(begin.replace(
                "continuity == null || !retainedCause", "false")));
        assertThrows(AssertionError.class, () -> assertContinuity(begin.replace(
                "credit.actor == actor && credit.owner == source", "true")));
        assertFalse(source.contains("ThreadLocal<P11QualifiedSourceOwner> ENGINEERING"));
        assertFalse(begin.contains("ENGINEERING") || begin.contains("getUUID()"));
        String engineering = section(source, "static <T> T engineering(", "/** Entered only after native predicates");
        assertTrue(engineering.contains("return nativeWork.get();"));
        assertFalse(engineering.contains(".set(") || engineering.contains("retainNativeRoot")
                || engineering.contains("nativeContinuity") || engineering.contains("new Binding"));
    }

    @Test void idleOrForeignNativeContextCannotSupplyDetachedBinding() throws Exception {
        String source = read("P11NativeOperationBoundary.java");
        String context = section(source, "private static Binding bindingInContext(",
                "public static List<OperationScope> beginCommandSources(");
        assertContextBinding(context);
        assertThrows(AssertionError.class, () -> assertContextBinding(context.replace(
                "if (!active) { return null; }", "")));
        assertThrows(AssertionError.class, () -> assertContextBinding(context.replace(
                "binding.owner == source && binding.recipient == actor", "binding.owner == source")));
        assertFalse(context.contains("nativeRecipient(") || context.contains("getUUID()")
                || context.contains("new Binding"));
        assertTrue(source.contains("context != null && context.exact == exact ? context : null"));
    }

    @Test void firstManagedBodyJoinsOnlyTheExistingAdmittedCallerCustody() throws Exception {
        String source = read("P11NativeStorageBoundary.java");
        String constructed = section(source, "public static void respawnConstructed(", "public static void place(");
        assertBefore(constructed, "P11LiveTransitionBoundary.expectedActor(player);", "var copy = COPY.get();");
        assertTrue(constructed.contains("copy.next.actor != player"));
        assertTrue(constructed.contains("P11LiveTransitionBoundary.adoptSourceBody(copy.next, copy.owner)"));
        String producer = section(read("mixin/P11PlayerListMixin.java"), "private ServerPlayer p11$actualRespawnBody(",
                "private void p11$respawnBodyComplete(");
        assertBefore(producer, "var next = original.call(exactServer, level, profile, information);",
                "P11NativeStorageBoundary.respawnConstructed(next);");
        String place = section(source, "public static void place(", "public static Optional<CompoundTag> load(");
        assertBefore(place, "var body = source.candidate(player);", "P11LiveTransitionBoundary.adoptSourceBody(body, source)");
        assertBefore(place, "P11LiveTransitionBoundary.adoptSourceBody(body, source)", "scope = new LoadScope(");
        String attach = section(read("P11QualifiedSourceOwner.java"), "ControlCustody attachControlCustody(", "void closeControlCustody(");
        assertTrue(attach.contains("body(body.actor) != body || body.fault != Fault.NONE"));
        assertTrue(attach.contains("retainNativeRoot(body, P11ControlBudgets.Root.TRANSITION)"));
        assertFalse(attach.contains("accounts.put(") || attach.contains("captureSource(") || attach.contains("tryAcquireRoot("));
    }

    @Test void controlCustodyAllocationPrecedesItsFirstRetainedRootMutation() throws Exception {
        String source = read("P11QualifiedSourceOwner.java");
        for (String body : new String[] {
                section(source, "ControlCustody beginControlCustody(", "/** Only the root's exact active factory ticket"),
                section(source, "ControlCustody attachControlCustody(", "void closeControlCustody(")}) {
            assertBefore(body, "var custody = new ControlCustody(this, body);",
                    "retainNativeRoot(body, P11ControlBudgets.Root.TRANSITION)");
            assertTrue(body.contains("return custody;"));
            assertFalse(body.contains("return new ControlCustody"));
        }
    }

    @Test void originalConfigurationCatchAndNativeFrameSendRemainInsideTheirOriginalBodies() throws Exception {
        String source = section(read("P11NativeStorageBoundary.java"), "public static void configurationFinished(",
                "/** A newly admitted parked attempt");
        assertTrue(source.contains("if (!listener.getMainThreadEventLoop().isSameThread()) { original.call(packet); return; }"));
        assertTrue(source.contains("try { P11LiveTransitionBoundary.configurationFinished(listener, packet, original); }"));
        assertTrue(source.contains("finally {\n            try { closeSelection(scope.selection); }"));
        assertFalse(source.contains("catch (") || source.contains("getPlayerForLogin(") || source.contains("placeNewPlayer("));
        String send = read("mixin/P11LiveCommonSendMixin.java");
        assertTrue(send.contains("@Mixin(ServerCommonPacketListenerImpl.class)"));
        assertTrue(send.contains("@WrapMethod(method = \"send(Lnet/minecraft/network/protocol/Packet;)V\", require = 1, expect = 1, allow = 1)"));
        assertTrue(send.contains("P11LiveTransitionBoundary.nativeSend((ServerCommonPacketListenerImpl) (Object) this, packet, original)"));
        assertFalse(send.contains("PacketSendListener") || send.contains("catch (") || send.contains(".send("));
    }


    @Test void concurrentConfigurationDepartureOnlyRetiresAnActuallyClosedExactConnection() throws Exception {
        String live = read("P11LiveTransitionService.java");
        assertClosedConfigurationDeparture(live);
        assertThrows(AssertionError.class, () -> assertClosedConfigurationDeparture(live.replace(
                "captured.isEmpty() && !connection.isConnected()", "captured.isEmpty()")));
        assertThrows(AssertionError.class, () -> assertClosedConfigurationDeparture(live.replace(
                "captured.isEmpty() && !entry.connection.isConnected()", "captured.isEmpty()")));
        assertThrows(AssertionError.class, () -> assertClosedConfigurationDeparture(live.replace(
                "Boolean.TRUE.equals(starting.remove(connection))", "starting.remove(connection) != null")));
        assertThrows(AssertionError.class, () -> assertClosedConfigurationDeparture(live.replace(
                "var bound = captured.orElseThrow();", "var bound = captured.orElse(null);")));
    }

    @Test void closedConfigurationCleanupDoesNotReplayBindingOrReleaseDataResponsibilities() throws Exception {
        String live = read("P11LiveTransitionService.java");
        String initial = section(live, "private void initializeConfiguration(", "private void service(");
        String ack = section(live, "private void completeConfigurationAcknowledgement(", "private void earlyTask(");
        assertEquals(1, initial.split("identities\\.bindAuthenticatedActorless\\(listener\\)", -1).length - 1);
        assertEquals(1, ack.split("identities\\.bindAuthenticatedActorless\\(config\\)", -1).length - 1);
        for (String body : new String[] {initial, ack}) {
            assertFalse(body.contains("catch (") || body.contains("retry(") || body.contains("halt("));
            assertFalse(body.contains("getPlayerForLogin(") || body.contains("placeNewPlayer(")
                    || body.contains("releaseNativeRoot(") || body.contains("sources."));
        }
        String retirement = section(live, "private void retire(Entry entry)", "void stop()");
        assertTrue(retirement.contains("entry.control.retire();") && retirement.contains("dispatcher.retire(entry.member);"));
        assertTrue(retirement.contains("identities.retireConnection(identity);"));
        assertTrue(retirement.contains("if (entry.waiting) { entry.waiting = false; waiting--; }"));
        assertFalse(retirement.contains("sources.") || retirement.contains("releaseRoot(")
                || retirement.contains("halt(") || retirement.contains("disconnect("));
        assertTrue(initial.contains("var member = dispatcher.register(identity.connectionEpoch()).orElseThrow();"),
                "dispatcher capacity/identity invariants are not converted into a departure");
    }

    @Test void exactRetirementDuringAQuantumCannotRequeueItOrRetireTheOtherConnection() {
        var dispatcher = new P11ControlBudgets.FairDispatcher(2, 2);
        var departed = dispatcher.register(1).orElseThrow();
        var peer = dispatcher.register(2).orElseThrow();
        assertTrue(dispatcher.eligible(departed));
        assertTrue(dispatcher.eligible(peer));
        var dispatch = dispatcher.poll(10).orElseThrow();
        assertEquals(1, dispatch.connectionId());
        assertTrue(dispatcher.retire(departed));
        assertFalse(dispatcher.complete(dispatch, true), "retired dispatch cannot regain executable membership");
        assertFalse(dispatcher.eligible(departed));
        assertFalse(dispatcher.retire(departed), "a second retirement cannot consume the peer's slot");
        assertEquals(1, dispatcher.members());
        var peerDispatch = dispatcher.poll(10).orElseThrow();
        assertEquals(2, peerDispatch.connectionId());
        assertTrue(dispatcher.complete(peerDispatch, false));
        assertEquals(1, dispatcher.members());
        assertEquals(0, dispatcher.queued());
    }

    private static void assertClosedConfigurationDeparture(String live) {
        String initial = section(live, "private void initializeConfiguration(", "private void service(");
        String initialBranch = section(initial, "var captured = identities.bindAuthenticatedActorless(listener);",
                "var control = new P11TransitionControl(");
        assertTrue(initialBranch.contains("if (captured.isEmpty() && !connection.isConnected())"));
        assertTrue(initialBranch.contains("synchronized (entries)"));
        assertTrue(initialBranch.contains("if (Boolean.TRUE.equals(starting.remove(connection))) { waiting--; }"));
        assertBefore(initialBranch, "starting.remove(connection)", "return;");
        assertBefore(initialBranch, "return;", "var identity = captured.orElseThrow();");
        String ack = section(live, "private void completeConfigurationAcknowledgement(", "private void earlyTask(");
        String ackBranch = section(ack, "var captured = identities.bindAuthenticatedActorless(config);",
                "if (!entry.control.bindActorlessHandoff(");
        assertTrue(ackBranch.contains("if (captured.isEmpty() && !entry.connection.isConnected())"));
        assertBefore(ackBranch, "retire(entry);", "return;");
        assertBefore(ackBranch, "return;", "var bound = captured.orElseThrow();");
    }

    private static void assertContinuity(String source) {
        assertTrue(source.contains("var continuity = P11LiveTransitionBoundary.nativeContinuity(source);"));
        assertTrue(source.contains("credit.actor == actor && credit.owner == source"));
        assertTrue(source.contains("source.nativeRecipient(credit.body) : source.nativeRecipient(actor)"));
        assertTrue(source.contains("source.nativeRecipient(binding.recipient) != binding.body"));
        assertTrue(source.contains("continuity == null || !retainedCause"));
    }

    private static void assertInactiveObservation(String source) {
        String server = section(source, "private static boolean observedInactive(MinecraftServer exact)",
                "private static boolean observedInactive(PlayerList list)");
        assertTrue(server.contains("current == null || exact == null || !exact.isSameThread()"));
        assertTrue(server.contains("var observed = current.startupState(exact).orElse(null);"));
        assertTrue(server.contains("return observed instanceof P11StartupLoadState.Invalid\n"
                + "                || observed instanceof P11StartupLoadState.Unavailable;"));
        assertFalse(server.contains("service()") || server.contains("new "));
        String list = section(source, "private static boolean observedInactive(PlayerList list)",
                "private static boolean observedInactive(ServerGamePacketListenerImpl listener)");
        assertTrue(list.contains("list != null && observedInactive(list.getServer())"));
        assertTrue(list.contains("list.getServer().getPlayerList() == list"));
        String listener = section(source, "private static boolean observedInactive(ServerGamePacketListenerImpl listener)",
                "static void ingress(");
        assertTrue(listener.contains("var exact = listener.player.getServer();"));
        assertTrue(listener.contains("observedInactive(exact) && listener.getMainThreadEventLoop() == exact"));
    }

    private static void assertContextBinding(String source) {
        assertTrue(source.contains("context == null || context.facts.terminal()"));
        assertTrue(source.contains("invocation.context == context"));
        assertTrue(source.contains("if (!active) { return null; }"));
        assertTrue(source.contains("binding.owner == source && binding.recipient == actor"));
    }

    private static P11QualifiedSourceOwner.ControlGate blocker(P11QualifiedSourceOwner.Account account) throws Exception {
        var method = P11QualifiedSourceOwner.class.getDeclaredMethod("nativeBlocker", P11QualifiedSourceOwner.Account.class);
        method.setAccessible(true);
        return (P11QualifiedSourceOwner.ControlGate) method.invoke(null, account);
    }

    private static void assertParked(String source) {
        assertTrue(source.contains("P11LiveTransitionBoundary.currentNativeAttempt(listener) == null"));
        assertTrue(source.contains("listener.getConnection().getPacketListener() != listener"));
        assertBefore(source, "throw unavailable();", "CONFIGURATION.set(scope);");
        assertTrue(source.contains("try { originalTail.run(); }\n        finally {"));
        assertTrue(source.contains("try { closeSelection(scope.selection); }\n            finally {"));
        assertTrue(source.contains("CONFIGURATION.remove();") && source.contains("CONFIGURATION.set(previous);"));
    }

    private static void assertBefore(String source, String first, String second) {
        int at = source.indexOf(first);
        assertTrue(at >= 0 && source.indexOf(second) > at, first + " before " + second);
    }

    private static String section(String source, String start, String end) {
        return source.substring(source.indexOf(start), source.indexOf(end, source.indexOf(start)));
    }

    private static String read(String relative) throws Exception {
        var directory = Path.of("").toAbsolutePath().normalize();
        while (directory != null && !Files.isRegularFile(directory.resolve("build.gradle"))) { directory = directory.getParent(); }
        if (directory == null) { throw new IllegalStateException("project root not found"); }
        return Files.readString(directory.resolve("src/main/java/com/yo1no/gramarye").resolve(relative));
    }
}
