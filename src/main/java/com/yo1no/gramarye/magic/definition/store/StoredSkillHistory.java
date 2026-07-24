package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import java.util.Collections;
import java.util.Comparator;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
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

    StoredSkillHistory append(SkillDocument document) {
        Objects.requireNonNull(document, "document");
        if (revisions.containsKey(document.revision())) {
            throw new IllegalArgumentException("replacement revision must be new");
        }

        var replacement = new TreeMap<SkillRevision, SkillDocument>(
                Comparator.comparingInt(SkillRevision::value));
        replacement.putAll(revisions);
        replacement.put(document.revision(), document);
        return new StoredSkillHistory(owner, replacement);
    }

    StoredSkillHistory retainRevisions(Set<SkillRevision> retainedRevisions) {
        Objects.requireNonNull(retainedRevisions, "retainedRevisions");
        if (retainedRevisions.isEmpty()) {
            throw new IllegalArgumentException("retained revisions must not be empty");
        }
        for (var revision : retainedRevisions) {
            Objects.requireNonNull(revision, "retained revision");
            if (!revisions.containsKey(revision)) {
                throw new IllegalArgumentException("retained revision is not in this history");
            }
        }
        if (!retainedRevisions.contains(revisions.lastKey())) {
            throw new IllegalArgumentException("retained revisions must include the latest");
        }
        if (retainedRevisions.size() == revisions.size()) {
            return this;
        }

        var replacement = new TreeMap<SkillRevision, SkillDocument>(
                Comparator.comparingInt(SkillRevision::value));
        revisions.forEach((revision, document) -> {
            if (retainedRevisions.contains(revision)) {
                replacement.put(revision, document);
            }
        });
        return new StoredSkillHistory(owner, replacement);
    }

    @Override
    public String toString() {
        return "StoredSkillHistory[owner=" + owner
                + ", revisionCount=" + revisions.size() + "]";
    }
}
