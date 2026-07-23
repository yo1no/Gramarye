package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import java.util.Collections;
import java.util.Comparator;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

final class StoredSkillHistory {
    private final SkillOwnerId owner;
    private final NavigableMap<SkillRevision, SkillDocument> revisions;

    StoredSkillHistory(
            SkillOwnerId owner,
            NavigableMap<SkillRevision, SkillDocument> revisions) {
        this.owner = Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(revisions, "revisions");

        var copy = new TreeMap<SkillRevision, SkillDocument>(
                Comparator.comparingInt(SkillRevision::value));
        revisions.forEach((revision, document) -> copy.put(
                Objects.requireNonNull(revision, "revision"),
                Objects.requireNonNull(document, "document")));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("stored skill history must not be empty");
        }
        this.revisions = Collections.unmodifiableNavigableMap(copy);
    }

    SkillOwnerId owner() {
        return owner;
    }

    NavigableMap<SkillRevision, SkillDocument> revisions() {
        return revisions;
    }

    @Override
    public String toString() {
        return "StoredSkillHistory[owner=" + owner
                + ", revisionCount=" + revisions.size() + "]";
    }
}
