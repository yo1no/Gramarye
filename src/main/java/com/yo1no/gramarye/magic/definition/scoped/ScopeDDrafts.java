package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;

/** Owner-private draft category, with independent physical and optimistic edit state. */
final class ScopeDDrafts {
    private static final int VERSION = 1;
    private final SkillOwnerId owner;
    private final ScopeDStorage storage;

    ScopeDDrafts(SkillOwnerId owner, ScopeDStorage storage) {
        if (!owner.equals(storage.owner())) {
            throw new IllegalArgumentException("Draft principal and physical owner differ");
        }
        this.owner = owner;
        this.storage = storage;
    }

    Snapshot read() throws IOException {
        byte[] bytes = storage.readDrafts().orElseGet(() -> new byte[0]);
        return bytes.length == 0 ? new Snapshot(this, bytes, 0, Map.of()) : decode(bytes);
    }

    Snapshot replace(Snapshot expected, List<Draft> replacements) throws IOException {
        if (expected.source != this || replacements.size() > MAX_DRAFTS) {
            throw new IOException("Draft source or capacity mismatch");
        }
        Map<Identity, Draft> drafts = new HashMap<>();
        for (Draft draft : replacements) {
            if (!owner.equals(draft.identity().owner()) || drafts.putIfAbsent(draft.identity(), draft) != null) {
                throw new IOException("Foreign or duplicate draft route");
            }
        }
        synchronized (storage) {
            if (!Arrays.equals(expected.bytes, read().bytes)) {
                throw new IOException("Stale draft snapshot");
            }
            if (expected.editVersion == Long.MAX_VALUE) {
                throw new IOException("Draft edit version exhausted");
            }
            storage.writeDrafts(encode(expected.editVersion + 1, drafts));
            return read();
        }
    }

    private byte[] encode(long editVersion, Map<Identity, Draft> drafts) throws IOException {
        var bytes = new ScopeDBoundedOutput(MAX_DRAFT_PAYLOAD);
        try (var out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeLong(editVersion);
            out.writeShort(drafts.size());
            var ordered = drafts.values().stream().sorted(Comparator
                    .comparingInt((Draft draft) -> draft instanceof SpellDraft ? 0 : 1)
                    .thenComparingLong(draft -> switch (draft) {
                        case SpellDraft spell -> spell.identity().id().value();
                        case ManifestDraft manifest -> manifest.identity().id().value();
                    })).toList();
            for (Draft draft : ordered) {
                ScopeDCatalogImage.writeDocument(out, draft);
            }
        }
        return bytes.copyBytes();
    }

    private Snapshot decode(byte[] bytes) throws IOException {
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readUnsignedByte() != VERSION) {
                throw new IOException("Unsupported draft image version");
            }
            long editVersion = in.readLong();
            if (editVersion <= 0) {
                throw new IOException("Invalid draft image edit version");
            }
            int count = ScopeDCatalogImage.count(in, MAX_DRAFTS);
            Map<Identity, Draft> drafts = new HashMap<>();
            for (int index = 0; index < count; index++) {
                if (!(ScopeDCatalogImage.readDocument(in) instanceof Draft draft)
                        || !owner.equals(draft.identity().owner()) || drafts.putIfAbsent(draft.identity(), draft) != null) {
                    throw new IOException("Invalid draft route in owner image");
                }
            }
            ScopeDCatalogImage.eof(in);
            return new Snapshot(this, bytes, editVersion, drafts);
        }
    }

    static final class Snapshot {
        private final ScopeDDrafts source;
        private final byte[] bytes;
        private final long editVersion;
        private final Map<Identity, Draft> drafts;

        private Snapshot(ScopeDDrafts source, byte[] bytes, long editVersion, Map<Identity, Draft> drafts) {
            this.source = source;
            this.bytes = bytes;
            this.editVersion = editVersion;
            this.drafts = Map.copyOf(drafts);
        }

        Map<Identity, Draft> drafts() { return drafts; }
    }
}
