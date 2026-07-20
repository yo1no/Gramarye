package com.yo1no.gramarye.magic.api.id;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TypedIdTest {
    @Test
    void uuidIdentifiersRejectNull() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new SkillId(null)),
                () -> assertThrows(NullPointerException.class, () -> new SkillInstanceId(null)),
                () -> assertThrows(NullPointerException.class, () -> new MarkerInstanceId(null)),
                () -> assertThrows(NullPointerException.class, () -> new ConstructInstanceId(null)),
                () -> assertThrows(NullPointerException.class, () -> new ScheduleId(null)));
    }

    @Test
    void numericIdentifiersRejectNegativeValues() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new SkillRevision(-1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new EventId(-1)));
    }

    @Test
    void recordsProvideValueEqualityAndHashCodes() {
        var uuid = UUID.fromString("617ed6df-f231-4b98-a56e-61e70c611035");

        assertAll(
                () -> assertEquals(new SkillId(uuid), new SkillId(uuid)),
                () -> assertEquals(new SkillId(uuid).hashCode(), new SkillId(uuid).hashCode()),
                () -> assertEquals(new SkillRevision(7), new SkillRevision(7)),
                () -> assertEquals(new SkillRevision(7).hashCode(), new SkillRevision(7).hashCode()),
                () -> assertEquals(new SkillInstanceId(uuid), new SkillInstanceId(uuid)),
                () -> assertEquals(new SkillInstanceId(uuid).hashCode(), new SkillInstanceId(uuid).hashCode()),
                () -> assertEquals(new MarkerInstanceId(uuid), new MarkerInstanceId(uuid)),
                () -> assertEquals(new MarkerInstanceId(uuid).hashCode(), new MarkerInstanceId(uuid).hashCode()),
                () -> assertEquals(new ConstructInstanceId(uuid), new ConstructInstanceId(uuid)),
                () -> assertEquals(new ConstructInstanceId(uuid).hashCode(), new ConstructInstanceId(uuid).hashCode()),
                () -> assertEquals(new ScheduleId(uuid), new ScheduleId(uuid)),
                () -> assertEquals(new ScheduleId(uuid).hashCode(), new ScheduleId(uuid).hashCode()),
                () -> assertEquals(new EventId(11), new EventId(11)),
                () -> assertEquals(new EventId(11).hashCode(), new EventId(11).hashCode()));
    }

    @Test
    void codecsRoundTripEveryIdentifierType() {
        var uuid = UUID.fromString("8e865b28-8ea4-4f85-97c3-3c8dd995aadf");

        assertAll(
                () -> assertCodecRoundTrip(SkillId.CODEC, new SkillId(uuid)),
                () -> assertCodecRoundTrip(SkillRevision.CODEC, new SkillRevision(42)),
                () -> assertCodecRoundTrip(SkillInstanceId.CODEC, new SkillInstanceId(uuid)),
                () -> assertCodecRoundTrip(MarkerInstanceId.CODEC, new MarkerInstanceId(uuid)),
                () -> assertCodecRoundTrip(ConstructInstanceId.CODEC, new ConstructInstanceId(uuid)),
                () -> assertCodecRoundTrip(ScheduleId.CODEC, new ScheduleId(uuid)),
                () -> assertCodecRoundTrip(EventId.CODEC, new EventId(73)));
    }

    @Test
    void numericCodecsRejectNegativeValuesAsDataErrors() {
        assertAll(
                () -> assertFalse(SkillRevision.CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createLong(-1)).isSuccess()),
                () -> assertFalse(EventId.CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createLong(-1)).isSuccess()));
    }

    @Test
    void semanticIdentifierTypesAreNotInterchangeable() {
        var uuid = UUID.fromString("88db51af-a3ce-4739-bcbb-683eec2ca9ef");
        var skillId = new SkillId(uuid);
        var instanceId = new SkillInstanceId(uuid);

        assertAll(
                () -> assertNotEquals(skillId, instanceId),
                () -> assertFalse(SkillId.class.isAssignableFrom(SkillInstanceId.class)),
                () -> assertFalse(SkillInstanceId.class.isAssignableFrom(SkillId.class)));
    }

    private static <T> void assertCodecRoundTrip(Codec<T> codec, T expected) {
        var encoded = codec.encodeStart(JsonOps.INSTANCE, expected).getOrThrow();
        var decoded = codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(expected, decoded);
    }
}
