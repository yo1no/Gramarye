package com.yo1no.gramarye;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/** Bounded observation only: no actor/ticket lookup, permit creation, source mutation or replay. */
public final class P11C4aNativeObservations {
    public enum Event {
        CONFIG_CALL_ENTER, CONFIG_CALL_RETURN, CONFIG_CALL_THROW,
        RESPAWN_CALL_ENTER, RESPAWN_CALL_RETURN, RESPAWN_CALL_THROW,
        SWITCH_CALL_ENTER, SWITCH_CALL_RETURN, SWITCH_CALL_THROW,
        FACTORY_TICKET_CHECKED, RESPAWN_TICKET_CHECKED, EXPECTED_ACTOR,
        LOGIN_FRAME_PRODUCED, RESPAWN_FRAME_PRODUCED,
        LOGIN_SEND_RETURN, RESPAWN_SEND_RETURN, START_CONFIGURATION_SEND_RETURN,
        CLIENT_LOGIN_RETURN, CLIENT_RESPAWN_RETURN, CLIENT_START_CONFIGURATION_RETURN,
        CLIENT_STATE_RETURN, SERVER_INGRESS, STALE_SERVER_INGRESS, PARKING_INSTALLED,
        PARKING_RESUME_ENTER, PARKING_RESUME_RETURN, PARKING_RESUME_THROW,
        CLIENT_RETURN_OTHER_FALLBACK, CLIENT_RETURN_OTHER_FALLBACK_DISCONNECTED
    }

    private static final int MAX_CONNECTIONS = 8;
    private static final Map<Connection, Row> ROWS = new IdentityHashMap<>();
    private static final ThreadLocal<Call> CALL = new ThreadLocal<>();
    private static final long[] WIRE = new long[4];
    private static String failure;
    private static boolean configurationCatchRecorded;

    private P11C4aNativeObservations() {}

    private static final class Row {
        private final long[] events = new long[Event.values().length];
        private final long[] outcomes = new long[P11TransitionProtocol.Outcome.values().length];
        private long sequence;
        private long tries;
        private long statuses;
        private long factoryOrder;
        private long frameOrder;
        private long sendOrder;
        private long callerOrder;
        private P11TransitionProtocol.State lastState;
        private P11TransitionProtocol.Request lastRequest;
    }

    public static final class Call {
        private final Connection connection;
        private final Call previous;
        private final Event entry;
        private Call(Connection connection, Call previous, Event entry) {
            this.connection = connection; this.previous = previous; this.entry = entry;
        }
    }

    public static Call begin(ServerCommonPacketListenerImpl listener, Event event) {
        if (!P11C4aEvidence.enabled() || !listener.getMainThreadEventLoop().isSameThread()) { return null; }
        var call = new Call(listener.getConnection(), CALL.get(), event);
        CALL.set(call);
        event(call.connection, event);
        return call;
    }

    public static void end(Call call, boolean normal) {
        if (call == null) { return; }
        if (CALL.get() != call) { failed("CALL_OBSERVER_ORDER"); return; }
        if (call.previous == null) { CALL.remove(); } else { CALL.set(call.previous); }
        var event = switch (call.entry) {
            case CONFIG_CALL_ENTER -> normal ? Event.CONFIG_CALL_RETURN : Event.CONFIG_CALL_THROW;
            case RESPAWN_CALL_ENTER -> normal ? Event.RESPAWN_CALL_RETURN : Event.RESPAWN_CALL_THROW;
            case SWITCH_CALL_ENTER -> normal ? Event.SWITCH_CALL_RETURN : Event.SWITCH_CALL_THROW;
            case PARKING_RESUME_ENTER -> normal ? Event.PARKING_RESUME_RETURN : Event.PARKING_RESUME_THROW;
            default -> throw new IllegalArgumentException("INVALID_C4A_CALL_EVENT");
        };
        event(call.connection, event);
    }

    public static void current(Event event) {
        var call = CALL.get();
        if (call != null) { event(call.connection, event); }
    }

    /** No exception text, arbitrary class name, cause chain, path, account data or native log is exported. */
    public static synchronized void configurationCatch(ServerCommonPacketListenerImpl listener, Throwable caught) {
        if (!P11C4aEvidence.enabled() || configurationCatchRecorded
                || !listener.getMainThreadEventLoop().isSameThread()) { return; }
        configurationCatchRecorded = true;
        String category = caught instanceof IllegalStateException ? "ILLEGAL_STATE"
                : caught instanceof java.util.NoSuchElementException ? "NO_SUCH_ELEMENT"
                : caught instanceof IllegalArgumentException ? "ILLEGAL_ARGUMENT"
                : caught instanceof NullPointerException ? "NULL_POINTER" : "OTHER_EXCEPTION";
        var frames = new java.util.ArrayList<Map<String, Object>>();
        for (var frame : caught.getStackTrace()) {
            if (frames.size() == 16) { break; }
            if (frame.getClassName().startsWith("com.yo1no.gramarye.")
                    || frame.getClassName().startsWith("net.minecraft.server.")) {
                frames.add(Map.of("class", frame.getClassName(), "method", frame.getMethodName(),
                        "line", frame.getLineNumber()));
            }
        }
        try {
            P11C4aEvidence.write(P11C4aEvidence.root().resolve("server"), "configuration-native-catch.json",
                    Map.of("status", "OBSERVED_ORIGINAL_CONFIG_EXCEPTION_CATCH_NOT_ACCEPTANCE",
                            "category", category, "sourceFrames", java.util.List.copyOf(frames),
                            "nativeAndWire", snapshot(listener.getConnection())));
        } catch (java.io.IOException | RuntimeException evidenceFailure) {
            failed("CONFIGURATION_CATCH_EVIDENCE_FAILURE");
        }
    }

