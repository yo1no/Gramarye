package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import org.junit.jupiter.api.Test;

/** Pinned packet exception policy without any player, authentication, or native-body fixture. */
final class P11LiveNativeErrorPolicyTest {
    @Test void successfulNativeCallReturnsTrueWithoutErrorCallback() throws Throwable {
        var listener = new Listener();
        assertTrue(invoke(listener, () -> { }));
        assertNull(listener.failure);
    }

    @Test void handledExceptionStillReportsUnsuccessfulNativeCaller() throws Throwable {
        var listener = new Listener();
        var primary = new IllegalStateException("native fixture");
        assertFalse(invoke(listener, () -> { throw primary; }));
        assertSame(primary, listener.failure);
        assertSame(packet, listener.packet);
    }

    @Test void rawErrorPropagatesWithoutBeingRoutedAsException() {
        var listener = new Listener();
        var primary = new AssertionError("native fixture");
        assertSame(primary, assertThrows(AssertionError.class, () -> invoke(listener, () -> { throw primary; })));
        assertNull(listener.failure);
    }

    @Test void reportedOutOfMemoryIsRethrownWithOriginalCrashOwner() {
        var listener = new Listener();
        var primary = new ReportedException(CrashReport.forThrowable(new OutOfMemoryError("fixture"), "fixture"));
        assertSame(primary, assertThrows(ReportedException.class, () -> invoke(listener, () -> { throw primary; })));
        assertNull(listener.failure);
    }

    @Test void reportedNonOomUsesTheOriginalListenerPolicy() throws Throwable {
        var listener = new Listener();
        var primary = new ReportedException(CrashReport.forThrowable(new IllegalStateException("fixture"), "fixture"));
        assertFalse(invoke(listener, () -> { throw primary; }));
        assertSame(primary, listener.failure);
    }

    @Test void listenerErrorPolicyFailureRemainsTheEscapingPrimary() {
        var listener = new Listener();
        var bodyFailure = new IllegalStateException("body");
        listener.policyFailure = new IllegalArgumentException("policy");
        assertSame(listener.policyFailure, assertThrows(IllegalArgumentException.class,
                () -> invoke(listener, () -> { throw bodyFailure; })));
        assertSame(bodyFailure, listener.failure);
    }

    @Test void terminalChecksAndCleanupCannotClaimCompleteFromBodyReturnAlone() throws Exception {
        String source = read("P11LiveTransitionService.java");
        String finish = section(source, "private void finish(", "private Throwable recordTerminalFailure(");
        assertTrue(finish.contains("sources.canCopy(body)"));
        assertTrue(finish.contains("boolean recorded = ticket.entry.control.callerCompleted"));
        assertTrue(finish.contains("if (!recorded)"));
        assertTrue(finish.contains("state.outcome() != Outcome.COMPLETED"));
        assertTrue(finish.contains("state.targetActorGeneration() != ticket.expectedIdentity.actorGeneration()"));
        assertTrue(finish.contains("if (primary == null && terminalFailure != null)"));
        assertTrue(finish.contains("ticket.closed = true;"));
        assertTrue(finish.contains("sources.closeControlCustody(ticket.custody)"));
        assertTrue(finish.contains("releaseCompletedDrain()"));
        String config = section(source, "void configurationFinished(", "boolean beforeConfigurationFactory(");
        assertTrue(config.contains("catch (RuntimeException | Error failure) { primary = failure; throw failure; }"));
        assertTrue(config.contains("finish(scope.ticket, normal, primary)"));
        String enter = section(source, "void switchToConfig(", "Ticket current(");
        assertTrue(enter.indexOf("notifyLatest(entry); // Publish") < enter.indexOf("ticket = admit("));
    }

