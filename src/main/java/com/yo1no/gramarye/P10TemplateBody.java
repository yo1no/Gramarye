package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Structural system content only: no player identity, policy, resource manager, or projection. */
record P10TemplateBody(List<NodeDocument> nodes, AppearanceDocument appearance) {
    P10TemplateBody {
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        Objects.requireNonNull(appearance, "appearance");
    }

    SkillDraft materialize(SkillId skillId, Optional<SkillRevision> baseRevision) {
        return new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                skillId,
                baseRevision,
                nodes.stream().map(node -> new DraftNode(
                        DraftTriggerSlot.present(node.trigger()),
                        DraftActionSlot.present(node.action()),
                        node.appearanceOverride())).toList(),
                appearance);
    }

    SkillDocument document(SkillId skillId, SkillRevision revision) {
        return new SkillDocument(SkillDocument.CURRENT_SCHEMA_VERSION, skillId, revision, nodes, appearance);
    }
}
