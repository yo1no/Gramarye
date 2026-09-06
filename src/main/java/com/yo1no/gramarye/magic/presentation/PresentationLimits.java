package com.yo1no.gramarye.magic.presentation;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;

/** Sole package-private numeric authority for the P8-S1 semantic core. */
final class PresentationLimits {
    static final int MAX_RESOURCE_LOCATION_UTF8_BYTES = 128;
    static final int MAX_PARAMETER_KEY_UTF8_BYTES = 32;
    static final int MAX_EVENT_OVERRIDES = 8;
    static final int MAX_INTENSITY_MILLI = MagicSafetyCeilings.MAX_APPEARANCE_INTENSITY;
    static final int MAX_ENTITY_ID = Integer.MAX_VALUE;
    static final double MAX_HORIZONTAL_POSITION = 30_000_000.0D;
    static final double MAX_VERTICAL_POSITION = 2_048.0D;
    static final int MAX_DIRECTION_Q15 = 32_767;
    static final int MAX_LEGAL_EVENT_BODY_BYTES = 897;
    static final int MAX_EVENT_BODY_BYTES = 1_024;
    static final int EVENT_PACKET_OVERHEAD_BYTES = 29;
    static final int MAX_SELECTED_RECIPIENTS = 32;

    static final long MIN_SEQUENCE = 1L;
    static final long MAX_SEQUENCE = Long.MAX_VALUE;

    static final int MAX_PROFILE_PARTICLE_STARTS = 256;
    static final int MIN_SAMPLE_INTERVAL_TICKS = 1;
    static final int MAX_SAMPLE_INTERVAL_TICKS = 120;

    static final long MAX_LOGICAL_EVENTS_PER_SKILL_PER_TICK = 8L;
    static final long MAX_LOGICAL_EVENTS_PER_SOURCE_PER_TICK = 32L;
    static final long MAX_LOGICAL_EVENTS_PER_SERVER_PER_TICK = 128L;
    static final long MAX_CURRENT_TICK_EVENT_BUFFER_EVENTS = 128L;
    static final long MAX_SERVER_EVENT_BODY_BYTES_PER_TICK = 131_072L;
    static final long MAX_DELIVERIES_PER_PLAYER_PER_TICK = 16L;
    static final long MAX_DELIVERY_BYTES_PER_PLAYER_PER_TICK = 32_768L;
    static final long MAX_DELIVERIES_PER_SERVER_PER_TICK = 512L;
    static final long MAX_DELIVERY_BYTES_PER_SERVER_PER_TICK = 524_288L;
    static final long MAX_CLIENT_PENDING_EVENTS = 64L;
    static final long MAX_CLIENT_PENDING_EVENT_BODY_BYTES = 65_536L;
    static final long MAX_CLIENT_COMBINED_QUEUED_BYTES = 573_440L;
    static final long MAX_CLIENT_EVENT_APPLICATIONS_PER_TICK = 32L;
    static final long MAX_CLIENT_ACTIVE_PRESENTATIONS = 128L;
    static final int MAX_CLIENT_ACTIVE_SELECTION_INPUT = 129;
    static final long MAX_ONLINE_RECIPIENT_SCAN_PER_EVENT = 512L;
    static final long MAX_RECIPIENT_EVALUATIONS_PER_TICK = 65_536L;
    static final long MAX_CANDIDATE_DELIVERIES_PER_TICK = 4_096L;
    static final long MAX_CLIENT_FACTORY_CALLS_PER_TICK = 192L;
    static final long MAX_ACTUAL_PARTICLE_STARTS_PER_TICK = 512L;
    static final long MAX_LIVE_PARTICLE_CREDITS = 4_096L;
    static final long MAX_SOUND_STARTS_PER_TICK = 8L;
    static final long MAX_ACTIVE_SOUNDS = 32L;
    static final long MAX_ACTIVE_TRAILS = 64L;
    static final long MAX_TOTAL_TRAIL_SEGMENTS = 1_536L;

    private PresentationLimits() {}
}
