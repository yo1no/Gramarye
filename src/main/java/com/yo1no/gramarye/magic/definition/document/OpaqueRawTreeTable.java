package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;

/** Ordered package-internal binding between transient token IDs and immutable raw bytes. */
final class OpaqueRawTreeTable {
    private static final int MAX_TOKEN_COUNT = 3 * MagicSafetyCeilings.MAX_NODES + 1;

    private final List<OpaqueRawTreeEntry> entries;

    private OpaqueRawTreeTable(List<OpaqueRawTreeEntry> entries) {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.size() > MAX_TOKEN_COUNT) {
            throw new IllegalArgumentException("opaque token count exceeds the derived hard ceiling");
        }
        for (var index = 0; index < entries.size(); index++) {
            if (entries.get(index).tokenId() != index) {
                throw new IllegalArgumentException("opaque token IDs must be contiguous and ordered");
            }
        }
        this.entries = entries;
    }

    static OpaqueRawTreeTable copyOf(List<OpaqueRawTreeEntry> entries) {
        return new OpaqueRawTreeTable(entries);
    }

    static OpaqueRawTreeTable fromPhysical(PhysicalSkillDocument document) {
        Objects.requireNonNull(document, "document");
        var entries = new ArrayList<OpaqueRawTreeEntry>(
                Math.min(MAX_TOKEN_COUNT, 2 * document.nodes().size() + 1));
        for (var nodeIndex = 0; nodeIndex < document.nodes().size(); nodeIndex++) {
            var node = document.nodes().get(nodeIndex);
            addDefinition(entries, new OpaqueRawTreeLocation.TriggerPayload(nodeIndex), node.trigger());
            addDefinition(entries, new OpaqueRawTreeLocation.ActionPayload(nodeIndex), node.action());
            if (node.appearanceOverride() instanceof PhysicalAppearanceOverride.Unparsed unparsed) {
                entries.add(OpaqueRawTreeEntry.appearance(
                        entries.size(),
                        new OpaqueRawTreeLocation.AppearanceOverride(nodeIndex),
                        unparsed.raw()));
            }
        }
        if (document.appearance() instanceof PhysicalTopAppearance.Unparsed unparsed) {
            entries.add(OpaqueRawTreeEntry.appearance(
                    entries.size(), OpaqueRawTreeLocation.TopAppearance.INSTANCE, unparsed.raw()));
        }
        return new OpaqueRawTreeTable(entries);
    }

    int size() {
        return entries.size();
    }

    OpaqueRawTreeEntry entryAt(int tokenId) {
        if (tokenId < 0 || tokenId >= entries.size()) {
            throw new IllegalArgumentException("unknown opaque token ID");
        }
        return entries.get(tokenId);
    }

    OpaqueRawTreeEntry entryAt(OpaqueRawTreeLocation location) {
        Objects.requireNonNull(location, "location");
        return findAt(location)
                .orElseThrow(() -> new IllegalArgumentException("location has no opaque token"));
    }

    Optional<OpaqueRawTreeEntry> findAt(OpaqueRawTreeLocation location) {
        Objects.requireNonNull(location, "location");
        return entries.stream()
                .filter(entry -> entry.location().equals(location))
                .findFirst();
    }

    List<OpaqueRawTreeEntry> entries() {
        return entries;
    }

    private static void addDefinition(
            List<OpaqueRawTreeEntry> entries,
            OpaqueRawTreeLocation location,
            PhysicalDefinitionEnvelope definition) {
        entries.add(OpaqueRawTreeEntry.definition(
                entries.size(),
                location,
                definition.typeId(),
                definition.schemaVersion(),
                definition.payload()));
    }

    @Override
    public String toString() {
        return "OpaqueRawTreeTable[entryCount=" + entries.size() + "]";
    }
}

record OpaqueRawTreeEntry(
        int tokenId,
        OpaqueRawTreeLocation location,
        Optional<ResourceLocation> definitionTypeId,
        OptionalInt definitionSchemaVersion,
        RawTreeEnvelope rawTree) {
    OpaqueRawTreeEntry {
        if (tokenId < 0) {
            throw new IllegalArgumentException("tokenId must be non-negative");
        }
        Objects.requireNonNull(location, "location");
        definitionTypeId = Objects.requireNonNull(definitionTypeId, "definitionTypeId");
        definitionSchemaVersion = Objects.requireNonNull(
                definitionSchemaVersion, "definitionSchemaVersion");
        Objects.requireNonNull(rawTree, "rawTree");
        var definitionLocation = location instanceof OpaqueRawTreeLocation.TriggerPayload
                || location instanceof OpaqueRawTreeLocation.ActionPayload;
        if (definitionLocation != definitionTypeId.isPresent()
                || definitionLocation != definitionSchemaVersion.isPresent()) {
            throw new IllegalArgumentException("definition binding must match the typed token location");
        }
        if (definitionSchemaVersion.isPresent() && definitionSchemaVersion.getAsInt() < 0) {
            throw new IllegalArgumentException("definition schema version must be non-negative");
        }
    }

    static OpaqueRawTreeEntry definition(
            int tokenId,
            OpaqueRawTreeLocation location,
            ResourceLocation typeId,
            int schemaVersion,
            RawTreeEnvelope rawTree) {
        return new OpaqueRawTreeEntry(
                tokenId,
                location,
                Optional.of(Objects.requireNonNull(typeId, "typeId")),
                OptionalInt.of(schemaVersion),
                rawTree);
    }

    static OpaqueRawTreeEntry appearance(
            int tokenId,
            OpaqueRawTreeLocation location,
            RawTreeEnvelope rawTree) {
        return new OpaqueRawTreeEntry(
                tokenId, location, Optional.empty(), OptionalInt.empty(), rawTree);
    }

    @Override
    public String toString() {
        return "OpaqueRawTreeEntry[tokenId=" + tokenId
                + ", location=" + location
                + ", context=" + rawTree.context()
                + ", byteCount=" + rawTree.byteCount() + "]";
    }
}
