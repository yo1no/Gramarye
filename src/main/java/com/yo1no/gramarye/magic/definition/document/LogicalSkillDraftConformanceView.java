package com.yo1no.gramarye.magic.definition.document;

import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/** Tokenizes every opaque Draft raw location and verifies exact V0 reinsertion invariants. */
final class LogicalSkillDraftConformanceView {
    private LogicalSkillDraftConformanceView() {
    }

    static BuildResult build(PhysicalSkillDraft draft) {
        Objects.requireNonNull(draft, "draft");
        try {
            var tokenized = PhysicalSkillDraftNbt.encode(draft);
            var entries = new ArrayList<OpaqueDraftRawTreeTable.Entry>();
            var nodes = (ListTag) tokenized.get("nodes");
            for (var index = 0; index < draft.nodes().size(); index++) {
                var physicalNode = draft.nodes().get(index);
                var node = (CompoundTag) nodes.get(index);
                if (physicalNode.trigger() instanceof PhysicalDraftTriggerSlot.Present present) {
                    tokenizeDefinition(
                            node,
                            "trigger",
                            new OpaqueDraftRawTreeTable.Location.Trigger(index),
                            present.definition().payload(),
                            entries);
                }
                if (physicalNode.action() instanceof PhysicalDraftActionSlot.Present present) {
                    tokenizeDefinition(
                            node,
                            "action",
                            new OpaqueDraftRawTreeTable.Location.Action(index),
                            present.definition().payload(),
                            entries);
                }
                if (physicalNode.appearanceOverride()
                        instanceof PhysicalAppearanceOverride.Unparsed unparsed) {
                    var override = (CompoundTag) node.get("appearance_override");
                    addToken(
                            override,
                            "raw",
                            new OpaqueDraftRawTreeTable.Location.AppearanceOverride(index),
                            unparsed.raw(),
                            entries);
                }
            }
            if (draft.appearance() instanceof PhysicalTopAppearance.Unparsed unparsed) {
                addToken(
                        (CompoundTag) tokenized.get("appearance"),
                        "raw",
                        OpaqueDraftRawTreeTable.Location.TopAppearance.INSTANCE,
                        unparsed.raw(),
                        entries);
            }
            var table = new OpaqueDraftRawTreeTable(tokenized, entries);
            return new Built(tokenized.copy(), table);
        } catch (PhysicalSkillDraftNbt.DraftFormatException | RuntimeException exception) {
            return new BuildFailure(exception.getClass().getName());
        }
    }

    static ReinsertionResult reinsert(
            CompoundTag migrated,
            OpaqueDraftRawTreeTable table) {
        Objects.requireNonNull(migrated, "migrated");
        Objects.requireNonNull(table, "table");
        try {
            if (!(migrated.get("draft_schema_version") instanceof IntTag migratedVersion)) {
                return new ReinsertionFailure();
            }
            var expected = table.originalTokenizedTree();
            expected.putInt("draft_schema_version", migratedVersion.getAsInt());
            if (!expected.equals(migrated)) {
                return new ReinsertionFailure();
            }

            var reinserted = migrated.copy();
            for (var entry : table.entries()) {
                var target = rawTarget(reinserted, entry.location());
                if (!(target.parent().get(target.field()) instanceof StringTag token)
                        || !token.getAsString().equals(entry.token())) {
                    return new ReinsertionFailure();
                }
                target.parent().put(target.field(), entry.raw().encodePhysical());
            }
            return new Reinserted(PhysicalSkillDraftNbt.decode(reinserted));
        } catch (PhysicalSkillDraftNbt.DraftFormatException | RuntimeException exception) {
            return new ReinsertionFailure();
        }
    }

    private static void tokenizeDefinition(
            CompoundTag node,
            String slotField,
            OpaqueDraftRawTreeTable.Location location,
            RawTreeEnvelope raw,
            ArrayList<OpaqueDraftRawTreeTable.Entry> entries) {
        var slot = (CompoundTag) node.get(slotField);
        var definition = (CompoundTag) slot.get("definition");
        addToken(definition, "payload", location, raw, entries);
    }

    private static void addToken(
            CompoundTag parent,
            String field,
            OpaqueDraftRawTreeTable.Location location,
            RawTreeEnvelope raw,
            ArrayList<OpaqueDraftRawTreeTable.Entry> entries) {
        var token = "__gramarye_draft_raw_" + entries.size();
        parent.putString(field, token);
        entries.add(new OpaqueDraftRawTreeTable.Entry(token, location, raw));
    }

    private static RawTarget rawTarget(
            CompoundTag root,
            OpaqueDraftRawTreeTable.Location location) {
        if (location instanceof OpaqueDraftRawTreeTable.Location.TopAppearance) {
            return new RawTarget((CompoundTag) root.get("appearance"), "raw");
        }
        var nodes = (ListTag) root.get("nodes");
        var nodeIndex = switch (location) {
            case OpaqueDraftRawTreeTable.Location.Trigger trigger -> trigger.nodeIndex();
            case OpaqueDraftRawTreeTable.Location.Action action -> action.nodeIndex();
            case OpaqueDraftRawTreeTable.Location.AppearanceOverride override -> override.nodeIndex();
            case OpaqueDraftRawTreeTable.Location.TopAppearance ignored -> throw new IllegalStateException();
        };
        var node = (CompoundTag) nodes.get(nodeIndex);
        if (location instanceof OpaqueDraftRawTreeTable.Location.AppearanceOverride) {
            return new RawTarget((CompoundTag) node.get("appearance_override"), "raw");
        }
        var slotField = location instanceof OpaqueDraftRawTreeTable.Location.Trigger
                ? "trigger"
                : "action";
        var slot = (CompoundTag) node.get(slotField);
        return new RawTarget((CompoundTag) slot.get("definition"), "payload");
    }

    sealed interface BuildResult permits Built, BuildFailure {
    }

    record Built(CompoundTag logicalTree, OpaqueDraftRawTreeTable table) implements BuildResult {
        Built {
            logicalTree = Objects.requireNonNull(logicalTree, "logicalTree").copy();
            Objects.requireNonNull(table, "table");
        }

        @Override
        public CompoundTag logicalTree() {
            return logicalTree.copy();
        }
    }

    record BuildFailure(String exceptionClassName) implements BuildResult {
        BuildFailure {
            Objects.requireNonNull(exceptionClassName, "exceptionClassName");
        }
    }

    sealed interface ReinsertionResult permits Reinserted, ReinsertionFailure {
    }

    record Reinserted(PhysicalSkillDraft draft) implements ReinsertionResult {
        Reinserted {
            Objects.requireNonNull(draft, "draft");
        }
    }

    record ReinsertionFailure() implements ReinsertionResult {
    }

    private record RawTarget(CompoundTag parent, String field) {
        private RawTarget {
            Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(field, "field");
        }
    }
}