    @Test void frameObservationNamesOnlyTheUniqueNativeConstructedPacketObjects() throws Exception {
        String source = read("mixin/P11PlayerListMixin.java");
        assertTrue(source.contains("(IZLjava/util/Set;IIIZZZLnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;Z)Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;"));
        assertTrue(source.contains("(Lnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;B)Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;"));
        assertTrue(source.contains("P11LiveTransitionBoundary.expectedNativeFrame((PlayerList) (Object) this, packet);\n        return packet;"));
        assertFalse(source.contains("new net.minecraft.network.protocol.game.ClientboundLoginPacket")
                || source.contains("new net.minecraft.network.protocol.game.ClientboundRespawnPacket"));
    }

    @Test void schedulingFailureCannotBeReplacedByAnUnknownReceiptObserverFailure() throws Exception {
        String source = read("P11LiveTransitionService.java");
        String submit = section(source, "private void submitPump(", "private void pumpQueued(");
        assertTrue(submit.contains("server.tell(new TickTask(server.getTickCount(), queuedPump))"));
        assertFalse(submit.contains("server.execute("));
        assertTrue(submit.contains("catch (RuntimeException | Error failure)"));
        assertTrue(submit.indexOf("stopping = true;") < submit.indexOf("wakeup.retire()"));
        assertTrue(submit.contains("catch (RuntimeException | Error secondary) { recordTerminalFailure(null, secondary); }"));
        assertTrue(submit.contains("throw failure;"));
        assertFalse(submit.contains("throw secondary;"));
    }

    @Test void admissionFailureContainsGateCustodyAndTicketAllocationWithoutReplacingPrimary() throws Exception {
        String source = section(read("P11LiveTransitionService.java"), "private Ticket admit(", "private void execute(");
        assertAdmissionFailure(source);
        assertThrows(AssertionError.class, () -> assertAdmissionFailure(source.replace(
                "var gate = gate(entry);", "")));
        assertThrows(AssertionError.class, () -> assertAdmissionFailure(source.replace(
                "sources.closeControlCustody(custody);", "")));
        assertThrows(AssertionError.class, () -> assertAdmissionFailure(source.replace(
                "throw primary;", "return null;")));
    }

    @Test void postAdmissionCallAllocationIsInsideBothNativeCleanupScopes() throws Exception {
        String source = read("P11LiveTransitionService.java");
        for (String body : java.util.List.of(section(source, "private void execute(", "void configurationFinished("),
                section(source, "void switchToConfig(", "Ticket current("))) {
            assertProtectedCallAllocation(body);
            assertThrows(AssertionError.class, () -> assertProtectedCallAllocation(body.replace(
                    "try {\n            var scope = new Call(listener, previous);",
                    "var scope = new Call(listener, previous);\n        try {")));
            assertThrows(AssertionError.class, () -> assertProtectedCallAllocation(body.replace(
                    "finish(ticket, normal, primary)", "unprotectedFinish(ticket)")));
        }
    }

    @Test void pumpResubmissionCannotReplaceAnAlreadyEscapingNativePrimary() throws Exception {
        String body = section(read("P11LiveTransitionService.java"), "private void pump()", "private void prepareAndDispatch(");
        assertPrimaryPreservingCleanup(body);
        assertTrue(body.contains("wakeup.complete(!stopping && dispatcher.budgetRemaining(tick))"));
        assertTrue(body.contains("submitPump();"));
        assertThrows(AssertionError.class, () -> assertPrimaryPreservingCleanup(body.replace(
                "if (primary == null) { throw secondary; }", "throw secondary;")));
        assertThrows(AssertionError.class, () -> assertPrimaryPreservingCleanup(body.replace(
                "primary = failure;", "primary = null;")));
    }

