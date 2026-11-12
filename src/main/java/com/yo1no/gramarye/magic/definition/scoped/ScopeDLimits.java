package com.yo1no.gramarye.magic.definition.scoped;

/** Technical ceilings for the isolated Scope D data formats and publication engine. */
final class ScopeDLimits {
    static final int MAX_SYMBOL_BYTES = 96;
    static final int MAX_TEXT_BYTES = 384;
    static final int MAX_SETTINGS = 24;
    static final int MAX_HANDLERS = 12;
    static final int MAX_BLOCKS = 96;
    static final int MAX_EXPRESSIONS = 96;
    static final int MAX_EXPRESSION_DEPTH = 12;
    static final int MAX_ARGUMENTS = 8;
    static final int MAX_CAPABILITIES = 12;
    static final int MAX_DESCRIPTOR_CONTEXT_ENTRIES = 96;
    static final int MAX_MANIFEST_ENTRIES = 24;
    static final int MAX_RAW_SLOTS = 12;
    static final int MAX_RAW_SLOT_BYTES = 12 * 1024;
    static final int MAX_RAW_BYTES = 40 * 1024;
    static final int MAX_RAW_DEPTH = 24;
    static final int MAX_RAW_VALUES = 768;
    static final int MAX_DOCUMENT_BYTES = 64 * 1024;
    static final int MAX_DIAGNOSTICS = 48;

    static final int MAX_FRONTEND_DESCRIPTORS = MAX_DESCRIPTOR_CONTEXT_ENTRIES;
    static final int MAX_FRONTEND_CAPABILITIES = 2 * MAX_CAPABILITIES;
    static final int MAX_FRONTEND_ARGUMENTS = MAX_ARGUMENTS;
    static final int MAX_FRONTEND_FIELDS = MAX_SETTINGS;
    static final int MAX_FRONTEND_CONTEXT_KEYS = 2 * MAX_HANDLERS;
    static final int MAX_FRONTEND_CONFLICTS = MAX_FRONTEND_CAPABILITIES;
    static final int MAX_FRONTEND_COST = 1_024;
    static final int MAX_FRONTEND_BYTES = ScopeDLimits.MAX_CATALOG_BYTES / 4;

    static final int MAX_ROUTES = 96;
    static final int MAX_REVISIONS = 192;
    static final int MAX_GRAPH_EDGES = 4 * MAX_ROUTES;
    static final int MAX_BATCH = 32;
    static final int MAX_EXTERNAL_ROOTS = 256;
    static final int MAX_GRAPH_WORK = 2 * (MAX_ROUTES + 1) * (MAX_ROUTES + MAX_GRAPH_EDGES);
    static final int MAX_CATALOG_BYTES = 2 * 1024 * 1024;
    static final int MAX_CATALOG_PAYLOAD = MAX_CATALOG_BYTES - 165;
    static final int MAX_DRAFT_BYTES = 512 * 1024;
    static final int MAX_DRAFT_PAYLOAD = MAX_DRAFT_BYTES - 77;
    static final int MAX_DRAFTS = 32;
    static final int MAX_OWNERS = 32;
    static final int ID_STRIDE = 256;
    static final long MAX_SERIAL = (Long.MAX_VALUE - (ID_STRIDE - 1)) / ID_STRIDE;

    // A resolver session can check every route and one full Manifest selection.
    // Per traversal: forward walk, two SCC passes, exact projection and entry binding.
    static final int MAX_COMPILER_EXACT_BINDINGS = MAX_ROUTES;
    static final int MAX_COMPILER_RESOLVER_ROOTS = MAX_ROUTES + MAX_MANIFEST_ENTRIES + 1;
    static final int MAX_COMPILER_RESOLVER_WORK = MAX_COMPILER_RESOLVER_ROOTS
            * (4 * MAX_ROUTES + 3 * MAX_GRAPH_EDGES + MAX_MANIFEST_ENTRIES);

