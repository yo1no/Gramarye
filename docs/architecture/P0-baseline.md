# P0 platform and repository baseline

## Definition of Done

P0 fixes the build platform and permanent identities, removes NeoForge MDK sample content, keeps minimal common and client entrypoints, establishes architecture records, and supplies build, GameTest, and dedicated-server smoke verification.

## Frozen values

| Property | Value |
| --- | --- |
| Mod ID | `gramarye` |
| Data namespace | `gramarye` |
| Java package root | `com.yo1no.gramarye` |
| Minecraft | `1.21.1` |
| NeoForge build | `21.1.241` |
| NeoForge metadata range | `[21.1.241,21.2)` |
| Java toolchain | `21` |
| Gradle | `9.2.1` |
| ModDevGradle | `2.0.142` |
| Schema baseline | `0` (no gameplay persistence schema exists in P0) |

Mojang mappings are used without a Parchment overlay. The Gradle wrapper distribution is protected by the official Gradle 9.2.1 binary SHA-256 checksum.

## Verification

```bash
./gradlew verifyPlatformBaseline
./gradlew compileJava
./gradlew test
./gradlew runGameTestServer
./scripts/run-dedicated-server-smoke.sh
```

The GameTest only proves that the mod loads in the GameTest dedicated-server environment. The dedicated-server smoke script uses a temporary game directory, waits for the normal server-ready message, sends `stop` through server stdin, and requires a clean process exit. Its default startup timeout is 180 seconds and can be overridden with `GRAMARYE_SERVER_SMOKE_TIMEOUT_SECONDS`.

`runClient` remains an optional local interactive check and is not executed in headless CI.

## Explicitly deferred

P0 contains no Typed IDs, Trigger or Action registries, SavedData, Attachments, network payloads, effect pipeline, mana service, skill runtime, or gameplay implementation. Those require separately approved later phases.
