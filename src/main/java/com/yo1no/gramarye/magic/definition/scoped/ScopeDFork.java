package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.CatalogGeneration;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ExactReference;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ForkProvenance;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.FormalDocument;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.Identity;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellIdentity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Isolated data transfer over a complete exact source closure and one receiver reservation. */
final class ScopeDFork {
    private ScopeDFork() {
    }

    static Bundle exportBundle(
            ScopeDGraph source, UUID sourceId, SkillOwnerId authorizedSender, ExactReference root) {
        Objects.requireNonNull(sourceId, "sourceId");
        if (!source.owner().equals(authorizedSender) || !source.owner().equals(root.identity().owner())) {
            throw new Rejected(Reason.FOREIGN_SCOPE);
        }
        var closure = source.closure(root);
        if (closure.documents().size() > ScopeDLimits.MAX_BATCH) {
            throw new Rejected(Reason.BATCH_LIMIT);
        }
        var materials = new LinkedHashMap<Identity, FormalDocument>();
        for (var document : closure.documents().values()) {
            if (document.hasOpaqueRaw()) {
                throw new Rejected(Reason.UNSAFE_RAW_REMAP);
            }
            materials.put(document.reference().identity(), document);
        }
        return new Bundle(sourceId, source.owner(), source.generation(), root, Collections.unmodifiableMap(materials));
    }

    /** Prepares immutable receiver documents. The catalog alone reserves and consumes allocations. */
    static Map<Identity, FormalDocument> importBundle(
            Bundle bundle, SkillOwnerId authorizedReceiver, Map<Identity, ExactReference> allocations) {
        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(authorizedReceiver, "authorizedReceiver");
        if (bundle.owner.equals(authorizedReceiver)) {
            throw new Rejected(Reason.FOREIGN_SCOPE);
        }
        if (allocations.size() != bundle.materials.size() || !allocations.keySet().equals(bundle.materials.keySet())) {
            throw new Rejected(Reason.INCOMPLETE_ALLOCATION);
        }

        var allocatedIdentities = new LinkedHashSet<Identity>();
        var spellMapping = new LinkedHashMap<SpellIdentity, SpellIdentity>();
        for (var origin : bundle.materials.keySet()) {
            var allocation = allocations.get(origin);
            if (allocation == null || !authorizedReceiver.equals(allocation.identity().owner())
                    || !origin.getClass().equals(allocation.identity().getClass())
                    || !allocatedIdentities.add(allocation.identity())) {
                throw new Rejected(Reason.INVALID_ALLOCATION);
            }
            if (origin instanceof SpellIdentity spell) {
                spellMapping.put(spell, (SpellIdentity) allocation.identity());
            }
        }

        var remapped = new LinkedHashMap<Identity, FormalDocument>();
        var exactSpellMapping = Map.copyOf(spellMapping);
        for (var entry : bundle.materials.entrySet()) {
            var source = entry.getValue();
            var allocation = allocations.get(entry.getKey());
            var provenance = new ForkProvenance(bundle.sourceId, source.reference(), bundle.generation);
            var receiver = source.remap(allocation, exactSpellMapping, provenance);
            remapped.put(receiver.reference().identity(), receiver);
        }
        return Collections.unmodifiableMap(remapped);
    }

    static final class Bundle {
        private final UUID sourceId;
        private final SkillOwnerId owner;
        private final CatalogGeneration generation;
        private final ExactReference root;
        private final Map<Identity, FormalDocument> materials;

        private Bundle(
                UUID sourceId, SkillOwnerId owner, CatalogGeneration generation, ExactReference root,
                Map<Identity, FormalDocument> materials) {
            this.sourceId = sourceId;
            this.owner = owner;
            this.generation = generation;
            this.root = root;
            this.materials = materials;
        }

        ExactReference root() {
            return root;
        }

        Set<Identity> identities() {
            return materials.keySet();
        }
    }

    enum Reason {
        FOREIGN_SCOPE, BATCH_LIMIT, UNSAFE_RAW_REMAP, INCOMPLETE_ALLOCATION, INVALID_ALLOCATION
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
