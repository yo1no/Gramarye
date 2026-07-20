# Gramarye

Gramarye is a server-authoritative, node-based magic mod for Minecraft 1.21.1 on NeoForge 21.1.x.

The repository is being implemented in approved phases. P0 establishes only the platform, build, documentation, and smoke-test baseline; gameplay architecture begins in later phases.

See [the architecture index](docs/architecture/README.md) and the repository [contributor instructions](AGENTS.md) before changing architecture or production code.

## P0 verification

```bash
./gradlew verifyPlatformBaseline
./gradlew compileJava
./gradlew test
./gradlew runGameTestServer
./scripts/run-dedicated-server-smoke.sh
```

`runClient` is a local interactive check and is intentionally excluded from headless CI.