    @Test void failedDispatcherRequeueRetiresExecutionWithoutReplacingNativePrimary() throws Exception {
        String body = section(read("P11LiveTransitionService.java"), "private void prepareAndDispatch(",
                "private void initializeConfiguration(");
        assertPrimaryPreservingCleanup(body);
        assertTrue(body.contains("try { dispatcher.complete(dispatch, more); }"));
        int failure = body.indexOf("catch (RuntimeException | Error secondary)");
        String cleanup = body.substring(failure);
        assertTrue(cleanup.contains("stopping = true;"));
        assertTrue(cleanup.contains("wakeup.retire();"));
        assertTrue(cleanup.contains("dispatcher.retireSlot();"));
        assertFalse(cleanup.contains("notifyLatest(") || cleanup.contains("sources.") || cleanup.contains("disconnect("));
        assertThrows(AssertionError.class, () -> assertPrimaryPreservingCleanup(body.replace(
                "if (primary == null) { throw secondary; }", "throw secondary;")));
    }

    private static void assertProtectedCallAllocation(String body) {
        int admission = body.indexOf("ticket = admit(");
        int previous = body.indexOf("var previous = call;", admission);
        int protectedStart = body.indexOf("try {", previous);
        int allocation = body.indexOf("var scope = new Call(listener, previous);", previous);
        assertTrue(admission >= 0 && previous > admission && protectedStart > previous && allocation > protectedStart);
        assertTrue(body.contains("finish(ticket, normal, primary)"));
        assertTrue(body.contains("finally { call = previous; }"));
    }

    private static void assertPrimaryPreservingCleanup(String body) {
        assertTrue(body.contains("Throwable primary = null;"));
        assertTrue(body.contains("catch (RuntimeException | Error failure)"));
        assertTrue(body.contains("primary = failure;") && body.contains("throw failure;"));
        assertTrue(body.contains("catch (RuntimeException | Error secondary)"));
        assertTrue(body.contains("if (primary == null) { throw secondary; }"));
        assertTrue(body.contains("recordTerminalFailure(null, secondary);"));
    }

    @Test void earlyConfigurationTaskContainsItsSourceGateAndReleasesAnAlreadyUnknownDrain() throws Exception {
        String source = section(read("P11LiveTransitionService.java"), "private void earlyTask(",
                "private P11TransitionControl.Gate gate(");
        assertTrue(source.indexOf("try {") < source.indexOf("gate(entry)"));
        assertTrue(source.contains("!entry.control.finishDrain(drain) && !entry.control.abortAdmission(drain)"));
        assertTrue(source.contains("if (pass && entry.connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl config)"));
        String cleanup = source.substring(source.indexOf("catch (RuntimeException | Error primary)"));
        assertTrue(cleanup.contains("if (!entry.control.abortAdmission(drain)) { entry.control.observationLost(); }"));
        assertTrue(cleanup.contains("catch (RuntimeException | Error secondary) { recordTerminalFailure(null, secondary); }"));
        assertTrue(cleanup.contains("throw primary;"));
        assertFalse(cleanup.contains("completeTask(") || cleanup.contains("return;") || cleanup.contains("onPacketError("));
    }

    private static void assertAdmissionFailure(String source) {
        int scope = source.indexOf("try {");
        int gate = source.indexOf("var gate = gate(entry);");
        int acquire = source.indexOf("custody = sources.beginControlCustody(entry.playerId);");
        int ticket = source.indexOf("return new Ticket(this, entry, caller, attempt, drain.request().kind(), custody);");
        int failure = source.indexOf("catch (RuntimeException | Error primary)");
        assertTrue(scope >= 0 && gate > scope && acquire > gate && ticket > acquire && failure > ticket);
        assertTrue(source.contains("!entry.control.finishDrain(drain) && !entry.control.abortAdmission(drain)"));
        String cleanup = source.substring(failure);
        assertTrue(cleanup.contains("if (!entry.control.abortAdmission(drain)) { entry.control.observationLost(); }"));
        assertTrue(cleanup.contains("try { sources.closeControlCustody(custody); }"));
        assertEquals(2, cleanup.split(java.util.regex.Pattern.quote(
                "catch (RuntimeException | Error secondary) { recordTerminalFailure(null, secondary); }"), -1).length - 1);
        assertTrue(cleanup.contains("throw primary;"));
        assertFalse(cleanup.contains("return null;") || cleanup.contains("onPacketError(") || cleanup.contains("notifyLatest("));
    }

