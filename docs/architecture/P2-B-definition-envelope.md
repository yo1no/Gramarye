# P2-B definition envelope baseline

This phase record is subordinate to the [frozen architecture specification](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md), the [implementation contract](../codex-spec/Codex_實作總規格Prompt.md), and the [detailed engineering phases](../codex-spec/NeoForge1.21.1_詳細實作步驟.md). It records the P2-B serialization boundary without creating a second architecture specification.

## Envelope shape

The registry-independent envelope has exactly these serialized fields:

```json
{
  "type": "namespace:path",
  "schema_version": 0,
  "payload": {}
}
```

`type` is the Trigger or Action descriptor's registry key. A descriptor does not store another type ID. `schema_version` is the non-negative data-format version of that descriptor's payload; it is not a `SkillRevision`. P2-B resolves only an exact match with `currentPayloadSchemaVersion()` and preserves all other versions as unknown. Explicit migration begins in P3.

## Resolved and unknown states

A resolved definition contains its descriptor, matching schema version, and typed payload. Semantic validation remains an explicit later call and is not part of Codec decoding. Encoding a resolved definition obtains `type` from the registry and uses the descriptor's current schema version and payload Codec; the Codec may produce a canonical payload representation.

An unknown definition contains the complete original envelope and a bounded diagnostic classified as unknown type, unsupported schema version, payload decode error, or Codec exception. It never substitutes a default descriptor, and a partial `DataResult` is not accepted as resolved.

## Registry lookup boundary

Trigger and Action use separate lookup interfaces. Production adapters delegate directly to the formal custom registries after `NewRegistryEvent`; they keep no secondary ID map. Test fakes exist only in test source. Reverse lookup failure makes resolved encoding return a `DataResult` error.

## Raw payload preservation boundary

`DefinitionEnvelope` takes a defensive deep snapshot at both its Java-constructor and Codec-decode boundaries. Its `copyRawPayload()` accessor creates another deep snapshot on every call, making the O(payload size) cost explicit and ensuring neither constructor inputs nor accessor results expose the envelope's internal mutable tree. Unknown definitions may safely retain the same envelope reference.

The supported raw value families are Gson `JsonElement` trees and Minecraft NBT `Tag` trees. JSON snapshots use `JsonElement#deepCopy()`; NBT snapshots use `Tag#copy()`. This covers nested JSON objects and arrays, NBT compounds and lists, NBT byte/int/long arrays, and their scalar leaves without converting the tree through text or another data format. A standard `RegistryOps` wrapper is retained by rebuilding it with the same registry lookup provider and verifying the matching JSON or NBT parent ops; support is selected from the actual value family rather than singleton ops identity. RegistryOps with an unrecognized custom parent is unsupported rather than silently reinterpreted.

An unsupported value/ops representation is rejected at envelope construction. Direct Java construction reports constructor misuse with `IllegalArgumentException`; Codec decode returns a bounded `DataResult.error` at the envelope boundary. Failure is not deferred until accessor use and does not create a Trigger/Action resolution failure code.

Unknown definitions re-encode the original envelope directly. Their type, schema version, and raw payload are structurally preserved when read and written with the same `DynamicOps`. Preservation covers structure, leaf representation, and registry-ops context, not object identity. Simple representable values are tested across `JsonOps` and `NbtOps`, but arbitrary values are not promised to convert losslessly between every pair of ops.

The strong original-raw preservation guarantee applies only to unknown definitions. Known resolved payloads may be canonicalized by their descriptor Codec. The `MAX_RAW_PAYLOAD_BYTES` ceiling belongs at a future I/O boundary that has actual bytes or JSON text; P2-B does not estimate bytes from `Dynamic#toString()`.

Envelope equality compares type ID, schema version, raw value family, and payload tree structure; it deliberately ignores `DynamicOps` and RegistryOps wrapper identity. `toString()` is a bounded metadata-only summary containing an abbreviated type ID, schema version, and payload family. It never renders raw payload content.
