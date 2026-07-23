package com.yo1no.gramarye.magic.definition.store;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;
import net.minecraft.resources.ResourceLocation;

final class StoreTestFixtures {
    private static final NodeDocument SHARED_NODE = new NodeDocument(
            envelope("trigger"),
            envelope("action"),
            AppearanceOverrideDocument.none());

    private StoreTestFixtures() {
    }

    static SkillId skillId(long value) {
        return new SkillId(new UUID(0L, value));
    }

    static SkillOwnerId ownerId(long value) {
        return new SkillOwnerId(new UUID(0L, value));
    }

    static SkillRevision revision(int value) {
        return new SkillRevision(value);
    }

    static SkillDocument document(SkillId skillId, int revision) {
        return document(skillId, revision, SkillDocument.CURRENT_SCHEMA_VERSION, List.of(SHARED_NODE));
    }

    static SkillDocument document(
            SkillId skillId,
            int revision,
            int schemaVersion,
            List<NodeDocument> nodes) {
        return new SkillDocument(
                schemaVersion,
                skillId,
                new SkillRevision(revision),
                nodes,
                AppearanceDocument.defaultAppearance());
    }

    static SkillRevisionSnapshot revisionSnapshot(SkillId skillId, int revision) {
        return new SkillRevisionSnapshot(
                new SkillRevision(revision),
                document(skillId, revision));
    }

    static SkillHistorySnapshot history(
            SkillId skillId,
            SkillOwnerId owner,
            int... revisions) {
        var entries = new ArrayList<SkillRevisionSnapshot>(revisions.length);
        for (var revision : revisions) {
            entries.add(revisionSnapshot(skillId, revision));
        }
        return new SkillHistorySnapshot(skillId, owner, entries);
    }

    static SkillDefinitionStoreSnapshot snapshot(SkillHistorySnapshot... histories) {
        return new SkillDefinitionStoreSnapshot(List.of(histories));
    }

    static SkillDefinitionStore restore(SkillDefinitionStoreSnapshot snapshot) {
        return ((SkillDefinitionStoreRestoreResult.Restored) SkillDefinitionStore.restore(snapshot)).store();
    }

    static ArrayList<SkillRevisionSnapshot> revisionSnapshots(SkillId skillId, int count) {
        var revisions = new ArrayList<SkillRevisionSnapshot>(count);
        for (var revision = 0; revision < count; revision++) {
            revisions.add(revisionSnapshot(skillId, revision));
        }
        return revisions;
    }

    static ArrayList<SkillHistorySnapshot> historySnapshots(
            int count,
            long firstSkillId,
            IntFunction<SkillOwnerId> owner,
            int revisionCount) {
        var histories = new ArrayList<SkillHistorySnapshot>(count);
        for (var index = 0; index < count; index++) {
            var skillId = skillId(firstSkillId + index);
            histories.add(new SkillHistorySnapshot(
                    skillId,
                    owner.apply(index),
                    revisionSnapshots(skillId, revisionCount)));
        }
        return histories;
    }

    private static DefinitionEnvelope envelope(String path) {
        return new DefinitionEnvelope(
                ResourceLocation.fromNamespaceAndPath("test", path),
                0,
                new Dynamic<>(JsonOps.INSTANCE, new JsonObject()));
    }
}
