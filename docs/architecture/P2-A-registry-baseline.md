# P2-A descriptor registry baseline

This phase record is subordinate to the [frozen architecture specification](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md), the [implementation contract](../codex-spec/Codex_實作總規格Prompt.md), and the [detailed engineering phases](../codex-spec/NeoForge1.21.1_詳細實作步驟.md). It records the P2-A implementation boundary without creating a second architecture specification.

- `gramarye:trigger_type` and `gramarye:action_type` are code-defined descriptor registries created during `NewRegistryEvent`.
- Registry entry keys are the only Trigger/Action type IDs; descriptors do not duplicate an ID field.
- The registries have no default, numeric-ID sync, explicit maximum ID, or P2-A production entries.
- Payload descriptors are strongly typed and expose only a `MapCodec`, immutable capabilities, and P1 semantic validation.
- P5 will connect typed trigger event kinds to the internal event model. P2-B envelope and unknown-payload dispatch remain deferred.