    private static final Packet<?> packet = new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN);

    private static boolean invoke(Listener listener, Runnable body) throws Throwable {
        var method = P11LiveTransitionService.class.getDeclaredMethod("runPacketBody", Packet.class, PacketListener.class, Runnable.class);
        method.setAccessible(true);
        try { return (boolean) method.invoke(null, packet, listener, body); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    private static final class Listener implements PacketListener {
        private Packet<?> packet;
        private Exception failure;
        private RuntimeException policyFailure;
        @Override public PacketFlow flow() { return PacketFlow.SERVERBOUND; }
        @Override public ConnectionProtocol protocol() { return ConnectionProtocol.PLAY; }
        @Override public void onDisconnect(DisconnectionDetails details) { }
        @Override public boolean isAcceptingMessages() { return true; }
        @Override public void onPacketError(Packet packet, Exception failure) {
            this.packet = packet;
            this.failure = failure;
            if (policyFailure != null) { throw policyFailure; }
        }
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

    @Test void terminalNativeSendsMarkOnlyExactOwnedMainCallsBeforeOriginalScheduling() throws Exception {
        String body = section(read("P11LiveTransitionService.java"), "void nativeSend(", "private void finish(");
        assertTerminalNativeMarking(body);
        for (String guard : java.util.List.of("server.isSameThread()", "listener.getMainThreadEventLoop() == server",
                "listener.getConnection().getPacketListener() == listener", "entry.control.configurationTaskPassed()",
                "net.minecraft.server.network.config.JoinWorldTask.TYPE.equals(access.p11$currentConfigurationTask())",
                "listener == ticket.caller", "ticket.entry.connection.getPacketListener() == listener")) {
            assertThrows(AssertionError.class, () -> assertTerminalNativeMarking(body.replace(guard, "true")));
        }
        assertThrows(AssertionError.class, () -> assertTerminalNativeMarking(body.replace(
                "entry.terminalInterval = TerminalInterval.TO_PLAY;", "")));
        assertThrows(AssertionError.class, () -> assertTerminalNativeMarking(body.replace(
                "ticket.entry.terminalInterval = TerminalInterval.TO_CONFIG;", "")));
    }

    @Test void deliveryHoldClearsOnlyAfterExistingValidatedPhaseHandoffs() throws Exception {
        String source = read("P11LiveTransitionService.java");
        String ack = section(source, "private void completeConfigurationAcknowledgement(", "private void earlyTask(");
        String factory = section(source, "boolean beforeConfigurationFactory(", "void prepareParking(");
        assertTerminalClearOrder(ack, factory);
        assertThrows(AssertionError.class, () -> assertTerminalClearOrder(ack.replace(
                "entry.control.completeHandoff(handoff).isEmpty()", "false"), factory));
        assertThrows(AssertionError.class, () -> assertTerminalClearOrder(ack, factory.replace(
                "entry.control.completeHandoff(entry.handoff).orElseThrow();", "")));
        assertThrows(AssertionError.class, () -> assertTerminalClearOrder(ack, factory.replace(
                "if (entry.terminalInterval == TerminalInterval.TO_PLAY) { entry.terminalInterval = TerminalInterval.NONE; }",
                "")));
        String callback = section(source, "void configurationFinished(", "boolean beforeConfigurationFactory(");
        assertFalse(callback.contains("terminalInterval ="));
        assertTrue(callback.contains("catch (RuntimeException | Error failure) { primary = failure; throw failure; }"));
        String network = section(source, "void ingress(", "void configurationAcknowledged(");
        assertFalse(network.contains("terminalInterval"));
        assertFalse(section(source, "void configurationAcknowledged(", "P11LiveTransitionBoundary.TaskDecision taskStarted(")
                .contains("terminalInterval"));
    }

    @Test void blockedDeliveryCannotConsumeLatestOrSelfRequeueNotificationOnlyWork() throws Exception {
        String source = read("P11LiveTransitionService.java");
        String notify = section(source, "private void notifyLatest(", "private Entry requiredEntry(");
        assertTerminalNotificationGuard(notify);
        assertThrows(AssertionError.class, () -> assertTerminalNotificationGuard(notify.replace(
                "if (entry.terminalInterval != TerminalInterval.NONE) { return; }", "")));
        assertEquals(3, source.split(java.util.regex.Pattern.quote(
                "entry.control.hasService(entry.terminalInterval == TerminalInterval.NONE)"), -1).length - 1);
        assertTrue(source.contains("entry.control.nextService(entry.terminalInterval == TerminalInterval.NONE)"));
        assertTrue(source.contains("if (entry.closeReason != null) {"));
        assertTrue(source.contains("disconnect(entry, reason);"));
        var interval = java.util.Arrays.stream(P11LiveTransitionService.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("TerminalInterval")).findFirst().orElseThrow();
        assertTrue(java.lang.reflect.Modifier.isPrivate(interval.getModifiers()));
        assertTrue(interval.isEnum());
        assertEquals(java.util.List.of("NONE", "TO_CONFIG", "TO_PLAY"),
                java.util.Arrays.stream(interval.getEnumConstants()).map(Object::toString).toList());
        assertFalse(java.util.Arrays.stream(P11LiveTransitionService.class.getDeclaredMethods())
                .anyMatch(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers())));
    }

    private static void assertTerminalNotificationGuard(String body) {
        int guard = body.indexOf("if (entry.terminalInterval != TerminalInterval.NONE) { return; }");
        int take = body.indexOf("takeNotification(logicalListener)");
        assertTrue(guard >= 0 && take > guard);
    }

    private static void assertTerminalNativeMarking(String body) {
        for (String guard : java.util.List.of("server.isSameThread()", "listener.getMainThreadEventLoop() == server",
                "listener.getConnection().getPacketListener() == listener", "entry.control.configurationTaskPassed()",
                "net.minecraft.server.network.config.JoinWorldTask.TYPE.equals(access.p11$currentConfigurationTask())",
                "listener == ticket.caller", "ticket.entry.connection.getPacketListener() == listener")) {
            assertTrue(body.contains(guard), guard);
        }
        int finish = body.indexOf("entry.terminalInterval = TerminalInterval.TO_PLAY;");
        int noTicket = body.indexOf("call == null || call.ticket == null");
        assertTrue(finish >= 0 && noTicket > finish);
        int start = body.indexOf("ticket.entry.terminalInterval = TerminalInterval.TO_CONFIG;");
        int nativeCall = body.indexOf("original.call(packet);", start);
        int observed = body.indexOf("startConfigurationObserved(ticket.attempt)");
        assertTrue(start >= 0 && nativeCall > start && observed > nativeCall);
        assertFalse(body.contains("catch (") || body.contains("synchronized (") || body.contains("pipeline()"));
    }

    private static void assertTerminalClearOrder(String ack, String factory) {
        int ackValidated = ack.indexOf("entry.control.completeHandoff(handoff).isEmpty()");
        int ackClear = ack.indexOf("if (entry.terminalInterval == TerminalInterval.TO_CONFIG)");
        assertTrue(ackValidated >= 0 && ackClear > ackValidated && ack.indexOf("notifyLatest(entry)") > ackClear);
        int playValidated = factory.indexOf("entry.control.completeHandoff(entry.handoff).orElseThrow();");
        int playClear = factory.indexOf("if (entry.terminalInterval == TerminalInterval.TO_PLAY)");
        assertTrue(playValidated >= 0 && playClear > playValidated && factory.indexOf("admit(entry, drain, listener)") > playClear);
    }
}
