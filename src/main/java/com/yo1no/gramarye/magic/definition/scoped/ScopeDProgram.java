package com.yo1no.gramarye.magic.definition.scoped;

import java.util.List;
import java.util.Optional;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDFrontend.*;

/** Derived compiler data. No execution, persistence, or retention interface. */
sealed interface ScopeDProgram permits ScopeDProgram.SpellProgram, ScopeDLegacyCompiler.LegacyProgram {
    int IR_VERSION = 1;

    record ContentBinding(SpellReference reference, String contentDigest) {}

    record Key(int version, SpellReference root, List<ContentBinding> closure, ContextIdentity context) {
        public Key { closure = List.copyOf(closure); }
    }

    record SpellProgram(Key key, List<SpellUnit> spells, Cost cost, ScopeDCompilerDiagnostics.Report diagnostics)
            implements ScopeDProgram {
        public SpellProgram { spells = List.copyOf(spells); }
    }

    record SpellUnit(SpellReference reference, Descriptor backend, String category, List<String> capabilities,
                     List<Input> inputs, List<SettingBinding> settings, Optional<ActivationBinding> activation,
                     List<HandlerProgram> handlers, List<Instruction> instructions, List<Value> constants,
                     Appearance appearance, List<SourceRange> sourceMap, UnitCost cost) {
        public SpellUnit {
            capabilities = List.copyOf(capabilities);
            inputs = List.copyOf(inputs);
            settings = List.copyOf(settings);
            handlers = List.copyOf(handlers);
            instructions = List.copyOf(instructions);
            constants = List.copyOf(constants);
            sourceMap = List.copyOf(sourceMap);
        }
    }

    record ActivationBinding(Descriptor descriptor, List<Fact> inputRequirements) {
        public ActivationBinding { inputRequirements = List.copyOf(inputRequirements); }
    }

    record Input(String name, Field field) {}
    record SettingBinding(String name, ValueType type, int result) {}
    record Fact(String name, ValueType type) {}
    record HandlerProgram(HandlerId id, DescriptorKey event, List<Fact> facts, int start, int end) {
        public HandlerProgram { facts = List.copyOf(facts); }
    }

    sealed interface Instruction permits Constant, ReadInput, CompareEqual, Choose, Effect, CreateChild {}
    record Constant(int constant) implements Instruction {}
    record ReadInput(String name, ValueType type) implements Instruction {}
    record CompareEqual(int left, int right) implements Instruction {}
    /** The two branch addresses are alternatives, evaluated only when selected. */
    record Choose(int condition, int whenTrue, int whenFalse) implements Instruction {}
    record Effect(Descriptor descriptor, EffectOperation operation, List<Fact> facts) implements Instruction {
        public Effect { facts = List.copyOf(facts); }
    }
    record CreateChild(SpellReference exactTarget, List<Fact> inputs) implements Instruction {
        public CreateChild { inputs = List.copyOf(inputs); }
    }

    enum VisualState { ABSENT, KNOWN, FALLBACK }
    record Appearance(VisualState state, Optional<Descriptor> descriptor) {}
    enum SourceDomain { BACKEND, ACTIVATION, SETTING, HANDLER, BLOCK, APPEARANCE }
    record SourceAddress(SpellReference spell, SourceDomain domain, Optional<HandlerId> handler,
                         Optional<BlockId> block, String symbol) {}
    record SourceRange(SourceAddress address, int firstInstruction, int endInstruction,
                       int firstConstant, int endConstant) {}

    record UnitCost(int settings, int expressions, int folded, int creationInputs, int handlers, int blocks,
                    int instructions, int operands, int constants, int createEdges, int descriptorUnits,
                    int sourceEntries, int sourceBytes, int retainedBytes) {}
    record Cost(int spells, int edges, int cyclicComponents, UnitCost units, int feedbackWork,
                int loweringWork, int scratchHighWater, int retainedDiagnostics, int omittedDiagnostics) {}
}
