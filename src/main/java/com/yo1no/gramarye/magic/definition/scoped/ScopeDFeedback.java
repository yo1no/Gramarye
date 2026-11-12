package com.yo1no.gramarye.magic.definition.scoped;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.yo1no.gramarye.magic.definition.scoped.ScopeDCompilerDiagnostics.Code;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDCompilerDiagnostics.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDFrontend.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDProgram.*;

/** Conservative causal graph over creation and internally reachable event handlers. */
final class ScopeDFeedback {
    private record Vertex(SpellReference spell, Optional<DescriptorKey> event) {}
    record Work(int steps, int scratch) {}

    static Work analyze(Map<SpellReference, SpellUnit> units, Context context, ScopeDCompilerDiagnostics diagnostics) {
        var vertices = new ArrayList<Vertex>();
        var indices = new LinkedHashMap<Vertex, Integer>();
        for (var unit : units.values()) {
            index(vertices, indices, creation(unit.reference()));
            for (var handler : unit.handlers()) {
                index(vertices, indices, event(unit.reference(), handler.event()));
            }
        }
        var adjacency = new ArrayList<List<Integer>>(vertices.size());
        var indegree = new int[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            adjacency.add(new ArrayList<>());
        }
        int edges = 0;
        int emittedKeys = 0;
        for (var unit : units.values()) {
            for (var handler : unit.handlers()) {
                int origin = indices.get(event(unit.reference(), handler.event()));
                var event = context.event(handler.event()).orElseThrow();
                if (event.origin() == EventOrigin.CREATION) {
                    edges = edge(adjacency, indegree, edges, indices.get(creation(unit.reference())), origin);
                }
                for (int pc = handler.start(); pc < handler.end(); pc++) {
                    var instruction = unit.instructions().get(pc);
                    if (instruction instanceof CreateChild child) {
                        var target = units.get(child.exactTarget());
                        if (target == null) {
                            diagnostics.error(Stage.FEEDBACK, Code.MISSING_DEPENDENCY,
                                    Location.at(unit.reference(), "create_spell"));
                            continue;
                        }
                        validateInputs(unit.reference(), handler.id(), target, child, diagnostics);
                        edges = edge(adjacency, indegree, edges, origin, indices.get(creation(target.reference())));
                    } else if (instruction instanceof Effect effect) {
                        var contract = context.effect(ScopeDLowering.key(DescriptorDomain.EFFECT,
                                effect.descriptor())).orElseThrow();
                        // Even a normally external event is internal when an effect can emit it.
                        for (var key : contract.emittedEvents()) {
                            emittedKeys = Math.incrementExact(emittedKeys);
                            Integer target = indices.get(event(unit.reference(), key));
                            if (target != null) {
                                edges = edge(adjacency, indegree, edges, origin, target);
                            }
                        }
                    }
                }
            }
        }
        var ready = new ArrayDeque<Integer>();
        for (int i = 0; i < indegree.length; i++) {
            if (indegree[i] == 0) {
                ready.addLast(i);
            }
        }
        int processed = 0;
        while (!ready.isEmpty()) {
            processed = Math.incrementExact(processed);
            for (int target : adjacency.get(ready.removeFirst())) {
                if (--indegree[target] == 0) {
                    ready.addLast(target);
                }
            }
        }
        if (processed != vertices.size()) {
            for (int i = 0; i < indegree.length; i++) {
                if (indegree[i] != 0) {
                    diagnostics.error(Stage.FEEDBACK, Code.RUNTIME_POLICY_REQUIRED,
                            Location.at(vertices.get(i).spell(), "creation_or_event_feedback"));
                    break;
                }
            }
        }
        int scratch = Math.addExact(vertices.size(), edges);
        int work = Math.addExact(Math.multiplyExact(3, scratch), emittedKeys);
        if (emittedKeys > MAX_COMPILER_EMISSION_CHECKS || work > MAX_COMPILER_FEEDBACK_WORK) {
            throw new ScopeDLowering.Capacity();
        }
        return new Work(work, scratch);
    }

    private static void validateInputs(SpellReference source, HandlerId handler, SpellUnit target,
                                       CreateChild child, ScopeDCompilerDiagnostics diagnostics) {
        var supplied = new LinkedHashMap<String, ValueType>();
        for (var input : child.inputs()) {
            supplied.put(input.name(), input.type());
        }
        boolean valid = true;
        for (var input : target.inputs()) {
            var type = supplied.remove(input.name());
            valid &= type == null ? !input.field().required() : type == input.field().type();
        }
        if (!valid || !supplied.isEmpty()) {
            diagnostics.error(Stage.FEEDBACK, Code.CREATION_INPUT,
                    new Location(Optional.of(source), Optional.of(handler), Optional.empty(), "create_spell"));
        }
    }

    private static int edge(List<List<Integer>> adjacency, int[] indegree, int count, int from, int to) {
        if (count >= MAX_COMPILER_FEEDBACK_EDGES) {
            throw new ScopeDLowering.Capacity();
        }
        adjacency.get(from).add(to);
        indegree[to] = Math.incrementExact(indegree[to]);
        return Math.incrementExact(count);
    }

    private static void index(List<Vertex> vertices, Map<Vertex, Integer> indices, Vertex vertex) {
        if (vertices.size() >= MAX_COMPILER_FEEDBACK_VERTICES) {
            throw new ScopeDLowering.Capacity();
        }
        indices.put(vertex, vertices.size());
        vertices.add(vertex);
    }

    private static Vertex creation(SpellReference spell) { return new Vertex(spell, Optional.empty()); }
    private static Vertex event(SpellReference spell, DescriptorKey event) { return new Vertex(spell, Optional.of(event)); }

    private ScopeDFeedback() {}
}
