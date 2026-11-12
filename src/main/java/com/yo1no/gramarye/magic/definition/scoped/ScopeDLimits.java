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

    private ScopeDLimits() {}
}
