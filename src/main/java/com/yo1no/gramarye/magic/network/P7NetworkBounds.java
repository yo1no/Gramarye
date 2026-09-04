package com.yo1no.gramarye.magic.network;

final class P7NetworkBounds {
    static final int MAX_C2S_INTENT_BYTES = 32;
    static final int MAX_S2C_ACK_BYTES = 32;
    static final int MAX_S2C_SYNC_BYTES = 4096;
    static final int MAX_INTENTS_PER_PACKET = 1;
    static final int MAX_INTENTS_PER_PLAYER_PER_TICK = 8;
    static final int RATE_BUCKET_CAPACITY = 8;
    static final int MAX_GLOBAL_WORK_UNITS_PER_TICK = 64;
    static final int MAX_PENDING_INTENTS_PER_PLAYER = 8;
    static final int MAX_PENDING_INTENTS_PER_SERVER = 64;
    static final long NETWORK_SEQUENCE_MIN = 1L;
    static final long NETWORK_SEQUENCE_MAX = Long.MAX_VALUE;
    static final int RETAINED_REPLAY_SCALARS = 1;
    static final int MAX_FUTURE_SEQUENCE_GAP = 0;
    static final int MAX_ACK_ENTRIES_PER_PACKET = 1;
    static final int MAX_PENDING_ACKS_PER_PLAYER = 0;
    static final int MAX_SYNC_ENTRIES_PER_PACKET = 64;
    static final int MAX_SYNC_PAYLOAD_BYTES = 4096;
    static final int MAX_WIRE_STRING_OR_RESOURCE_BYTES = 128;
    static final int MAX_OPTIONAL_ENTITY_HINTS_PER_INTENT = 1;
    static final int MAX_ACTIVE_SESSIONS_PER_PLAYER = 1;
    static final int MAX_ACTIVE_SESSIONS_PER_SERVER = 256;
    static final int MAX_DIAGNOSTIC_RECORDS = 256;
    static final int MAX_DISCONNECT_CLEANUP_WORK = 9;
    static final int MAX_RELOAD_RECONCILIATION_QUEUE = 256;
    static final int MAX_RELOAD_RECONCILIATION_PER_TICK = 16;
    static final int MAX_SERVER_STOP_CLEANUP_RECORDS = 576;
    static final int RATE_STRIKE_DISCONNECT_THRESHOLD = 8;
    static final int RATE_STRIKE_WINDOW_TICKS = 100;
    static final int MIN_RESYNC_INTERVAL_TICKS = 20;
    static final String PROTOCOL_VERSION = "gramarye-p7-v0";
    static final long SEQUENCE_EXHAUSTION_BOUNDARY = Long.MAX_VALUE;
    static final int MAX_CUMULATIVE_P7_WORK_PER_TICK = 64;

    static final int RATE_BUCKET_INITIAL_TOKENS = 8;
    static final int RATE_BUCKET_REFILL_PER_TICK = 2;
    static final int RATE_BUCKET_COST_PER_CAST = 1;
    static final int SLOT_MIN = 0;
    static final int SLOT_MAX = 63;
    static final int CAST_INPUT_KIND_CODE = 0;
    static final int AIM_PRESENT_BIT = 0;
    static final int ENTITY_HINT_PRESENT_BIT = 1;
    static final int ALLOWED_PRESENCE_MASK = 0b00000011;
    static final int Q15_MIN = -32767;
    static final int Q15_MAX = 32767;
    static final int Q15_RESERVED = -32768;
    static final int ENTITY_HINT_MIN = 1;
    static final int ENTITY_HINT_MAX = Integer.MAX_VALUE;
    static final int ACTUAL_MAX_CAST_INTENT_BODY_BYTES = 22;
    static final int ACTUAL_MAX_ACK_BODY_BYTES = 18;

    private P7NetworkBounds() {
        throw new AssertionError("no instances");
    }
}
