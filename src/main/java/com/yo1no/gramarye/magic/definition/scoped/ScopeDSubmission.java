package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.CatalogGeneration;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ExactReference;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.FormalDocument;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.Identity;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ManifestDraft;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ManifestReference;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ManifestRevision;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SkillManifest;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellDefinition;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellDraft;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellIdentity;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellReference;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellRevision;
import java.util.HashMap;
import java.util.Optional;

/** Authoring checks before an isolated catalog candidate is validated as a whole. */
final class ScopeDSubmission {
    private ScopeDSubmission() {
    }

    static SpellDefinition prepare(
            ScopeDGraph base, SkillOwnerId authorizedOwner, SpellDraft draft, SpellReference allocation) {
        var previous = requireBase(base, authorizedOwner, draft.identity(), draft.baseGeneration(),
                draft.baseRevision().map(SpellRevision::value), allocation);
        requireValid(base, draft);
        var draftRef = new SpellReference(draft.identity(), allocation.revision());
        var formal = ScopeDData.formalize(draft, draftRef);
        if (!draft.identity().equals(allocation.identity())) {
            requireRebindable(formal);
            var mapping = new HashMap<SpellIdentity, SpellIdentity>();
            for (var reference : formal.dependencies()) {
                var target = reference.target();
                if (target.equals(allocation.identity())) {
                    throw new Rejected(Reason.CONFLICTING_DRAFT_REFERENCE);
                }
                mapping.put(target, target);
            }
            mapping.put(draft.identity(), allocation.identity());
            return new SpellDefinition(allocation, ScopeDData.remapSpellBody(formal.body(), mapping), Optional.empty());
        }
        return previous.flatMap(FormalDocument::provenance)
                .map(provenance -> new SpellDefinition(formal.reference(), formal.body(), Optional.of(provenance)))
                .orElse(formal);
    }

    static SkillManifest prepare(
            ScopeDGraph base, SkillOwnerId authorizedOwner, ManifestDraft draft, ManifestReference allocation) {
        var previous = requireBase(base, authorizedOwner, draft.identity(), draft.baseGeneration(),
                draft.baseRevision().map(ManifestRevision::value), allocation);
        requireValid(base, draft);
        var draftRef = new ManifestReference(draft.identity(), allocation.revision());
        var formal = ScopeDData.formalize(draft, draftRef);
        if (!draft.identity().equals(allocation.identity())) {
            requireRebindable(formal);
            return new SkillManifest(allocation, formal.body(), Optional.empty());
        }
        return previous.flatMap(FormalDocument::provenance)
                .map(provenance -> new SkillManifest(formal.reference(), formal.body(), Optional.of(provenance)))
                .orElse(formal);
    }

    private static Optional<FormalDocument> requireBase(
            ScopeDGraph base, SkillOwnerId authorizedOwner, Identity identity,
            CatalogGeneration generation, Optional<Long> revision, ExactReference allocation) {
        if (!base.owner().equals(authorizedOwner) || !identity.owner().equals(authorizedOwner)
                || !allocation.identity().owner().equals(authorizedOwner)) {
            throw new Rejected(Reason.FOREIGN_SCOPE_OR_WRONG_ALLOCATION);
        }
        if (!generation.equals(base.generation())) {
            throw new Rejected(Reason.STALE_GENERATION);
        }
        var previous = base.find(identity);
        var current = previous.map(FormalDocument::reference).map(ExactReference::revisionNumber);
        if (!revision.equals(current)) {
            throw new Rejected(Reason.STALE_REVISION);
        }
        if (current.isPresent()) {
            if (!identity.equals(allocation.identity())) {
                throw new Rejected(Reason.FOREIGN_SCOPE_OR_WRONG_ALLOCATION);
            }
            if (allocation.revisionNumber() <= current.orElseThrow()) {
                throw new Rejected(Reason.NON_ADVANCING_ALLOCATION);
            }
        } else if (base.find(allocation.identity()).isPresent()) {
            throw new Rejected(Reason.ALLOCATION_ALREADY_CURRENT);
        }
        return previous;
    }

    private static void requireRebindable(FormalDocument formal) {
        if (formal.hasOpaqueRaw()) {
            throw new Rejected(Reason.UNSAFE_RAW_REBIND);
        }
    }

    private static void requireValid(ScopeDGraph base, ScopeDData.ScopedDocument draft) {
        if (!ScopeDData.validate(draft, base.context()).valid()) {
            throw new Rejected(Reason.INVALID_DRAFT);
        }
    }

    enum Reason {
        FOREIGN_SCOPE_OR_WRONG_ALLOCATION, STALE_GENERATION, STALE_REVISION,
        NON_ADVANCING_ALLOCATION, ALLOCATION_ALREADY_CURRENT, INVALID_DRAFT,
        UNSAFE_RAW_REBIND, CONFLICTING_DRAFT_REFERENCE
    }

    static final class Rejected extends IllegalArgumentException {
        private final Reason reason;

        private Rejected(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }

        Reason reason() {
            return reason;
        }
    }
}
