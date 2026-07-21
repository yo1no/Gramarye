package com.yo1no.gramarye.magic.definition.document;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

final class DocumentTestFixtures {
    static final String SKILL_UUID = "123e4567-e89b-12d3-a456-426614174000";
    static final SkillId SKILL_ID = new SkillId(UUID.fromString(SKILL_UUID));

    private DocumentTestFixtures() {
    }

    static DefinitionEnvelope envelope(String path) {
        return new DefinitionEnvelope(
                ResourceLocation.fromNamespaceAndPath("test", path),
                0,
                new Dynamic<>(JsonOps.INSTANCE, new JsonObject()));
    }

    static NodeDocument node() {
        return new NodeDocument(
                envelope("trigger"), envelope("action"), AppearanceOverrideDocument.none());
    }

    static SkillDocument document(AppearanceDocument appearance) {
        return new SkillDocument(0, SKILL_ID, new SkillRevision(0), List.of(node()), appearance);
    }

    static SkillDraft draft(AppearanceDocument appearance) {
        return new SkillDraft(
                0,
                SKILL_ID,
                Optional.empty(),
                List.of(new DraftNode(
                        DraftTriggerSlot.missing(),
                        DraftActionSlot.present(envelope("action")),
                        AppearanceOverrideDocument.none())),
                appearance);
    }

    static JsonObject documentJson(JsonElement appearance) {
        var root = JsonParser.parseString("""
                {
                  "schema_version": 0,
                  "skill_id": "123e4567-e89b-12d3-a456-426614174000",
                  "revision": 0,
                  "nodes": [
                    {
                      "trigger": {"type":"test:trigger","schema_version":0,"payload":{}},
                      "action": {"type":"test:action","schema_version":0,"payload":{}}
                    }
                  ]
                }
                """).getAsJsonObject();
        if (appearance != null) {
            root.add("appearance", appearance);
        }
        return root;
    }

    static JsonObject draftJson(JsonElement appearance) {
        var root = JsonParser.parseString("""
                {
                  "draft_schema_version": 0,
                  "skill_id": "123e4567-e89b-12d3-a456-426614174000",
                  "nodes": [
                    {
                      "trigger": {"state":"missing"},
                      "action": {
                        "state":"present",
                        "definition":{"type":"test:action","schema_version":0,"payload":{}}
                      }
                    }
                  ]
                }
                """).getAsJsonObject();
        if (appearance != null) {
            root.add("appearance", appearance);
        }
        return root;
    }
}