    public static void expectedFrame(Packet<?> packet) {
        if (packet instanceof ClientboundLoginPacket) { current(Event.LOGIN_FRAME_PRODUCED); }
        else if (packet instanceof ClientboundRespawnPacket) { current(Event.RESPAWN_FRAME_PRODUCED); }
    }

    public static void sent(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        if (packet instanceof ClientboundLoginPacket) { event(listener.getConnection(), Event.LOGIN_SEND_RETURN); }
        else if (packet instanceof ClientboundRespawnPacket) { event(listener.getConnection(), Event.RESPAWN_SEND_RETURN); }
        else if (packet instanceof ClientboundStartConfigurationPacket) { event(listener.getConnection(), Event.START_CONFIGURATION_SEND_RETURN); }
    }

    public static synchronized void event(Connection connection, Event event) {
        if (!P11C4aEvidence.enabled()) { return; }
        var row = row(connection);
        if (row == null) { return; }
        row.events[event.ordinal()]++;
        row.sequence++;
        switch (event) {
            case FACTORY_TICKET_CHECKED, RESPAWN_TICKET_CHECKED -> row.factoryOrder = row.sequence;
            case LOGIN_FRAME_PRODUCED, RESPAWN_FRAME_PRODUCED -> row.frameOrder = row.sequence;
            case LOGIN_SEND_RETURN, RESPAWN_SEND_RETURN, START_CONFIGURATION_SEND_RETURN -> row.sendOrder = row.sequence;
            case CONFIG_CALL_RETURN, RESPAWN_CALL_RETURN, SWITCH_CALL_RETURN -> row.callerOrder = row.sequence;
            default -> { }
        }
    }

    public static synchronized void ingress(Object value, Connection connection, ICommonPacketListener listener) {
        if (!P11C4aEvidence.enabled()) { return; }
        if (!(value instanceof P11TransitionProtocol.Request request) || connection == null || listener == null
                || listener.getConnection() != connection) {
            failed("INGRESS_OBSERVER_TYPE_OR_CONNECTION"); return;
        }
        if (connection.getPacketListener() != listener || !connection.isConnected()) {
            // A captured retired listener is a legitimate negative, never an accepted TRY.
            event(connection, Event.STALE_SERVER_INGRESS); return;
        }
        var row = row(connection);
        if (row == null) { return; }
        event(connection, Event.SERVER_INGRESS);
        row.lastRequest = request;
        if (request.command() == P11TransitionProtocol.Command.TRY) { row.tries++; }
        else { row.statuses++; }
    }

    public static synchronized void clientState(Object value, Connection connection) {
        if (!P11C4aEvidence.enabled()) { return; }
        if (!(value instanceof P11TransitionProtocol.State state)) { failed("STATE_OBSERVER_TYPE"); return; }
        var row = row(connection);
        if (row == null) { return; }
        row.lastState = state;
        row.outcomes[state.outcome().ordinal()]++;
        event(connection, Event.CLIENT_STATE_RETURN);
    }

    public static synchronized void wire(int kind, int bytes) {
        if (!P11C4aEvidence.enabled()) { return; }
        if (kind < 0 || kind >= 4 || bytes != (kind < 2 ? 36 : 54)) { failed("WIRE_OBSERVER_LENGTH"); return; }
        WIRE[kind]++;
    }

    static synchronized long count(Connection connection, Event event) {
        var row = ROWS.get(connection);
        return row == null ? 0 : row.events[event.ordinal()];
    }

    static synchronized long tries(Connection connection) {
        var row = ROWS.get(connection); return row == null ? 0 : row.tries;
    }

    static synchronized void requireHealthy() {
        P11C4aEvidence.require(failure == null, "C4A_NATIVE_OBSERVER_FAILURE");
    }

    static synchronized Map<String, Object> snapshot(Connection connection) {
        var row = ROWS.get(connection);
        if (row == null) { return Map.of("observed", false, "failure", failure == null ? "NONE" : failure); }
        var counts = new java.util.LinkedHashMap<String, Long>();
        for (var event : Event.values()) { counts.put(event.name(), row.events[event.ordinal()]); }
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("observed", true); result.put("events", counts);
        result.put("tries", row.tries); result.put("statuses", row.statuses);
        result.put("factoryOrder", row.factoryOrder); result.put("frameOrder", row.frameOrder);
        result.put("sendOrder", row.sendOrder); result.put("callerOrder", row.callerOrder);
        result.put("lastState", row.lastState); result.put("lastRequest", row.lastRequest);
        result.put("stateOutcomes", row.outcomes.clone()); result.put("wireEncodeRequestDecodeRequestEncodeStateDecodeState", WIRE.clone());
        result.put("failure", failure == null ? "NONE" : failure);
        return java.util.Collections.unmodifiableMap(result);
    }

    static synchronized void release() {
        ROWS.clear(); CALL.remove(); Arrays.fill(WIRE, 0); configurationCatchRecorded = false;
    }

    private static Row row(Connection connection) {
        if (connection == null) { failed("NULL_OBSERVER_CONNECTION"); return null; }
        var row = ROWS.get(connection);
        if (row == null && ROWS.size() < MAX_CONNECTIONS) { row = new Row(); ROWS.put(connection, row); }
        if (row == null) { failed("OBSERVER_CONNECTION_BOUND"); }
        return row;
    }

    private static synchronized void failed(String code) { if (failure == null) { failure = code; } }
}
