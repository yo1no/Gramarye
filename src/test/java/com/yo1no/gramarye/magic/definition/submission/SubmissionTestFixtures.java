package com.yo1no.gramarye.magic.definition.submission;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

final class SubmissionTestFixtures {
    static final SkillId SKILL_ID =
            new SkillId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

    private SubmissionTestFixtures() {
    }

    static SkillDraft draft(int schemaVersion, List<DraftNode> nodes) {
        return draft(schemaVersion, nodes, AppearanceDocument.defaultAppearance());
    }

    static SkillDraft draft(
            int schemaVersion,
            List<DraftNode> nodes,
            AppearanceDocument appearance) {
        return new SkillDraft(
                schemaVersion,
                SKILL_ID,
                Optional.empty(),
                nodes,
                appearance);
    }

    static DraftNode completeNode() {
        return completeNode(
                envelope("trigger"),
                envelope("action"),
                AppearanceOverrideDocument.none());
    }

    static DraftNode completeNode(
            DefinitionEnvelope trigger,
            DefinitionEnvelope action,
            AppearanceOverrideDocument appearanceOverride) {
        return new DraftNode(
                DraftTriggerSlot.present(trigger),
                DraftActionSlot.present(action),
                appearanceOverride);
    }

    static DraftNode missingTrigger() {
        return new DraftNode(
                DraftTriggerSlot.missing(),
                DraftActionSlot.present(envelope("action")),
                AppearanceOverrideDocument.none());
    }

    static DraftNode missingAction() {
        return new DraftNode(
                DraftTriggerSlot.present(envelope("trigger")),
                DraftActionSlot.missing(),
                AppearanceOverrideDocument.none());
    }

    static DraftNode missingBoth() {
        return new DraftNode(
                DraftTriggerSlot.missing(),
                DraftActionSlot.missing(),
                AppearanceOverrideDocument.none());
    }

    static DefinitionEnvelope envelope(String path) {
        return envelope(path, null);
    }

    static DefinitionEnvelope envelope(String path, String secret) {
        var payload = new JsonObject();
        if (secret != null) {
            var nested = new JsonObject();
            nested.addProperty("secret", secret);
            payload.add("nested", nested);
        }
        return new DefinitionEnvelope(
                ResourceLocation.fromNamespaceAndPath("test", path),
                0,
                new Dynamic<>(JsonOps.INSTANCE, payload));
    }
}
