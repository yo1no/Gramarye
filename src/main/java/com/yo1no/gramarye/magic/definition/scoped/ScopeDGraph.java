package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.CatalogGeneration;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.DescriptorContext;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ExactReference;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.FormalDocument;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.Identity;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ManifestIdentity;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SkillManifest;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellDefinition;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellIdentity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Rebuildable dependency index over one immutable owner catalog view. */
final class ScopeDGraph {
    private static final Comparator<Identity> ROUTE_ORDER =
            Comparator.<Identity>comparingInt(identity -> identity instanceof SpellIdentity ? 0 : 1)
                    .thenComparingLong(identity -> switch (identity) {
                        case SpellIdentity spell -> spell.id().value();
                        case ManifestIdentity manifest -> manifest.id().value();
                    });

    private final SkillOwnerId owner;
    private final CatalogGeneration generation;
    private final DescriptorContext context;
    private final Map<Identity, FormalDocument> documents;
    private final Map<Identity, List<SpellIdentity>> forward;
    private final Map<SpellIdentity, Set<Identity>> reverse;
    private final Set<Identity> unavailable;

    private ScopeDGraph(
            SkillOwnerId owner, CatalogGeneration generation, DescriptorContext context,
            Map<Identity, FormalDocument> documents, Map<Identity, List<SpellIdentity>> forward,
            Map<SpellIdentity, Set<Identity>> reverse, Set<Identity> unavailable) {
        this.owner = owner;
        this.generation = generation;
        this.context = context;
        this.documents = documents;
        this.forward = forward;
        this.reverse = reverse;
        this.unavailable = unavailable;
    }

    static ScopeDGraph build(
            SkillOwnerId owner, CatalogGeneration generation, DescriptorContext context,
            Map<Identity, FormalDocument> documents) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(documents, "documents");
        if (documents.size() > ScopeDLimits.MAX_ROUTES) {
            throw reject(Reason.ROUTE_LIMIT);
        }

