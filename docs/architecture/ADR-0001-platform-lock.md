# ADR-0001: Platform lock

- Status: Accepted
- Scope: P0

## Decision

Gramarye is fixed to Minecraft 1.21.1, NeoForge build 21.1.241, NeoForge metadata range `[21.1.241,21.2)`, Java toolchain 21, Gradle 9.2.1, ModDevGradle 2.0.142, and Mojang mappings without a Parchment overlay.

The permanent identities are `gramarye` for both mod ID and data namespace, and `com.yo1no.gramarye` for the Java package root. Versions are explicit; dynamic dependency selectors are not allowed.

## Consequences

Build and metadata changes must pass `verifyPlatformBaseline`. Support for other Minecraft versions, loaders, or package roots requires an explicit amendment to the frozen architecture.

## Authority

See [Frozen skeleton §1–2](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md).
