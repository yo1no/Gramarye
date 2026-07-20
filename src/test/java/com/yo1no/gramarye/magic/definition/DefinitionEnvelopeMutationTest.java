package com.yo1no.gramarye.magic.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.action.ActionDefinition;
import com.yo1no.gramarye.magic.definition.action.UnknownActionDefinition;
import com.yo1no.gramarye.magic.definition.codec.ActionDefinitionCodec;
import com.yo1no.gramarye.magic.definition.codec.TriggerDefinitionCodec;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import com.yo1no.gramarye.magic.definition.trigger.TriggerDefinition;
import com.yo1no.gramarye.magic.definition.trigger.UnknownTriggerDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DefinitionEnvelopeMutationTest {
    private static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("unknown", "mutable_payload");
    private static final DefinitionFailure FAILURE = DefinitionFailure.of(
            DefinitionFailure.Code.UNKNOWN_TYPE,
            "test unknown type");

    @Test
    void modifyingConstructorJsonTreeDoesNotChangeUnknownTriggerEncoding() {
        var source = nestedJsonPayload();
        var unknown = new UnknownTriggerDefinition(jsonEnvelope(source), FAILURE);
        var before = encodeTriggerJson(unknown);

        source.addProperty("root", "source-mutated");
        source.getAsJsonObject("nested").addProperty("value", "nested-source-mutated");
        assertEquals("source-mutated", source.get("root").getAsString());
        assertEquals("nested-source-mutated", source.getAsJsonObject("nested").get("value").getAsString());

        var after = encodeTriggerJson(unknown);
        assertJsonEnvelopeUnchanged(before, after);
    }

    @Test
    void modifyingConstructorNbtTreeDoesNotChangeUnknownActionEncoding() {
        var source = nestedNbtPayload();
        var unknown = new UnknownActionDefinition(nbtEnvelope(source), FAILURE);
        var before = encodeActionNbt(unknown);

        source.putString("root", "source-mutated");
        ((CompoundTag) source.get("nested")).putString("value", "nested-source-mutated");
        assertEquals("source-mutated", source.getString("root"));
        assertEquals("nested-source-mutated", ((CompoundTag) source.get("nested")).getString("value"));

        var after = encodeActionNbt(unknown);
        assertNbtEnvelopeUnchanged(before, after);
    }

    @Test
    void modifyingAccessorJsonTreeDoesNotChangeUnknownTriggerEncoding() {
        var unknown = new UnknownTriggerDefinition(jsonEnvelope(nestedJsonPayload()), FAILURE);
        var before = encodeTriggerJson(unknown);
        var exposed = (JsonObject) unknown.envelope().copyRawPayload().getValue();

        exposed.addProperty("root", "accessor-mutated");
        exposed.getAsJsonObject("nested").addProperty("value", "nested-accessor-mutated");
        assertEquals("accessor-mutated", exposed.get("root").getAsString());
        assertEquals("nested-accessor-mutated", exposed.getAsJsonObject("nested").get("value").getAsString());

        var after = encodeTriggerJson(unknown);
        assertJsonEnvelopeUnchanged(before, after);
    }

    @Test
    void modifyingAccessorNbtTreeDoesNotChangeUnknownActionEncoding() {
        var unknown = new UnknownActionDefinition(nbtEnvelope(nestedNbtPayload()), FAILURE);
        var before = encodeActionNbt(unknown);
        var exposed = (CompoundTag) unknown.envelope().copyRawPayload().getValue();

        exposed.putString("root", "accessor-mutated");
        ((CompoundTag) exposed.get("nested")).putString("value", "nested-accessor-mutated");
        assertEquals("accessor-mutated", exposed.getString("root"));
        assertEquals("nested-accessor-mutated", ((CompoundTag) exposed.get("nested")).getString("value"));

        var after = encodeActionNbt(unknown);
        assertNbtEnvelopeUnchanged(before, after);
    }

    private static DefinitionEnvelope jsonEnvelope(JsonObject payload) {
        return new DefinitionEnvelope(TYPE_ID, 3, new Dynamic<>(JsonOps.INSTANCE, payload));
    }

    private static DefinitionEnvelope nbtEnvelope(CompoundTag payload) {
        return new DefinitionEnvelope(TYPE_ID, 3, new Dynamic<>(NbtOps.INSTANCE, payload));
    }

    private static JsonObject nestedJsonPayload() {
        var nested = new JsonObject();
        nested.addProperty("value", "original-nested");
        var root = new JsonObject();
        root.addProperty("root", "original-root");
        root.add("nested", nested);
        return root;
    }

    private static CompoundTag nestedNbtPayload() {
        var nested = new CompoundTag();
        nested.putString("value", "original-nested");
        var root = new CompoundTag();
        root.putString("root", "original-root");
        root.put("nested", nested);
        return root;
    }

    private static JsonObject encodeTriggerJson(TriggerDefinition definition) {
        return TriggerDefinitionCodec.create(new DefinitionTestFixtures.FakeTriggerTypeLookup())
                .encodeStart(JsonOps.INSTANCE, definition)
                .getOrThrow()
                .getAsJsonObject()
                .deepCopy();
    }

    private static CompoundTag encodeActionNbt(ActionDefinition definition) {
        var encoded = (CompoundTag) ActionDefinitionCodec.create(new DefinitionTestFixtures.FakeActionTypeLookup())
                .encodeStart(NbtOps.INSTANCE, definition)
                .getOrThrow();
        return encoded.copy();
    }

    private static void assertJsonEnvelopeUnchanged(JsonObject before, JsonObject after) {
        assertEquals(before.get("type"), after.get("type"));
        assertEquals(before.get("schema_version"), after.get("schema_version"));
        assertEquals(before.get("payload"), after.get("payload"));
    }

    private static void assertNbtEnvelopeUnchanged(CompoundTag before, CompoundTag after) {
        assertEquals(before.get("type"), after.get("type"));
        assertEquals(before.get("schema_version"), after.get("schema_version"));
        assertEquals(before.get("payload"), after.get("payload"));
    }
}
