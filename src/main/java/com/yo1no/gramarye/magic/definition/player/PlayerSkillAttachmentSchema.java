package com.yo1no.gramarye.magic.definition.player;

import java.util.Set;

/** Current player Attachment field vocabulary. */
final class PlayerSkillAttachmentSchema {
    static final int CURRENT_VERSION = 0;
    static final String ATTACHMENT_SCHEMA_VERSION = "attachment_schema_version";
    static final String DRAFTS = "drafts";
    static final String LATEST_STATES = "latest_states";
    static final String EQUIPPED_SLOTS = "equipped_slots";
    static final String EDITOR = "editor";
    static final Set<String> OUTER_FIELDS = Set.of(
            ATTACHMENT_SCHEMA_VERSION, DRAFTS, LATEST_STATES, EQUIPPED_SLOTS, EDITOR);

    static final String SKILL_ID = "skill_id";
    static final String DRAFT_ENCODING = "draft_encoding";
    static final String DRAFT_BYTES = "draft_bytes";
    static final Set<String> DRAFT_FIELDS = Set.of(SKILL_ID, DRAFT_ENCODING, DRAFT_BYTES);

    static final String MUTATION_GENERATION = "mutation_generation";
    static final String POINTER = "pointer";
    static final Set<String> LATEST_REQUIRED_FIELDS = Set.of(SKILL_ID, MUTATION_GENERATION);
    static final Set<String> LATEST_POINTER_FIELDS = Set.of(SKILL_ID, MUTATION_GENERATION, POINTER);

    static final String SLOT = "slot";
    static final String REFERENCE = "reference";
    static final Set<String> EQUIPPED_FIELDS = Set.of(SLOT, REFERENCE);

    static final String SELECTED_DRAFT = "selected_draft";
    static final String SELECTED_NODE_INDEX = "selected_node_index";
    static final Set<String> EDITOR_FIELDS = Set.of(SELECTED_DRAFT, SELECTED_NODE_INDEX);

    static final String TOKEN_PREFIX = "__gramarye_opaque_draft_token_v0:";

    private PlayerSkillAttachmentSchema() {
    }
}
