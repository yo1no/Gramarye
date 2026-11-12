package com.yo1no.gramarye.magic.definition.scoped;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.yo1no.gramarye.magic.definition.scoped.ScopeDCompilerDiagnostics.Code;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDCompilerDiagnostics.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDFrontend.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDProgram.*;

/** Validates and lowers a single Spell body in a captured exact binding set. */
final class ScopeDLowering {
    private final SpellDefinition spell;
    private final Context context;
    private final Map<SpellIdentity, SpellReference> bindings;
    private final ScopeDCompilerDiagnostics diagnostics;
    private final List<Instruction> instructions = new ArrayList<>();
    private final List<Value> constants = new ArrayList<>();
    private final List<SourceRange> sourceMap = new ArrayList<>();
    private final List<HandlerProgram> handlers = new ArrayList<>();
    private final List<SettingBinding> settings = new ArrayList<>();
    private int expressionVisits;
    private int folded;
    private int blocks;
    private int operands;
    private int inlineConstants;
    private int createEdges;
    private int descriptorUnits;
    private int sourceBytes;
    private int valueBytes;

    ScopeDLowering(SpellDefinition spell, Context context, Map<SpellIdentity, SpellReference> bindings,
                   ScopeDCompilerDiagnostics diagnostics) {
        this.spell = spell;
        this.context = context;
        this.bindings = bindings;
        this.diagnostics = diagnostics;
    }

