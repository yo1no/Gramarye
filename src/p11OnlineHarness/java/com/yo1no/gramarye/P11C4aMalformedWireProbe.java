package com.yo1no.gramarye;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.neoforge.network.connection.ConnectionUtils;

/** Excluded exact native codec call scopes. Never constructs a packet, supplies a receiver result or bypasses a sender. */
public final class P11C4aMalformedWireProbe {
    private static volatile Run active;
    private static final ThreadLocal<Frame> FRAME = new ThreadLocal<>();
    private static final ThreadLocal<FailureCall> FAILURES = new ThreadLocal<>();
    private P11C4aMalformedWireProbe() { }
    static boolean selected() { return switch (P11C4aScenario.MODE) {
        case MALFORMED_CONFIG_REQUEST, MALFORMED_PLAY_REQUEST, MALFORMED_CONFIG_STATE, MALFORMED_PLAY_STATE -> true;
        default -> false;
    }; }
    static boolean config() { return P11C4aScenario.MODE == P11C4aScenario.Mode.MALFORMED_CONFIG_REQUEST
            || P11C4aScenario.MODE == P11C4aScenario.Mode.MALFORMED_CONFIG_STATE; }
    static boolean request() { return P11C4aScenario.MODE == P11C4aScenario.Mode.MALFORMED_CONFIG_REQUEST
            || P11C4aScenario.MODE == P11C4aScenario.Mode.MALFORMED_PLAY_REQUEST; }
    static void arm(Connection exact, boolean server) {
        P11C4aEvidence.require(selected() && active == null && exact != null && exact.isConnected()
                && exact.isEncrypted() && !exact.isMemoryConnection(), "MALFORMED_EXACT_ENCRYPTED_ARM");
        active = new Run(exact, server);
    }
    static void enable() { var run = active; P11C4aEvidence.require(run != null, "MALFORMED_ENABLE_OWNER"); run.enabled = true; }
    public static final class Frame {
        final Run run; final Frame previous; final boolean encode; final int kind;
        boolean selectedDecode;
        Frame(Run run, Frame previous, boolean encode, int kind) { this.run = run; this.previous = previous; this.encode = encode; this.kind = kind; }
    }
    public static Frame begin(ChannelHandlerContext context, ConnectionProtocol protocol, Packet<?> packet, boolean encode) {
        var run = active;
        if (run == null || !run.enabled || ConnectionUtils.getConnection(context) != run.connection
                || protocol != (config() ? ConnectionProtocol.CONFIGURATION : ConnectionProtocol.PLAY)) { return null; }
        int kind = packet instanceof ServerboundCustomPayloadPacket custom && custom.payload() instanceof P11TransitionRequestPayload ? 36
                : packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof P11TransitionStatePayload ? 54 : 0;
        if (encode && kind != (request() ? 36 : 54)) { return null; }
        var frame = new Frame(run, FRAME.get(), encode, kind); FRAME.set(frame); return frame;
    }
    public static void end(Frame frame, Throwable originalFailure) {
        if (frame == null) { return; }
        if (FRAME.get() != frame) { frame.run.failure = "MALFORMED_FRAME_SCOPE_ORDER"; return; }
        if (frame.previous == null) { FRAME.remove(); } else { FRAME.set(frame.previous); }
        synchronized (frame.run) {
            if (frame.selectedDecode) {
                frame.run.packetFailureCategory = category(originalFailure);
                frame.run.packetCauseCategory = category(originalFailure == null ? null : originalFailure.getCause());
                frame.run.packetGrandCauseCategory = category(originalFailure == null || originalFailure.getCause() == null
                        ? null : originalFailure.getCause().getCause());
                frame.run.packetCauseIsWire = originalFailure != null && originalFailure.getCause() == frame.run.thrown;
                frame.run.packetGrandCauseIsWire = originalFailure != null && originalFailure.getCause() != null
                        && originalFailure.getCause().getCause() == frame.run.thrown;
                // Pinned native codec adds two original wrappers: CustomPacketPayload RuntimeException,
                // then IdDispatchCodec DecoderException. Preserve these; do not unwrap/rethrow the inner one.
                if (originalFailure instanceof DecoderException && originalFailure.getCause() != null
                        && originalFailure.getCause().getClass() == RuntimeException.class
                        && originalFailure.getCause().getCause() == frame.run.thrown) {
                    frame.run.packetThrown = originalFailure; frame.run.packetThrows++;
                } else { frame.run.failure = "MALFORMED_NATIVE_DECODER_WRAPPER_CHAIN"; }
            }
        }
    }
    public static void encoded(FriendlyByteBuf buffer, Object value, int start, int length) {
        var frame = FRAME.get();
        if (frame == null || !frame.encode || frame.kind != length) { return; }
        var run = frame.run;
        synchronized (run) {
            if (run.server == request() || run.corruptions != 0 || !target(value)) { return; }
            if (buffer.writerIndex() - start != length || buffer.getUnsignedByte(start) != 1) {
                run.failure = "MALFORMED_ORIGINAL_ENCODER_SHAPE"; return;
            }
            run.tuple = tuple(buffer, start, length);
            // After the genuine product encoder returned; preserve body length and outer packet framing.
            buffer.setByte(start, 255);
            run.corruptions++;
        }
    }
    private static boolean target(Object value) {
        if (value instanceof P11TransitionProtocol.Request request) {
            return config() ? request.scope() == P11TransitionProtocol.Scope.CONFIG
                    && request.kind() == P11TransitionProtocol.Kind.RETURN_TO_WORLD && request.command() == P11TransitionProtocol.Command.STATUS
                    : request.scope() == P11TransitionProtocol.Scope.PLAY && request.kind() == P11TransitionProtocol.Kind.DEATH
                    && request.command() == P11TransitionProtocol.Command.TRY;
        }
        if (value instanceof P11TransitionProtocol.State state) {
            return config() ? state.scope() == P11TransitionProtocol.Scope.CONFIG
                    && state.kind() == P11TransitionProtocol.Kind.RETURN_TO_WORLD
                    && state.outcome() == P11TransitionProtocol.Outcome.NOT_STARTED
                    && state.reason() == P11TransitionProtocol.Reason.ACTIVE_OPERATION
                    : state.scope() == P11TransitionProtocol.Scope.PLAY && state.kind() == P11TransitionProtocol.Kind.DEATH
                    && state.outcome() == P11TransitionProtocol.Outcome.BINDING;
        }
        return false;
    }
    /** Called inside the original installed P11 codec, not from an arbitrary raw buffer. */
    public static boolean decodeEntered(FriendlyByteBuf buffer, int length) {
        var frame = FRAME.get();
        if (frame == null || frame.encode || frame.run.server != request()
                || length != (request() ? 36 : 54) || buffer.readableBytes() != length
                || buffer.getUnsignedByte(buffer.readerIndex()) != 255) { return false; }
        synchronized (frame.run) {
            frame.selectedDecode = true;
            frame.run.tuple = tuple(buffer, buffer.readerIndex(), length);
            frame.run.decodeEntries++;
            return true;
        }
    }
    public static void decodeEnded(boolean selected, Throwable sameOriginal) {
        if (!selected) { return; }
        var frame = FRAME.get();
        if (frame == null || !frame.selectedDecode) { return; }
        synchronized (frame.run) {
            if (sameOriginal instanceof DecoderException) { frame.run.thrown = sameOriginal; frame.run.wireThrows++; }
            else { frame.run.normalDecodes++; frame.run.failure = "MALFORMED_CODEC_DID_NOT_THROW_DECODER_EXCEPTION"; }
        }
    }
    public static final class FailureCall {
        private final Run run; private final FailureCall previous;
        private final boolean selected, secondary;
        private boolean closed;
        private FailureCall(Run run, FailureCall previous, boolean selected, boolean secondary) {
            this.run = run; this.previous = previous; this.selected = selected; this.secondary = secondary;
        }
    }
    static boolean secondaryAllowed(boolean samePacket, boolean handlingFault, int primaryEntries,
            int primaryReturns, boolean primaryInStack, boolean stackSameRun, long secondaryEntries) {
        return !samePacket && handlingFault && primaryEntries == 1 && primaryReturns >= 0 && primaryReturns <= 1
                && (primaryReturns == 1 || primaryInStack) && stackSameRun && secondaryEntries >= 0 && secondaryEntries < Long.MAX_VALUE;
    }
    static boolean secondaryTerminal(int openCalls, long entries, long normalReturns) {
        return openCalls == 0 && entries >= 0 && entries == normalReturns;
    }
    static boolean secondarySample(long ordinal) { return ordinal > 0 && ordinal <= 4; }
    static boolean terminalFaults(String failure, int downstream, int openCalls, long entries, long normalReturns,
            long invalidEntries, long invalidReturns, int openInvalid, long nonNormal) {
        return failure == null && downstream == 0 && secondaryTerminal(openCalls, entries, normalReturns)
                && invalidEntries == 0 && invalidReturns == 0 && openInvalid == 0 && nonNormal == 0;
    }
    static long checkedNext(long value) {
        if (value < 0) { throw new IllegalStateException("MALFORMED_NEGATIVE_OBSERVATION_COUNT"); }
        return Math.incrementExact(value);
    }
    public static FailureCall exceptionEntered(Connection connection, Throwable sameOriginal, boolean handlingFault) {
        var run = active;
        if (run == null || connection != run.connection || run.server != request() || run.packetThrown == null) { return null; }
        var previous = FAILURES.get();
        synchronized (run) {
            // Stop's final snapshot/release has the same monitor linearization as fault entry.
            if (active != run) { return null; }
            boolean samePacket = sameOriginal == run.packetThrown;
            boolean primaryInStack = false, stackSameRun = true;
            int depth = 0;
            for (var item = previous; item != null; item = item.previous) {
                if (++depth > 4 || item.run != run || item.closed) { stackSameRun = false; break; }
                primaryInStack |= item.selected;
            }
            boolean selected = samePacket && !handlingFault && run.exceptionEntries == 0 && previous == null;
            boolean secondary = secondaryAllowed(samePacket, handlingFault, run.exceptionEntries,
                    run.exceptionReturns, primaryInStack, stackSameRun, run.secondaryEntries);
            if (selected) {
                run.exceptionFailureCategory = category(sameOriginal);
                run.exceptionIsPacket = true;
                run.exceptionCauseIsPacket = sameOriginal.getCause() == run.packetThrown;
                run.exceptionEntries++;
            } else if (secondary) {
                run.secondaryEntries = checkedNext(run.secondaryEntries);
                if (sameOriginal instanceof java.nio.channels.ClosedChannelException) {
                    run.closedChannelSecondaryEntries = checkedNext(run.closedChannelSecondaryEntries);
                }
                // Four detail samples only. Every later native invocation remains checked/counted.
                if (secondarySample(run.secondaryEntries)) {
                    run.secondaryFaults.add(java.util.Map.of("ordinal", run.secondaryEntries,
                            "category", category(sameOriginal), "nativeHandlingFaultBefore", true,
                            "selectedPrimaryReturnedBefore", run.exceptionReturns == 1,
                            "selectedPrimaryInCallStack", primaryInStack, "observerDepth", depth + 1,
                            "causeIsSelectedPacket", sameOriginal != null && sameOriginal.getCause() == run.packetThrown));
                }
            } else {
                run.failure = "MALFORMED_NATIVE_EXCEPTION_ORDER_OR_POLICY";
                run.invalidFaultEntries = checkedNext(run.invalidFaultEntries);
                run.openInvalidFaultCalls = Math.incrementExact(run.openInvalidFaultCalls);
                // Invalid entry is still observed through its original call-local normal return.
                // It is not installed in the already bounded valid-scope ThreadLocal chain.
                return new FailureCall(run, null, false, false);
            }
            var token = new FailureCall(run, previous, selected, secondary);
            FAILURES.set(token); run.openFailureCalls++;
            return token;
        }
    }
    public static void exceptionEnded(FailureCall token, boolean normal) {
        if (token == null) { return; }
        synchronized (token.run) {
            if (!token.selected && !token.secondary) {
                if (token.closed) { token.run.failure = "MALFORMED_NATIVE_EXCEPTION_SCOPE_ORDER"; return; }
                token.closed = true; token.run.openInvalidFaultCalls--;
                if (normal) { token.run.invalidFaultReturns = checkedNext(token.run.invalidFaultReturns); }
                else { token.run.nonNormalFaultCalls = checkedNext(token.run.nonNormalFaultCalls); }
                return;
            }
            if (FAILURES.get() != token || token.closed) {
                token.run.failure = "MALFORMED_NATIVE_EXCEPTION_SCOPE_ORDER"; return;
            }
            token.closed = true;
            if (token.previous == null) { FAILURES.remove(); } else { FAILURES.set(token.previous); }
            token.run.openFailureCalls--;
            if (!normal) {
                token.run.nonNormalFaultCalls = checkedNext(token.run.nonNormalFaultCalls);
                token.run.failure = "MALFORMED_NATIVE_EXCEPTION_HANDLER_DID_NOT_RETURN"; return;
            }
            if (token.selected) { token.run.exceptionReturns++; }
            if (token.secondary) { token.run.secondaryReturns = checkedNext(token.run.secondaryReturns); }
        }
    }
    public static void exceptionObservationFailed() {
        var run = active;
        if (run != null) { run.failure = "MALFORMED_NATIVE_EXCEPTION_OBSERVER_FAILED"; }
    }
    public static void downstream(Object value, Connection connection) {
        var run = active;
        if (run == null || connection != run.connection || run.server != request()) { return; }
        var tuple = run.tuple;
        if (tuple != null && tuple.matches(value)) { synchronized (run) { run.downstream++; } }
    }
    static boolean terminalReady() {
        var run = active;
        if (run == null || run.connection.isConnected()) { return false; }
        synchronized (run) {
            P11C4aEvidence.require(run.failure == null && run.downstream == 0, "MALFORMED_OBSERVER_OR_DOWNSTREAM");
            P11C4aEvidence.require(terminalFaults(run.failure, run.downstream, run.openFailureCalls,
                            run.secondaryEntries, run.secondaryReturns, run.invalidFaultEntries,
                            run.invalidFaultReturns, run.openInvalidFaultCalls, run.nonNormalFaultCalls),
                    "MALFORMED_SECONDARY_HANDLER_NOT_TERMINAL");
            boolean receive = run.server == request();
            P11C4aEvidence.require(receive ? run.decodeEntries == 1 && run.wireThrows == 1 && run.packetThrows == 1
                    && run.normalDecodes == 0 && run.exceptionEntries == 1 && run.exceptionReturns == 1 && run.corruptions == 0
                    : run.corruptions == 1 && run.decodeEntries == 0, "MALFORMED_NATIVE_CODEC_COUNTS");
            return true;
        }
    }
    static Map<String, Object> report() {
        var run = active; P11C4aEvidence.require(run != null, "MALFORMED_REPORT_OWNER");
        synchronized (run) {
            var values = new java.util.LinkedHashMap<String, Object>();
            values.put("status", "EXACT_ORIGINAL_WIRE_FAULT_OBSERVATION_NOT_FULL_ACCEPTANCE");
            values.put("mode", P11C4aScenario.MODE.name()); values.put("side", run.server ? "SERVER" : "CLIENT_A");
            values.put("bodyBytes", request() ? 36 : 54); values.put("corruptions", run.corruptions);
            values.put("decodeEntries", run.decodeEntries); values.put("sameOriginalWireThrows", run.wireThrows);
            values.put("sameOriginalPacketDecoderThrows", run.packetThrows); values.put("normalDecodes", run.normalDecodes);
            values.put("nativeCodecWrapperDepth", run.packetThrown == null ? 0 : 2);
            values.put("nativeExceptionEntries", run.exceptionEntries); values.put("nativeExceptionReturns", run.exceptionReturns);
            values.put("matchingDownstream", run.downstream); values.put("connected", run.connection.isConnected());
            values.put("failureCategory", run.thrown == null ? "NOT_RECEIVER" : "DECODER_EXCEPTION");
            values.put("observerFailure", run.failure == null ? "NONE" : run.failure);
            values.put("packetFailureCategory", run.packetFailureCategory);
            values.put("packetCauseCategory", run.packetCauseCategory);
            values.put("packetGrandCauseCategory", run.packetGrandCauseCategory);
            values.put("packetCauseIsWire", run.packetCauseIsWire);
            values.put("packetGrandCauseIsWire", run.packetGrandCauseIsWire);
            values.put("exceptionFailureCategory", run.exceptionFailureCategory);
            values.put("exceptionIsPacket", run.exceptionIsPacket);
            values.put("exceptionCauseIsPacket", run.exceptionCauseIsPacket);
            values.put("secondaryNativeFaultEntries", run.secondaryEntries);
            values.put("secondaryNativeFaultNormalReturns", run.secondaryReturns);
            values.put("openNativeFaultCalls", run.openFailureCalls);
            values.put("secondaryNativeFaults", java.util.List.copyOf(run.secondaryFaults));
            values.put("secondaryDetailSampleLimit", 4);
            values.put("secondaryDetailSamples", run.secondaryFaults.size());
            values.put("secondaryDetailsNotRetained", run.secondaryEntries - run.secondaryFaults.size());
            values.put("closedChannelSecondaryEntries", run.closedChannelSecondaryEntries);
            values.put("invalidNativeFaultEntries", run.invalidFaultEntries);
            values.put("invalidNativeFaultNormalReturns", run.invalidFaultReturns);
            values.put("openInvalidNativeFaultCalls", run.openInvalidFaultCalls);
            values.put("nonNormalNativeFaultCalls", run.nonNormalFaultCalls);
            values.put("sampleLimitIsAcceptanceLimit", false);
            values.put("secondaryFaultCauseInferred", false);
            values.put("tuple", run.tuple); values.put("fullC4aAcceptance", false); return values;
        }
    }
    /** One atomic observer cutoff; later unobserved callbacks are not claimed as prior history. */
    static Map<String,Object> releaseWithTerminalReport() {
        var run = active; P11C4aEvidence.require(run != null, "MALFORMED_TERMINAL_REPORT_OWNER");
        synchronized (run) {
            P11C4aEvidence.require(active == run, "MALFORMED_TERMINAL_REPORT_RETIRED");
            try {
                boolean passed;
                try { passed = terminalReady(); }
                catch (RuntimeException | Error invalidObservation) { passed = false; }
                var result = new java.util.LinkedHashMap<String,Object>(report());
                result.put("status", passed ? "ORIGINAL_SERVER_STOPPED_FINAL_WIRE_CHECKED"
                        : "FAILED_FINAL_WIRE_OBSERVATION");
                result.put("terminalRecheckPassed", passed);
                result.put("observationCutoff", "SERVER_STOPPED_AT_ATOMIC_HELPER_RELEASE");
                result.put("laterUnobservedCallbacksClaimed", false);
                return result;
            } finally { active = null; FRAME.remove(); FAILURES.remove(); }
        }
    }
    static void release() { active = null; FRAME.remove(); FAILURES.remove(); }
    private static String category(Throwable failure) {
        if (failure == null) { return "NONE"; }
        if (failure instanceof java.nio.channels.ClosedChannelException) { return "CLOSED_CHANNEL_EXCEPTION"; }
        if (failure instanceof DecoderException) { return "DECODER_EXCEPTION"; }
        if (failure.getClass() == RuntimeException.class) { return "EXACT_RUNTIME_EXCEPTION"; }
        if (failure instanceof RuntimeException) { return "OTHER_RUNTIME_EXCEPTION"; }
        if (failure instanceof Error) { return "ERROR"; }
        return "OTHER_THROWABLE";
    }
    private static Tuple tuple(ByteBuf buffer, int start, int length) {
        return new Tuple(length, buffer.getUnsignedByte(start + 1), buffer.getLong(start + 2), buffer.getLong(start + 10),
                buffer.getLong(start + 18), buffer.getLong(start + 26), length == 54 ? buffer.getLong(start + 34) : 0,
                buffer.getUnsignedByte(start + (length == 54 ? 42 : 35)), length == 36 ? buffer.getUnsignedByte(start + 34) : 0);
    }
    private record Tuple(int bytes, int scope, long connectionEpoch, long sceneSerial, long actorGeneration,
            long requestSeq, long statusVersion, int kind, int command) {
        boolean matches(Object value) {
            if (value instanceof P11TransitionProtocol.Request request) {
                return bytes == 36 && scope == P11TransitionWire.scopeCode(request.scope())
                        && connectionEpoch == request.connectionEpoch() && sceneSerial == request.sceneSerial()
                        && actorGeneration == request.actorGeneration() && requestSeq == request.requestSeq()
                        && kind == P11TransitionWire.kindCode(request.kind()) && command == P11TransitionWire.commandCode(request.command());
            }
            if (value instanceof P11TransitionProtocol.State state) {
                return bytes == 54 && scope == P11TransitionWire.scopeCode(state.scope())
                        && connectionEpoch == state.connectionEpoch() && sceneSerial == state.sceneSerial()
                        && actorGeneration == state.actorGeneration() && requestSeq == state.requestSeq()
                        && statusVersion == state.statusVersion() && kind == P11TransitionWire.kindCode(state.kind());
            }
            return false;
        }
    }
    private static final class Run {
        final Connection connection; final boolean server;
        volatile boolean enabled; volatile String failure; volatile Throwable thrown, packetThrown; volatile Tuple tuple;
        String packetFailureCategory = "UNOBSERVED", packetCauseCategory = "UNOBSERVED", packetGrandCauseCategory = "UNOBSERVED";
        String exceptionFailureCategory = "UNOBSERVED";
        boolean packetCauseIsWire, packetGrandCauseIsWire, exceptionIsPacket, exceptionCauseIsPacket;
        final java.util.List<java.util.Map<String,Object>> secondaryFaults = new java.util.ArrayList<>();
        long secondaryEntries, secondaryReturns, closedChannelSecondaryEntries, invalidFaultEntries, invalidFaultReturns, nonNormalFaultCalls;
        int openFailureCalls, openInvalidFaultCalls;
        int corruptions, decodeEntries, normalDecodes, wireThrows, packetThrows, exceptionEntries, exceptionReturns, downstream;
        Run(Connection connection, boolean server) { this.connection = connection; this.server = server; }
    }
}