        var ordered = new LinkedHashMap<Identity, FormalDocument>();
        var forward = new LinkedHashMap<Identity, List<SpellIdentity>>();
        var reverse = new LinkedHashMap<SpellIdentity, Set<Identity>>();
        var unavailable = new LinkedHashSet<Identity>();
        int edgeCount = 0;
        for (var route : documents.keySet().stream().sorted(ROUTE_ORDER).toList()) {
            var body = Objects.requireNonNull(documents.get(route), "route body");
            if (!owner.equals(route.owner()) || !route.equals(body.reference().identity())) {
                throw reject(Reason.INVALID_ROUTE);
            }
            ordered.put(route, body);
            var edges = body.dependencies().stream().map(ScopeDData.SpellIdentityReference::target).toList();
            edgeCount = Math.addExact(edgeCount, edges.size());
            if (edgeCount > ScopeDLimits.MAX_GRAPH_EDGES) {
                throw reject(Reason.EDGE_LIMIT);
            }
            forward.put(route, edges);
            for (var target : edges) {
                if (owner.equals(target.owner())) {
                    reverse.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(route);
                }
            }
            if (!ScopeDData.validate(body, context).valid()) {
                unavailable.add(route);
            }
        }
        reverse.replaceAll((ignored, origins) -> Collections.unmodifiableSet(origins));
        return new ScopeDGraph(owner, generation, context, Collections.unmodifiableMap(ordered),
                Map.copyOf(forward), Map.copyOf(reverse), Set.copyOf(unavailable));
    }

    SkillOwnerId owner() {
        return owner;
    }

    CatalogGeneration generation() {
        return generation;
    }

    DescriptorContext context() {
        return context;
    }

    Optional<FormalDocument> find(Identity identity) {
        requireOwner(identity);
        return Optional.ofNullable(documents.get(identity));
    }

    Closure closure(ExactReference root) {
        requireExactRoot(root);
        var budget = new WorkBudget();
        var reachable = visit(Set.of(root.identity()), budget);
        var exact = new LinkedHashMap<ExactReference, FormalDocument>();
        reachable.forEach(identity -> {
            var document = documents.get(identity);
            exact.put(document.reference(), document);
        });
        return new Closure(root, Collections.unmodifiableMap(exact), cyclicComponents(reachable, budget));
    }

    /** Captures exact material needed by roots from one view and one shared traversal budget. */
    Set<ExactReference> exactRootUnion(List<ExactReference> roots) {
        if (roots.size() > ScopeDLimits.MAX_ROUTES) {
            throw reject(Reason.ROOT_LIMIT);
        }
        var budget = new WorkBudget();
        var seeds = new LinkedHashSet<Identity>();
        for (var root : roots) {
            budget.consume();
            requireExactRoot(root);
            seeds.add(root.identity());
        }
        var exact = new LinkedHashSet<ExactReference>();
        for (var identity : visit(seeds, budget)) {
            exact.add(documents.get(identity).reference());
        }
        return Set.copyOf(exact);
    }

    private void requireExactRoot(ExactReference root) {
        var body = find(root.identity()).orElseThrow(() -> reject(Reason.EXACT_ROOT_UNAVAILABLE));
        if (!body.reference().equals(root)) {
            throw reject(Reason.EXACT_ROOT_UNAVAILABLE);
        }
    }

    static CandidateValidation validateCandidate(ScopeDGraph base, ScopeDGraph candidate) {
        return validateCandidate(base, candidate, false);
    }

    static CandidateValidation validateCandidate(
            ScopeDGraph base, ScopeDGraph candidate, boolean contextIdentityChanged) {
        if (!base.owner.equals(candidate.owner)
                || base.generation.value() == Long.MAX_VALUE
                || candidate.generation.value() != base.generation.value() + 1) {
            throw reject(Reason.STALE_OR_FOREIGN_BASE);
        }

        var routes = new LinkedHashSet<>(base.documents.keySet());
        routes.addAll(candidate.documents.keySet());
        var changed = new LinkedHashSet<Identity>();
        var seeds = new LinkedHashSet<Identity>();
        boolean revalidateAll = contextIdentityChanged || !base.context.equals(candidate.context);
        for (var route : routes) {
            var before = base.documents.get(route);
            var after = candidate.documents.get(route);
            if (!Objects.equals(before, after)) {
                if (before != null && after != null && before.reference().equals(after.reference())) {
                    throw reject(Reason.IMMUTABLE_BODY_CHANGED);
                }
                if (before != null && after != null && sameContent(before, after)) {
                    throw reject(Reason.UNCHANGED_REVISION);
                }
                changed.add(route);
                seeds.add(route);
            }
            if (revalidateAll) {
                seeds.add(route);
            }
        }
        if (changed.size() > ScopeDLimits.MAX_BATCH) {
            throw reject(Reason.BATCH_LIMIT);
        }

        var budget = new WorkBudget();
        var affected = base.reverseReachable(seeds, budget);
        affected.addAll(candidate.reverseReachable(seeds, budget));
        var revalidated = new LinkedHashSet<ExactReference>();
        for (var root : affected) {
            var surviving = candidate.documents.get(root);
            if (surviving != null) {
                candidate.visit(Set.of(root), budget);
                revalidated.add(surviving.reference());
            }
        }
        return new CandidateValidation(Set.copyOf(affected), Set.copyOf(revalidated));
    }

    private static boolean sameContent(FormalDocument before, FormalDocument after) {
        return switch (before) {
            case SpellDefinition spell -> after instanceof SpellDefinition other
                    && spell.body().equals(other.body()) && spell.provenance().equals(other.provenance());
            case SkillManifest manifest -> after instanceof SkillManifest other
                    && manifest.body().equals(other.body()) && manifest.provenance().equals(other.provenance());
        };
    }

    private LinkedHashSet<Identity> reverseReachable(Set<Identity> seeds, WorkBudget budget) {
        var reached = new LinkedHashSet<>(seeds);
        var queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            budget.consume();
            if (queue.removeFirst() instanceof SpellIdentity target) {
                for (var dependent : reverse.getOrDefault(target, Set.of())) {
                    budget.consume();
                    if (reached.add(dependent)) {
                        queue.addLast(dependent);
                    }
                }
            }
        }
        return reached;
    }

    private Set<Identity> visit(Set<Identity> roots, WorkBudget budget) {
        var reached = new LinkedHashSet<>(roots);
        var queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            budget.consume();
            var route = queue.removeFirst();
            var edges = forward.get(route);
            if (edges == null) {
                throw reject(Reason.MISSING_DEPENDENCY);
            }
            for (var target : edges) {
                budget.consume();
                requireOwner(target);
                if (reached.add(target)) {
                    queue.addLast(target);
                }
            }
            if (unavailable.contains(route)) {
                throw reject(Reason.UNAVAILABLE_DOCUMENT);
            }
        }
        return reached;
    }

    /** Computes Spell SCCs only. Manifest membership remains an independent root projection. */
    private List<Set<SpellIdentity>> cyclicComponents(Set<Identity> reached, WorkBudget budget) {
        var seen = new LinkedHashSet<SpellIdentity>();
        var finishOrder = new ArrayDeque<SpellIdentity>();
        var stack = new ArrayDeque<SearchFrame>();
        for (var identity : reached) {
            if (identity instanceof SpellIdentity root && seen.add(root)) {
                visitInFinishOrder(root, seen, finishOrder, stack, budget);
            }
        }

        var components = new ArrayList<Set<SpellIdentity>>();
        seen.clear();
        while (!finishOrder.isEmpty()) {
            var root = finishOrder.removeLast();
            if (!seen.add(root)) {
                continue;
            }
            var component = visitTranspose(root, reached, seen, budget);
            if (component.size() > 1 || forward.get(root).contains(root)) {
                components.add(Set.copyOf(component));
            }
        }
        return List.copyOf(components);
    }

    private void visitInFinishOrder(
            SpellIdentity root, Set<SpellIdentity> seen, Deque<SpellIdentity> finishOrder,
            Deque<SearchFrame> stack, WorkBudget budget) {
        stack.push(new SearchFrame(root, forward.get(root).iterator()));
        while (!stack.isEmpty()) {
            budget.consume();
            var frame = stack.element();
            if (frame.edges().hasNext()) {
                var target = frame.edges().next();
                if (seen.add(target)) {
                    stack.push(new SearchFrame(target, forward.get(target).iterator()));
                }
            } else {
                finishOrder.addLast(stack.pop().identity());
            }
        }
    }

    private Set<SpellIdentity> visitTranspose(
            SpellIdentity root, Set<Identity> reached, Set<SpellIdentity> seen, WorkBudget budget) {
        var queue = new ArrayDeque<SpellIdentity>();
        var component = new LinkedHashSet<SpellIdentity>();
        queue.addLast(root);
        while (!queue.isEmpty()) {
            budget.consume();
            var identity = queue.removeFirst();
            component.add(identity);
            for (var origin : reverse.getOrDefault(identity, Set.of())) {
                budget.consume();
                if (origin instanceof SpellIdentity spell && reached.contains(spell) && seen.add(spell)) {
                    queue.addLast(spell);
                }
            }
        }
        return component;
    }

    private void requireOwner(Identity identity) {
        if (!owner.equals(identity.owner())) {
            throw reject(Reason.FOREIGN_SCOPE);
        }
    }

    private static Rejected reject(Reason reason) {
        return new Rejected(reason);
    }

    record Closure(ExactReference root, Map<ExactReference, FormalDocument> documents,
                   List<Set<SpellIdentity>> cyclicComponents) {
    }

    record CandidateValidation(Set<Identity> affected, Set<ExactReference> revalidated) {
    }

    enum Reason {
        INVALID_ROUTE, ROUTE_LIMIT, ROOT_LIMIT, EDGE_LIMIT, WORK_LIMIT, FOREIGN_SCOPE,
        EXACT_ROOT_UNAVAILABLE, MISSING_DEPENDENCY, UNAVAILABLE_DOCUMENT,
        STALE_OR_FOREIGN_BASE, IMMUTABLE_BODY_CHANGED, UNCHANGED_REVISION, BATCH_LIMIT
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

    private record SearchFrame(SpellIdentity identity, Iterator<SpellIdentity> edges) {
    }

    private static final class WorkBudget {
        private int remaining = ScopeDLimits.MAX_GRAPH_WORK;

        void consume() {
            if (remaining-- == 0) {
                throw reject(Reason.WORK_LIMIT);
            }
        }
    }
}