    Optional<SpellUnit> lower() {
        int startFatals = diagnostics.fatals();
        SpellBody body = spell.body();
        if (!(body.backend() instanceof Known<Backend> known)) {
            error(body.backend() instanceof Unknown<?> ? Code.UNKNOWN_GAMEPLAY : Code.MISSING_REQUIRED, "backend");
            return Optional.empty();
        }
        Backend backend = known.value();
        var backendContract = context.backend(key(DescriptorDomain.BACKEND, backend.descriptor()));
        if (backendContract.isEmpty()) {
            error(Code.BACKEND, "backend");
            return Optional.empty();
        }
        BackendContract contract = backendContract.orElseThrow();
        if (!contract.category().equals(backend.category())) {
            error(Code.BACKEND, "backend.category");
        }
        Set<String> capabilities = Set.copyOf(backend.capabilities());
        if (!contract.supports(backend.capabilities())) {
            error(Code.CAPABILITY, "backend.capabilities");
        }
        var backendBinding = bind(backend.descriptor(), contract.arguments(), Location.at(spell.reference(), "backend"));
        mark(SourceDomain.BACKEND, null, null, "", 0, 0);
        units(contract.staticCost());
        if (!(body.phenomenon() instanceof Absent<?>)) {
            error(Code.HOOK_ONLY, "phenomenon");
        }
        var inputs = contract.creationInputs().fields().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new Input(entry.getKey(), entry.getValue())).toList();
        inputs.forEach(input -> input.field().defaultValue().ifPresent(this::accountInline));
        var activation = lowerActivation(body.activation(), contract, capabilities);
        lowerSettings(body.settings(), contract);
        lowerHandlers(body.handlers(), contract, capabilities);
        var appearance = lowerAppearance(body.appearance(), contract);
        if (diagnostics.fatals() != startFatals) {
            return Optional.empty();
        }
        int constantCount = Math.addExact(inlineConstants, constants.size());
        int structuralBytes = Math.multiplyExact(48, Math.addExact(1 + instructions.size() + operands + handlers.size(),
                inputs.size() + settings.size() + constantCount));
        int retainedBytes = Math.addExact(Math.addExact(structuralBytes, valueBytes), sourceBytes);
        var cost = new UnitCost(settings.size(), expressionVisits, folded, inputs.size(), handlers.size(), blocks,
                instructions.size(), operands, constantCount, createEdges, descriptorUnits, sourceMap.size(),
                sourceBytes, retainedBytes);
        return Optional.of(new SpellUnit(spell.reference(), backendBinding.orElseThrow(), backend.category(),
                capabilities.stream().sorted().toList(), inputs, settings, activation, handlers, instructions,
                constants, appearance, sourceMap, cost));
    }

    private Optional<ActivationBinding> lowerActivation(Slot<Descriptor> slot, BackendContract backend, Set<String> capabilities) {
        if (!(slot instanceof Known<Descriptor> known)) {
            if (!(slot instanceof Absent<?>)) {
                error(slot instanceof Unknown<?> ? Code.UNKNOWN_GAMEPLAY : Code.MISSING_REQUIRED, "activation");
            }
            return Optional.empty();
        }
        Descriptor descriptor = known.value();
        var match = context.activation(key(DescriptorDomain.ACTIVATION, descriptor));
        var location = Location.at(spell.reference(), "activation");
        if (match.isEmpty()) {
            diagnostics.error(Stage.FRONTEND, Code.DESCRIPTOR, location);
            return Optional.empty();
        }
        var contract = match.orElseThrow();
        requireContext(backend, capabilities, contract.categories(), contract.requiredCapabilities(), location);
        requireInputs(backend, contract.creationInputs(), location);
        int start = constants.size();
        var binding = bind(descriptor, contract.arguments(), location);
        mark(SourceDomain.ACTIVATION, null, null, "", instructions.size(), start);
        units(contract.staticCost());
        var requirements = facts(contract.creationInputs());
        operands = Math.addExact(operands, requirements.size());
        return binding.map(value -> new ActivationBinding(value, requirements));
    }

    private void lowerSettings(Slot<List<Setting>> slot, BackendContract backend) {
        if (!(slot instanceof Known<List<Setting>> known)) {
            error(slot instanceof Unknown<?> ? Code.UNKNOWN_GAMEPLAY : Code.MISSING_REQUIRED, "settings");
            return;
        }
        var declared = new HashSet<String>();
        for (var setting : known.value()) {
            var field = backend.settings().fields().get(setting.name());
            if (!declared.add(setting.name()) || field == null || field.type() != setting.expression().type()) {
                error(Code.TYPE, "settings." + setting.name());
            }
            lowerSetting(setting.name(), setting.expression(), backend);
        }
        for (var entry : new TreeMap<>(backend.settings().fields()).entrySet()) {
            if (declared.contains(entry.getKey())) {
                continue;
            }
            var field = entry.getValue();
            if (field.required()) {
                error(Code.MISSING_REQUIRED, "settings." + entry.getKey());
            }
            field.defaultValue().ifPresent(value -> lowerSetting(entry.getKey(), new Literal(value), backend));
        }
    }

    private void lowerSetting(String name, Expression expression, BackendContract backend) {
        int startInstruction = instructions.size();
        int startConstant = constants.size();
        var normalized = normalize(expression, backend, "settings." + name, 1);
        int result = emit(normalized);
        settings.add(new SettingBinding(name, expression.type(), result));
        mark(SourceDomain.SETTING, null, null, name, startInstruction, startConstant);
    }

    private Expression normalize(Expression expression, BackendContract backend, String path, int depth) {
        expressionVisits = Math.incrementExact(expressionVisits);
        if (depth > MAX_EXPRESSION_DEPTH || expressionVisits > MAX_EXPRESSIONS + MAX_FRONTEND_FIELDS) {
            throw new Capacity();
        }
        return switch (expression) {
            case Literal literal -> literal;
            case CreationInput input -> {
                var field = backend.creationInputs().fields().get(input.name());
                if (!guaranteedInput(field, input.type())) {
                    error(Code.CREATION_INPUT, path);
                }
                yield input;
            }
            case Equal equal -> {
                var left = normalize(equal.left(), backend, path + ".left", depth + 1);
                var right = normalize(equal.right(), backend, path + ".right", depth + 1);
                if (left instanceof Literal first && right instanceof Literal second) {
                    folded = Math.incrementExact(folded);
                    yield new Literal(new BooleanValue(first.value().equals(second.value())));
                }
                yield new Equal(left, right);
            }
            case Select select -> {
                var condition = normalize(select.condition(), backend, path + ".condition", depth + 1);
                var yes = normalize(select.whenTrue(), backend, path + ".true", depth + 1);
                var no = normalize(select.whenFalse(), backend, path + ".false", depth + 1);
                if (condition instanceof Literal literal && literal.value() instanceof BooleanValue flag) {
                    folded = Math.incrementExact(folded);
                    yield flag.value() ? yes : no;
                }
                yield new Select(select.type(), condition, yes, no);
            }
        };
    }

    private int emit(Expression expression) {
        return switch (expression) {
            case Literal literal -> {
                int index = constants.size();
                constants.add(literal.value());
                accountValue(literal.value());
                yield add(new Constant(index), 1);
            }
            case CreationInput input -> add(new ReadInput(input.name(), input.type()), 1);
            case Equal equal -> add(new CompareEqual(emit(equal.left()), emit(equal.right())), 2);
            case Select select -> add(new Choose(emit(select.condition()), emit(select.whenTrue()), emit(select.whenFalse())), 3);
        };
    }

    private void lowerHandlers(Slot<List<Handler>> slot, BackendContract backend, Set<String> capabilities) {
        if (!(slot instanceof Known<List<Handler>> known)) {
            error(slot instanceof Unknown<?> ? Code.UNKNOWN_GAMEPLAY : Code.MISSING_REQUIRED, "handlers");
            return;
        }
        for (var handler : known.value()) {
            var eventKey = new DescriptorKey(DescriptorDomain.EVENT, handler.event(), handler.eventVersion());
            var location = location(handler.id(), null, "event");
            var match = context.event(eventKey);
            if (match.isEmpty() || !backend.events().contains(eventKey)) {
                diagnostics.error(Stage.FRONTEND, Code.DESCRIPTOR, location);
                continue;
            }
            var event = match.orElseThrow();
            requireContext(backend, capabilities, event.categories(), event.requiredCapabilities(), location);
            units(event.staticCost());
            int firstInstruction = instructions.size();
            int firstConstant = constants.size();
            for (var block : handler.blocks()) {
                int blockStart = instructions.size();
                int blockConstants = constants.size();
                blocks = Math.incrementExact(blocks);
                lowerBlock(block, backend, capabilities, event, location(handler.id(), block.id(), "block"));
                mark(SourceDomain.BLOCK, handler.id(), block.id(), "", blockStart, blockConstants);
            }
            var facts = facts(event.facts());
            operands = Math.addExact(operands, facts.size());
            handlers.add(new HandlerProgram(handler.id(), eventKey, facts, firstInstruction, instructions.size()));
            mark(SourceDomain.HANDLER, handler.id(), null, "", firstInstruction, firstConstant);
        }
    }

    private void lowerBlock(Block block, BackendContract backend, Set<String> capabilities, EventContract event,
                            Location location) {
        switch (block) {
            case UnknownBlock ignored -> diagnostics.error(Stage.FRONTEND, Code.UNKNOWN_GAMEPLAY, location);
            case CreateSpell create -> {
                var target = bindings.get(create.target().target());
                if (target == null) {
                    diagnostics.error(Stage.RESOLVER, Code.MISSING_DEPENDENCY, location);
                } else if (event.creation().isEmpty()) {
                    diagnostics.error(Stage.FRONTEND, Code.DESCRIPTOR, location);
                } else {
                    var contract = event.creation().orElseThrow();
                    if (!capabilities.containsAll(contract.requiredCapabilities())) {
                        diagnostics.error(Stage.FRONTEND, Code.CAPABILITY, location);
                    }
                        var inputs = facts(contract.providedInputs());
                    add(new CreateChild(target, inputs), 1 + inputs.size());
                    units(contract.staticCost());
                    createEdges = Math.incrementExact(createEdges);
                }
            }
            case EffectBlock effect -> {
                var match = context.effect(key(DescriptorDomain.EFFECT, effect.descriptor()));
                if (match.isEmpty() || !match.orElseThrow().events().contains(event.key())) {
                    diagnostics.error(Stage.FRONTEND, Code.DESCRIPTOR, location);
                    return;
                }
                var contract = match.orElseThrow();
                requireContext(backend, capabilities, contract.categories(), contract.requiredCapabilities(), location);
                requireInputs(backend, contract.creationInputs(), location);
                if (!event.facts().entrySet().containsAll(contract.facts().entrySet())) {
                    diagnostics.error(Stage.FRONTEND, Code.EVENT_FACT, location);
                }
                for (var emitted : contract.emittedEvents().stream().sorted(java.util.Comparator.comparing(DescriptorKey::tag)
                        .thenComparingInt(DescriptorKey::version)).toList()) {
                    var emission = context.event(emitted);
                    if (!backend.events().contains(emitted) || emission.isEmpty()) {
                        diagnostics.error(Stage.FRONTEND, Code.EVENT_FACT, location);
                    } else {
                        var emittedEvent = emission.orElseThrow();
                        requireContext(backend, capabilities, emittedEvent.categories(), emittedEvent.requiredCapabilities(), location);
                        if (!event.facts().entrySet().containsAll(emittedEvent.facts().entrySet())) {
                            diagnostics.error(Stage.FRONTEND, Code.EVENT_FACT, location);
                        }
                    }
                }
                var facts = facts(contract.facts());
                bind(effect.descriptor(), contract.arguments(), location).ifPresent(descriptor ->
                        add(new Effect(descriptor, contract.operation(), facts), 1 + facts.size()));
                units(contract.staticCost());
            }
        }
    }

    private Appearance lowerAppearance(Slot<Descriptor> slot, BackendContract backend) {
        if (slot instanceof Absent<?>) {
            return new Appearance(VisualState.ABSENT, Optional.empty());
        }
        if (slot instanceof Known<Descriptor> known) {
            var descriptor = known.value();
            var contract = context.appearance(key(DescriptorDomain.APPEARANCE, descriptor));
            var normalized = contract.filter(value -> value.categories().contains(backend.category()))
                    .flatMap(value -> value.arguments().bind(descriptor.arguments()));
            if (normalized.isPresent()) {
                var arguments = normalized.orElseThrow();
                arguments.forEach(argument -> accountInline(argument.value()));
                mark(SourceDomain.APPEARANCE, null, null, "", instructions.size(), constants.size());
                units(contract.orElseThrow().staticCost());
                return new Appearance(VisualState.KNOWN,
                        Optional.of(new Descriptor(descriptor.tag(), descriptor.version(), arguments)));
            }
            if (contract.isPresent()) {
                error(Code.ARGUMENT, "appearance");
                return new Appearance(VisualState.FALLBACK, Optional.empty());
            }
        }
        if (slot instanceof Missing<?>) {
            error(Code.MISSING_REQUIRED, "appearance");
        } else {
            diagnostics.warn(Stage.FRONTEND, Code.VISUAL_FALLBACK, Location.at(spell.reference(), "appearance"));
        }
        return new Appearance(VisualState.FALLBACK, Optional.empty());
    }

    private void requireContext(BackendContract backend, Set<String> capabilities, Set<String> categories,
                                Set<String> required, Location location) {
        if (!categories.contains(backend.category())) {
            diagnostics.error(Stage.FRONTEND, Code.BACKEND, location);
        }
        if (!capabilities.containsAll(required)) {
            diagnostics.error(Stage.FRONTEND, Code.CAPABILITY, location);
        }
    }

    private void requireInputs(BackendContract backend, Map<String, ValueType> inputs, Location location) {
        for (var input : new TreeMap<>(inputs).entrySet()) {
            var field = backend.creationInputs().fields().get(input.getKey());
            if (!guaranteedInput(field, input.getValue())) {
                diagnostics.error(Stage.FRONTEND, Code.CREATION_INPUT, location);
            }
        }
    }

    private static boolean guaranteedInput(Field field, ValueType type) {
        return field != null && field.type() == type && (field.required() || field.defaultValue().isPresent());
    }

    private Optional<Descriptor> bind(Descriptor descriptor, Schema schema, Location location) {
        var bound = schema.bind(descriptor.arguments());
        if (bound.isEmpty()) {
            diagnostics.error(Stage.FRONTEND, Code.ARGUMENT, location);
        }
        return bound.map(arguments -> {
            arguments.forEach(argument -> accountInline(argument.value()));
            return new Descriptor(descriptor.tag(), descriptor.version(), arguments);
        });
    }

    private void accountInline(Value value) {
        operands = Math.incrementExact(operands);
        inlineConstants = Math.incrementExact(inlineConstants);
        accountValue(value);
    }

    private void accountValue(Value value) {
        int bytes = value instanceof TextValue text ? text.value().getBytes(StandardCharsets.UTF_8).length : Long.BYTES;
        valueBytes = Math.addExact(valueBytes, bytes);
    }

    private int add(Instruction instruction, int width) {
        operands = Math.addExact(operands, width);
        int address = instructions.size();
        if (address >= MAX_EXPRESSIONS + MAX_FRONTEND_FIELDS + MAX_BLOCKS) {
            throw new Capacity();
        }
        instructions.add(instruction);
        return address;
    }

    private void mark(SourceDomain domain, HandlerId handler, BlockId block, String symbol,
                      int instruction, int constant) {
        var address = new SourceAddress(spell.reference(), domain, Optional.ofNullable(handler),
                Optional.ofNullable(block), symbol);
        sourceMap.add(new SourceRange(address, instruction, instructions.size(), constant, constants.size()));
        sourceBytes = Math.addExact(sourceBytes, 64 + symbol.getBytes(StandardCharsets.UTF_8).length);
    }

    private void units(int amount) { descriptorUnits = Math.addExact(descriptorUnits, amount); }

    private void error(Code code, String path) {
        diagnostics.error(Stage.FRONTEND, code, Location.at(spell.reference(), path));
    }

    private Location location(HandlerId handler, BlockId block, String path) {
        return new Location(Optional.of(spell.reference()), Optional.of(handler), Optional.ofNullable(block), path);
    }

    static DescriptorKey key(DescriptorDomain domain, Descriptor descriptor) {
        return new DescriptorKey(domain, descriptor.tag(), descriptor.version());
    }

    private static List<Fact> facts(Map<String, ValueType> facts) {
        return facts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new Fact(entry.getKey(), entry.getValue())).toList();
    }

    static final class Capacity extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