    // One lowered unit per routed Spell. Descriptor arguments are inline typed operands.
    static final int MAX_COMPILER_INSTRUCTIONS = MAX_ROUTES * (MAX_EXPRESSIONS + MAX_FRONTEND_FIELDS + MAX_BLOCKS);
    static final int MAX_COMPILER_CREATION_INPUTS = MAX_ROUTES * MAX_FRONTEND_FIELDS;
    static final int MAX_COMPILER_SETTINGS = MAX_ROUTES * MAX_SETTINGS;
    static final int MAX_COMPILER_EXPRESSION_VISITS = MAX_ROUTES * (MAX_EXPRESSIONS + MAX_FRONTEND_FIELDS);
    static final int MAX_COMPILER_HANDLERS = MAX_ROUTES * MAX_HANDLERS;
    static final int MAX_COMPILER_BLOCKS = MAX_ROUTES * MAX_BLOCKS;
    static final int MAX_COMPILER_CONSTANTS = MAX_ROUTES * (MAX_EXPRESSIONS + 2 * MAX_FRONTEND_FIELDS + (MAX_BLOCKS + 3) * MAX_ARGUMENTS);
    static final int MAX_COMPILER_OPERANDS = MAX_COMPILER_INSTRUCTIONS * (3 + MAX_FRONTEND_FIELDS)
            + MAX_COMPILER_CONSTANTS + (MAX_COMPILER_HANDLERS + MAX_ROUTES) * MAX_FRONTEND_FIELDS;
    static final int MAX_COMPILER_SOURCE_ENTRIES = MAX_ROUTES * (MAX_SETTINGS + MAX_HANDLERS + MAX_BLOCKS + 3);
    static final int MAX_COMPILER_SOURCE_BYTES = MAX_COMPILER_SOURCE_ENTRIES * (64 + MAX_SYMBOL_BYTES);
    static final int MAX_COMPILER_FEEDBACK_VERTICES = MAX_ROUTES * (MAX_HANDLERS + 1);
    static final int MAX_COMPILER_FEEDBACK_EDGES = MAX_COMPILER_HANDLERS + MAX_GRAPH_EDGES
            + MAX_COMPILER_BLOCKS * MAX_HANDLERS;
    static final int MAX_COMPILER_EMISSION_CHECKS = MAX_COMPILER_BLOCKS * MAX_FRONTEND_CONTEXT_KEYS;
    static final int MAX_COMPILER_FEEDBACK_WORK = 3 * (MAX_COMPILER_FEEDBACK_VERTICES + MAX_COMPILER_FEEDBACK_EDGES)
            + MAX_COMPILER_EMISSION_CHECKS;
    static final int MAX_COMPILER_DESCRIPTOR_UNITS = MAX_ROUTES * (MAX_BLOCKS + MAX_HANDLERS + 3) * MAX_FRONTEND_COST;
    static final int MAX_COMPILER_DIAGNOSTICS = 64;
    static final int MAX_COMPILER_RETAINED_BYTES = 16 * 1024 * 1024;
    static final int MAX_COMPILER_SESSIONS = 1;

    static final int MAX_LEGACY_NODES = com.yo1no.gramarye.magic.limits.MagicSafetyCeilings.MAX_NODES;
    static final int MAX_LEGACY_PAYLOAD_FIELDS = 3;
    static final int MAX_LEGACY_REFERENCES = MAX_LEGACY_NODES - 1;
    static final int MAX_LEGACY_PROFILE_FACTS = 3 * (MAX_LEGACY_NODES + 1);
    static final int MAX_LEGACY_SOURCE_MAP_ENTRIES =
            1 + (3 + 2 * MAX_LEGACY_PAYLOAD_FIELDS) * MAX_LEGACY_NODES + MAX_LEGACY_REFERENCES;
    // Conservative projected material charges for the closed legacy layout, not an encoding ABI.
    static final int LEGACY_FIXED_BYTES = 192;
    static final int LEGACY_BINDING_BYTES = 64;
    static final int LEGACY_SCALAR_BYTES = 9;
    static final int LEGACY_ADDRESS_BYTES = 24;
    static final int LEGACY_REFERENCE_BYTES = 8;
    static final int LEGACY_PROFILE_FACT_BYTES = 2;
    static final int LEGACY_DIAGNOSTIC_BYTES = 1;
    static final int LEGACY_APPEARANCE_BYTES = 24;
    static final int MAX_LEGACY_PROJECTED_BYTES =
            2 * com.yo1no.gramarye.magic.limits.MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES;

    private ScopeDLimits() {}
}
