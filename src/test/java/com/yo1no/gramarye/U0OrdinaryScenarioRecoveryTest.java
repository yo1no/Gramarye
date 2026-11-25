package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exact qualification body controls only; no Minecraft/native/auth result is simulated. */
final class U0OrdinaryScenarioRecoveryTest {
    @TempDir Path temporary;

    @Test void typedStateSamplerUsesExactChangesAndTheSixtyFourSnapshotBound() throws Exception { run("sampler"); }
    @Test void partialAndCompleteCheckpointDocumentsNeverBecomeCaseAcceptance() throws Exception { run("partial"); }
    @Test void lateTitleFlushNeedsActualLogoutAndWritesAtMostOnce() throws Exception { run("late"); }
    @Test void activeSaveUsesTheOriginalWriterReadbackOnceAndNeverExtendsTheDeadline() throws Exception {
        runActiveSave();
    }
    @Test void fixedDensityUsesOneInitialNativeMarkerProfileAndOnlyDrainedCleanup() throws Exception {
        runDensity();
    }

    private void runDensity() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) root = root.getParent();
        assertNotNull(root);
        String scene = Files.readString(root.resolve("src/p11OnlineHarness/java/com/yo1no/gramarye/U0OrdinaryScenario.java"));
        String tick = method(scene, "    public static void tick(");
        assertTrue(tick.indexOf("prepareArena();") < tick.indexOf("prepareDensity();")
                && tick.indexOf("prepareDensity();") < tick.indexOf("normal-input-ready.ready"));
        String sunset = method(scene, "    private static void sunsetStep(");
        assertFalse(sunset.contains("checkpoint == CHECKPOINTS.size()"));
        assertTrue(sunset.indexOf("bootstrapDrainedForSunset") < sunset.indexOf("removeDensityAfterDrain();"));
        assertTrue(sunset.contains("capturedStartup.runtime().v0Active(capturedSlot, server)")
                && sunset.contains("V0BootstrapRetirementCoordinator.begin")
                && sunset.contains("retirement.requestPlayerCleanup")
                && sunset.contains("retirement.requestRosterFinalization()")
                && sunset.contains("retirement.requestSeedCleanup()"));
        String prepare = method(scene, "    private static void prepareDensity(");
        assertFalse(prepare.contains(".setMarker(") || prepare.contains("U0BoundedEntityQuery") || prepare.contains("P9StarterProjectile"));
        assertTrue(prepare.contains("marker.saveWithoutId(new CompoundTag())")
                && prepare.contains("marker.readAdditionalSaveData(nativeState)")
                && prepare.indexOf("densityStage = 1") < prepare.indexOf("EntityType.ARMOR_STAND.create"));
        String source = densityScaffold().replace("/* DENSITY */", prepare
                + method(scene, "    private static void removeDensityAfterDrain(")
                + method(scene, "    private static List<Map<String, Object>> densityMarkerScalars("));
        var classes = Files.createDirectory(temporary.resolve("density-classes"));
        var java = temporary.resolve("DensityControl.java");
        Files.writeString(java, source, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler);
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            assertTrue(Boolean.TRUE.equals(compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none", "-Xlint:all,-serial,-auxiliaryclass", "-Werror",
                            "-classpath", classes.toString(), "-d", classes.toString()), null,
                    manager.getJavaFileObjects(java)).call()), () -> diagnostics.getDiagnostics().toString());
        }
        try (var loader = new URLClassLoader(new java.net.URL[] {classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            loader.loadClass("com.yo1no.gramarye.DensityControl").getMethod("run").invoke(null);
        }
    }

    private static String densityScaffold() { return """
            package com.yo1no.gramarye;
            import java.io.IOException;
            import java.nio.file.Path;
            import java.util.ArrayList;
            import java.util.LinkedHashMap;
            import java.util.List;
            import java.util.Map;
            public final class DensityControl {
                private static final List<ArmorStand> DENSITY_FILLERS = new ArrayList<>();
                private static String densityMode = "NONE";
                private static int densityStage, arenaFloor = 4;
                private static final Server server = new Server();
                private static final Path output = Path.of("excluded-component-only");
                public static void run() throws Exception {
                    for (int target : new int[] {0, 64, 65}) {
                        reset(target); prepareDensity();
                        int count = target == 0 ? 0 : target == 64 ? 59 : 60;
                        check(DENSITY_FILLERS.size() == count && EntityType.ARMOR_STAND.calls == count
                                && server.level.adds == count, "fixed native factory/insertion count");
                        var setup = P11C4aEvidence.values.getFirst();
                        check(setup.get("mode").equals(target == 0 ? "NONE" : target == 64 ? "RAW64" : "RAW65")
                                && setup.get("expectedRawNotMeasured").equals(target == 0 ? -1 : target), "mode and expected raw are explicit, never a Scan measurement");
                        check(setup.get("status").equals("FIXED_FILLERS_AND_EXPECTED_RAW_NOT_SCAN_PROOF"), "setup cannot be raw or row proof");
                        for (var marker : DENSITY_FILLERS) {
                            check(marker.marker && marker.noPhysics && marker.invisible && marker.noGravity
                                    && marker.nativePreserved == 7 && marker.loads == 1, "public native load preserves original body and sets marker/noPhysics");
                            check(marker.x == 8.5 && marker.y == 5 && marker.z == 8.5 && marker.level == server.level,
                                    "all fillers stay in fixed already-loaded arena section");
                        }
                        P11C4aEvidence.at64 = !P11C4aEvidence.at64;
                        expectRefusal(() -> prepareDensity());
                        check(EntityType.ARMOR_STAND.calls == count && P11C4aEvidence.values.size() == 1, "no repeat setup or later profile switch");
                        var beforeRows = (List<?>) setup.get("markers");
                        var unrelated = new ArmorStand(10000); server.level.entities.put(unrelated.id, unrelated);
                        removeDensityAfterDrain();
                        check(DENSITY_FILLERS.isEmpty() && server.level.discards == count && !unrelated.removed,
                                "only exact own fixture entities removed, no product/unrelated entity deletion");
                        check(beforeRows.size() == count && P11C4aEvidence.values.size() == 2,
                                "initial immutable scalar list survives own-reference cleanup");
                        var removed = (List<?>) P11C4aEvidence.values.getLast().get("markers");
                        for (var row : removed) check(((Map<?,?>) row).get("removed").equals(true), "actual component removal state exported");
                        expectRefusal(() -> removeDensityAfterDrain());
                        check(server.level.discards == count, "no duplicate native removal");
                    }
                    reset(64); P11C4aEvidence.at65 = true;
                    expectRefusal(() -> prepareDensity()); check(EntityType.ARMOR_STAND.calls == 0, "exclusive profile refuses before native calls");
                    reset(64); server.level.loaded = false;
                    expectRefusal(() -> prepareDensity()); check(EntityType.ARMOR_STAND.calls == 0, "unloaded chunk never gets fixture insertion");
                    server.level.loaded = true; expectRefusal(() -> prepareDensity());
                    check(EntityType.ARMOR_STAND.calls == 0, "failed initial setup does not retry native work");
                    reset(64); var factoryPrimary = new AssertionError("native factory primary");
                    EntityType.ARMOR_STAND.failAt = 4; EntityType.ARMOR_STAND.failure = factoryPrimary;
                    Throwable factoryCaught = null;
                    try { prepareDensity(); } catch (RuntimeException | Error failure) { factoryCaught = failure; }
                    check(factoryCaught == factoryPrimary && DENSITY_FILLERS.size() == 3 && server.level.adds == 3,
                            "native factory Error identity and partial own references retained");
                    expectRefusal(() -> prepareDensity()); check(EntityType.ARMOR_STAND.calls == 4, "native Error does not repeat setup");
                    reset(64); prepareDensity();
                    var own = DENSITY_FILLERS.getFirst(); var foreign = new ArmorStand(own.id);
                    server.level.entities.put(own.id, foreign);
                    expectRefusal(() -> removeDensityAfterDrain());
                    check(!foreign.removed && server.level.discards == 0, "identity mismatch never removes foreign replacement");
                    reset(64); prepareDensity(); var removalPrimary = new AssertionError("native removal primary");
                    DENSITY_FILLERS.getFirst().failure = removalPrimary; Throwable removalCaught = null;
                    try { removeDensityAfterDrain(); } catch (RuntimeException | Error failure) { removalCaught = failure; }
                    check(removalCaught == removalPrimary, "original removal Error preserved outside observers");
                    expectRefusal(() -> removeDensityAfterDrain()); check(server.level.discards == 1, "failed removal cannot retry");
                }
                private static void reset(int target) {
                    DENSITY_FILLERS.clear(); densityMode = "NONE"; densityStage = 0;
                    server.level = new Level(); EntityType.ARMOR_STAND.calls = 0;
                    EntityType.ARMOR_STAND.failAt = -1; EntityType.ARMOR_STAND.failure = null;
                    P11C4aEvidence.at64 = target == 64; P11C4aEvidence.at65 = target == 65;
                    P11C4aEvidence.values.clear();
                }
                private interface Action { void run() throws IOException; }
                private static void expectRefusal(Action action) throws IOException {
                    try { action.run(); } catch (IllegalStateException expected) { return; }
                    throw new AssertionError("expected fixed-fixture refusal");
                }
                private static void require(boolean value, String code) { if (!value) throw new IllegalStateException(code); }
                private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
                /* DENSITY */
                private static final class Server { Level level = new Level(); Level overworld() { return level; } int getTickCount() { return 100; } }
                private static final class Level {
                    boolean loaded = true; int adds, discards; final Map<Integer,ArmorStand> entities = new LinkedHashMap<>();
                    BlockPos getSharedSpawnPos() { return new BlockPos(0, 4, 0); }
                    boolean hasChunkAt(BlockPos ignored) { return loaded; }
                    boolean addFreshEntity(ArmorStand marker) { adds++; marker.level = this; entities.put(marker.id, marker); return true; }
                    ArmorStand getEntity(int id) { return entities.get(id); }
                }
                private record BlockPos(int x, int y, int z) { int getX() { return x; } int getZ() { return z; } }
                private static final class EntityType {
                    static final EntityType ARMOR_STAND = new EntityType(); int calls, failAt = -1; Error failure;
                    ArmorStand create(Level ignored) { calls++; if (calls == failAt) throw failure; return new ArmorStand(calls); }
                }
                private static final class CompoundTag {
                    final Map<String,Object> fields = new LinkedHashMap<>();
                    void putBoolean(String key, boolean value) { fields.put(key, value); }
                    boolean getBoolean(String key) { return Boolean.TRUE.equals(fields.get(key)); }
                }
                private static final class ArmorStand {
                    final int id; int nativePreserved = 7, loads; double x, y, z;
                    boolean marker, noPhysics, invisible, noGravity, removed; Level level; Error failure;
                    ArmorStand(int id) { this.id = id; }
                    CompoundTag saveWithoutId(CompoundTag tag) { tag.fields.put("nativePreserved", nativePreserved); tag.putBoolean("Marker", marker); return tag; }
                    void readAdditionalSaveData(CompoundTag tag) {
                        check(tag.fields.get("nativePreserved").equals(7), "own original native preimage retained");
                        loads++; marker = tag.getBoolean("Marker"); noPhysics = marker;
                    }
                    void setInvisible(boolean value) { invisible = value; } void setNoGravity(boolean value) { noGravity = value; }
                    void setPos(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
                    int getId() { return id; } double getX() { return x; } double getY() { return y; } double getZ() { return z; }
                    boolean isRemoved() { return removed; } boolean isNoGravity() { return noGravity; } Level level() { return level; }
                    Box getBoundingBox() { return new Box(marker ? 0 : 1); }
                    void discard() { level.discards++; if (failure != null) throw failure; removed = true; level.entities.remove(id); }
                }
                private record Box(double size) { double getXsize() { return size; } double getYsize() { return size; } }
                private static final class P11C4aEvidence {
                    static boolean at64, at65; static final List<Map<String,Object>> values = new ArrayList<>();
                    static Path root() { return Path.of("evidence-root-control"); }
                    static boolean cuePresent(Path ignored, String leaf) { return leaf.equals("density-64.ready") ? at64 : at65; }
                    static void write(Path ignored, String leaf, Map<String,Object> value) { values.add(value); }
                }
            }
            """; }

    private void runActiveSave() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) root = root.getParent();
        assertNotNull(root);
        String scene = Files.readString(root.resolve("src/p11OnlineHarness/java/com/yo1no/gramarye/U0OrdinaryScenario.java"));
        String step = method(scene, "    private static boolean activeSaveStep(");
        assertTrue(step.indexOf("activeSaveIssued = true") < step.indexOf("server.getPlayerList().saveAll()"));
        assertTrue(step.indexOf("server.getPlayerList().saveAll()") < step.indexOf("var writtenSource = body.source"));
        assertTrue(step.indexOf("var writtenSource = body.source") < step.indexOf("source.prepareCooldownRetirementReadback"));
        assertTrue(step.indexOf("source.prepareCooldownRetirementReadback") < step.indexOf("server.halt(false)"));
        assertFalse(step.contains("preparePolicyRetirement") || step.contains("claimPolicyRetirement"));
        assertTrue(step.contains("if (verified && lifecycleRequested)")
                && step.indexOf("beginNativeLifecycle(selected)") < step.indexOf("server.halt(false)"));
        String source = activeScaffold().replace("/* ACTIVE_SAVE */", step
                + method(scene, "    private static int firstActiveActor(")
                + method(scene, "    private static Map<String, Object> activeSaveObservation("));
        var classes = Files.createDirectory(temporary.resolve("active-classes"));
        var java = temporary.resolve("ActiveSaveControl.java");
        Files.writeString(java, source, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler);
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            assertTrue(Boolean.TRUE.equals(compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none", "-Xlint:all,-serial,-auxiliaryclass", "-Werror",
                            "-classpath", classes.toString(), "-d", classes.toString()), null,
                    manager.getJavaFileObjects(java)).call()), () -> diagnostics.getDiagnostics().toString());
        }
        try (var loader = new URLClassLoader(new java.net.URL[] {classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            loader.loadClass("com.yo1no.gramarye.ActiveSaveControl").getMethod("run").invoke(null);
        }
    }

    private static String activeScaffold() { return """
            package com.yo1no.gramarye;
            import java.io.IOException;
            import java.nio.file.Path;
            import java.util.ArrayList;
            import java.util.LinkedHashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.UUID;
            public final class ActiveSaveControl {
                private static final List<Map<String,Object>> ACTIVE_SAVE = new ArrayList<>();
                private static String activeSaveMode;
                private static boolean activeSaveIssued;
                private static boolean lifecycleRequested;
                private static int lifecycleStage, lifecycleBegins;
                private static String densityMode = "NONE";
                private static final Actor[] ACTORS = {new Actor(0), new Actor(1)};
                private static final Server server = new Server();
                private static final Path output = Path.of("component-control-only");
                public static void run() throws Exception {
                    reset(true);
                    check(!activeSaveStep() && !activeSaveIssued && server.saves == 0, "cue arms before real release");
                    var entry = new Entry(0, 20, 80, 100, 120);
                    ACTORS[1].owner.body.cooldown.data.entries.put("BOOTSTRAP", new Entry(1, 20, 80, -1, -1));
                    check(!activeSaveStep() && server.saves == 0, "actual PENDING is not ACTIVE");
                    ACTORS[1].owner.body.cooldown.data.entries.put("BOOTSTRAP", new Entry(0, 19, 80, 100, 119));
                    check(!activeSaveStep() && server.saves == 0, "wrong duration is not Bootstrap ACTIVE20");
                    ACTORS[1].owner.body.cooldown.data.entries.put("BOOTSTRAP", new Entry(0, 20, 80, 80, 100));
                    check(!activeSaveStep() && server.saves == 0, "inclusive actual expiry cannot trigger save");
                    ACTORS[1].owner.body.cooldown.data.entries.put("BOOTSTRAP", entry);
                    check(activeSaveStep() && activeSaveIssued && server.saves == 1 && server.halts == 1, "original save/readback then normal halt once");
                    check(ACTORS[0].owner.reads == 0 && ACTORS[1].owner.reads == 1, "only actual selected actor has primary readback");
                    check(ACTORS[1].owner.expected == ACTORS[1].owner.body.source
                            && ACTORS[1].owner.expected.version() == 2, "readback takes exact source published by original save");
                    check(ACTORS[1].owner.body.cooldown.data.entries.get("BOOTSTRAP") == entry
                            && entry.duration == 20 && entry.expiresAt == 120 && entry.releasedAt == 100,
                            "no material setter or duration/deadline extension");
                    check(P11C4aEvidence.values.size() == 3 && ACTIVE_SAVE.size() == 3, "separate bounded arm/before/after files");
                    var after = ACTIVE_SAVE.getLast();
                    check(after.get("selectedPrimaryReadback").equals("VERIFIED")
                            && after.get("peerPrimaryReadback").equals("NOT_REQUESTED"), "peer observation is not readback proof");
                    check(((List<?>) ((Map<?,?>) after.get("actual")).get("actors")).size() == 2, "both independent source/material observations");
                    check(!activeSaveStep() && !activeSaveStep() && server.saves == 1 && ACTORS[1].owner.reads == 1 && server.halts == 1,
                            "persistent cues never retry save/readback/halt");

                    reset(false); activeSaveStep(); makeActive(0); activeSaveStep();
                    check(server.saves == 1 && ACTORS[0].owner.reads == 1 && server.halts == 0, "save-only mode never halts");
                    reset(false); P11C4aEvidence.life = true; activeSaveStep();
                    check(lifecycleRequested && lifecycleStage == 1 && lifecycleBegins == 0, "lifecycle cue only pre-arms the verified-save branch");
                    makeActive(1); check(activeSaveStep() && lifecycleBegins == 1 && server.saves == 1
                            && ACTORS[1].owner.reads == 1 && server.halts == 0, "one verified selected save enters lifecycle component boundary");
                    activeSaveStep(); check(lifecycleBegins == 1, "persistent lifecycle cue never repeats the boundary");
                    reset(true); P11C4aEvidence.life = true;
                    expectRefusal("lifecycle excludes immediate-stop profile", () -> activeSaveStep());
                    check(server.saves == 0 && lifecycleBegins == 0, "combined stop profile cannot issue lifecycle controls");
                    reset(false); P11C4aEvidence.life = true; densityMode = "RAW64";
                    expectRefusal("lifecycle excludes density profile", () -> activeSaveStep());
                    check(server.saves == 0 && lifecycleBegins == 0, "density setup cannot be changed into lifecycle");
                    reset(false); P11C4aEvidence.life = true; activeSaveStep(); makeActive(0);
                    ACTORS[0].owner.readback = false; activeSaveStep();
                    check(lifecycleBegins == 0 && server.halts == 0, "unproved selected primary never enters lifecycle");
                    reset(true); activeSaveStep(); makeActive(0); ACTORS[0].owner.readback = false; activeSaveStep();
                    check(server.halts == 0 && ACTIVE_SAVE.getLast().get("selectedPrimaryReadback").equals("UNPROVED"), "false readback remains unproved");
                    activeSaveStep(); check(server.saves == 1 && ACTORS[0].owner.reads == 1, "false readback is not retried");
                    reset(true); activeSaveStep(); makeActive(0); ACTORS[0].owner.changeDuringRead = true; activeSaveStep();
                    check(server.halts == 0 && ACTIVE_SAVE.getLast().get("selectedPrimaryReadback").equals("UNPROVED"), "source change is never blindly rebased");

                    for (boolean inRead : new boolean[] {false, true}) {
                        reset(true); activeSaveStep(); makeActive(0);
                        var primary = new AssertionError("component primary only");
                        if (inRead) ACTORS[0].owner.failure = primary; else server.failure = primary;
                        Throwable caught = null;
                        try { activeSaveStep(); } catch (RuntimeException | Error failure) { caught = failure; }
                        check(caught == primary && activeSaveIssued && server.halts == 0, "original primary Error identity escapes without false halt");
                        int reads = ACTORS[0].owner.reads; activeSaveStep();
                        check(server.saves == 1 && ACTORS[0].owner.reads == reads, "primary failure cannot repeat native controls");
                    }
                    reset(true); P11C4aEvidence.save = true;
                    expectRefusal("exclusive cues", () -> activeSaveStep()); check(server.saves == 0, "conflicting cues never save");
                    reset(true); makeActive(0);
                    expectRefusal("late cue", () -> activeSaveStep()); check(server.saves == 0 && !activeSaveIssued, "late pre-existing ACTIVE is not pre-armed evidence");
                    reset(true); activeSaveStep(); P11C4aEvidence.stop = false; P11C4aEvidence.save = true;
                    expectRefusal("changed mode", () -> activeSaveStep()); check(server.saves == 0, "armed mode cannot change");
                }
                private static void makeActive(int actor) { ACTORS[actor].owner.body.cooldown.data.entries.put("BOOTSTRAP", new Entry(0, 20, 80, 100, 120)); }
                private static void reset(boolean stop) {
                    ACTIVE_SAVE.clear(); activeSaveMode = null; activeSaveIssued = false;
                    lifecycleRequested = false; lifecycleStage = 0; lifecycleBegins = 0; densityMode = "NONE";
                    server.saves = 0; server.halts = 0; server.failure = null;
                    P11C4aEvidence.values.clear(); P11C4aEvidence.save = !stop; P11C4aEvidence.stop = stop;
                    P11C4aEvidence.life = false;
                    for (var actor : ACTORS) actor.owner = new Owner();
                }
                private static Map<String,Object> snapshot(String name, int number) {
                    var actors = new ArrayList<Map<String,Object>>();
                    for (var actor : ACTORS) {
                        var body = actor.owner.body; var entry = body.cooldown.data.entries.get("BOOTSTRAP");
                        actors.add(Map.of("authOrder", actor.index + 1, "epoch", body.source.epoch(), "version", body.source.version(),
                                "kind", body.cooldown.data.kind.name(), "entryPresent", entry != null,
                                "expiresAt", entry == null ? -1L : entry.expiresAt));
                    }
                    return Map.of("checkpoint", name, "number", number, "gameTime", 100L, "actors", List.copyOf(actors));
                }
                private static boolean cue(String leaf) {
                    return switch (leaf) {
                        case "active-save.ready" -> P11C4aEvidence.save;
                        case "active-save-stop.ready" -> P11C4aEvidence.stop;
                        case "lifecycle-death.ready" -> P11C4aEvidence.life;
                        default -> throw new AssertionError("unexpected component cue");
                    };
                }
                // Honest component boundary only: native death/respawn itself is tested in the external exact-method model/native cohort.
                private static void beginNativeLifecycle(int selected) { lifecycleBegins++; }
                private static void require(boolean value, String code) { if (!value) throw new IllegalStateException(code); }
                private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
                private interface Action { boolean run() throws IOException; }
                private static void expectRefusal(String label, Action action) throws IOException {
                    try { action.run(); } catch (IllegalStateException expected) { return; }
                    throw new AssertionError(label);
                }
                /* ACTIVE_SAVE */
                private static final class Actor { final int index; Owner owner = new Owner(); Actor(int index) { this.index = index; } }
                private static final class Server {
                    int saves, halts; Error failure;
                    Server overworld() { return this; } long getGameTime() { return 100; }
                    Server getPlayerList() { return this; }
                    void saveAll() {
                        saves++; if (failure != null) throw failure;
                        for (var actor : ACTORS) actor.owner.body.source = new Version(1, 2);
                    }
                    void halt(boolean wait) { halts++; }
                }
                private record Version(long epoch, long version) { }
                private static final class Owner {
                    final Body body = new Body(); int reads; boolean readback = true, changeDuringRead; Error failure; Version expected;
                    Body body(Actor ignored) { return body; } boolean canCopy(Body supplied) { return supplied == body; }
                    boolean prepareCooldownRetirementReadback(Body supplied, Version written) {
                        reads++; expected = written; if (failure != null) throw failure;
                        if (changeDuringRead) body.source = new Version(1, 3);
                        return readback;
                    }
                }
                private static final class Body { Version source = new Version(1, 1); final Cooldown cooldown = new Cooldown(); }
                private static final class Cooldown { final Data data = new Data(); }
                private static final class Data { final P11CastCooldownData.Kind kind = P11CastCooldownData.Kind.ROUTED; final Map<String,Entry> entries = new LinkedHashMap<>(); }
                private static final class Entry {
                    final int kind, duration; final long acceptedAt, releasedAt, expiresAt; final UUID attemptId = new UUID(1, 2);
                    Entry(int kind, int duration, long accepted, long release, long expiry) { this.kind = kind; this.duration = duration; acceptedAt = accepted; releasedAt = release; expiresAt = expiry; }
                }
                private static final class P11NativeStorageBoundary { static Owner nativeSourceOwner(Actor actor) { return actor.owner; } }
                private static final class P11CastCooldownData { enum Kind { ROUTED } }
                private static final class NormalSpellPolicy { static final NormalSpellPolicy BOOTSTRAP = new NormalSpellPolicy(); String key() { return "BOOTSTRAP"; } }
                private static final class P11C4aEvidence {
                    static boolean save, stop, life; static final List<Object> values = new ArrayList<>();
                    static void write(Path ignored, String leaf, Object value) { values.add(value); }
                }
            }
            """; }

    private void run(String choice) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) root = root.getParent();
        assertNotNull(root);
        var path = root.resolve("src/p11OnlineHarness/java/com/yo1no/gramarye");
        String scene = Files.readString(path.resolve("U0OrdinaryScenario.java"));
        String client = Files.readString(path.resolve("P11OnlineClientHarness.java"));
        String key = method(scene, "    private static String typedStateKey(");
        assertFalse(key.contains("data.clockFloor") || key.contains("body.source.version()"));
        assertTrue(key.contains("entry.attemptId") && key.contains("entry.releasedAt") && key.contains("entry.kind"));
        String server = Files.readString(path.resolve("P11OnlineServerHarness.java"));
        String stop = method(server, "    static void stopped(ServerStoppedEvent event)");
        assertTrue(stop.indexOf("U0OrdinaryScenario.flushStopped") < stop.indexOf("U0OrdinaryTrace.write"));
        assertTrue(stop.indexOf("U0OrdinaryTrace.write") < stop.indexOf("U0OrdinaryScenario.stopped"));
        String tick = method(client, "    static void tick(ClientTickEvent.Post ignored)");
        assertTrue(tick.contains("boolean terminalScreen = minecraft.screen instanceof TitleScreen")
                && tick.contains("|| minecraft.screen instanceof DisconnectedScreen"));
        assertTrue(tick.indexOf("u0LateFinishEligible(terminalScreen, detached") < tick.indexOf("if (!nativeCall && minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen"));
        assertTrue(tick.contains("minecraft.player == null && minecraft.level == null && minecraft.getConnection() == null"));
        assertTrue(tick.indexOf("nativeExit = u0PrepareExitGame") < tick.indexOf("} catch (Exception | Error observationFailure)"));
        assertTrue(tick.indexOf("} catch (Exception | Error observationFailure)") < tick.indexOf("if (nativeExit) { minecraft.stop(); return; }"));
        String exit = method(client, "    private static boolean u0PrepareExitGame(");
        assertTrue(exit.indexOf("u0ExitGameRequested = true") < exit.indexOf("P11C4aEvidence.write"));
        String source = scaffold().replace("/* CONSTANTS */", declaration(scene, "    private static final int TOOL_TICK_LIMIT")
                + declaration(scene, "    private static final int MAX_AUTOMATIC_SNAPSHOTS")
                + declaration(scene, "    private static final List<String> CHECKPOINTS"))
                .replace("/* SAMPLER */", method(scene, "    private static boolean typedStateChanged(")
                + method(scene, "    private static boolean automaticSnapshotAvailable("))
                .replace("/* DOCUMENT */", method(scene, "    private static Map<String, Object> sceneDocument("))
                .replace("/* CLIENT */", method(client, "    private static boolean u0LateFinishEligible(")
                        + method(client, "    private static void u0FlushTrace(")
                        + method(client, "    private static boolean u0ExitGameEligible(")
                        + exit);
        var classes = Files.createDirectory(temporary.resolve("classes")); var java = temporary.resolve("RecoveryControl.java");
        Files.writeString(java, source, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler);
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            assertTrue(Boolean.TRUE.equals(compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none", "-Xlint:all,-serial,-auxiliaryclass", "-Werror",
                            "-classpath", classes.toString(), "-d", classes.toString()), null,
                    manager.getJavaFileObjects(java)).call()), () -> diagnostics.getDiagnostics().toString());
        }
        try (var loader = new URLClassLoader(new java.net.URL[] {classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            loader.loadClass("com.yo1no.gramarye.RecoveryControl").getMethod("run", String.class, Path.class)
                    .invoke(null, choice, temporary);
        }
    }

    private static String declaration(String source, String head) {
        int start = source.indexOf(head); assertTrue(start >= 0, head);
        return source.substring(start, source.indexOf(';', start) + 1) + '\n';
    }

    private static String method(String source, String head) {
        int start = source.indexOf(head); assertTrue(start >= 0, head);
        int open = source.indexOf('{', start), end = open + 1, depth = 1;
        while (depth != 0) { char value = source.charAt(end++); if (value == '{') depth++; else if (value == '}') depth--; }
        return source.substring(start, end) + '\n';
    }

    private static String scaffold() { return """
            package com.yo1no.gramarye;
            import java.io.IOException;
            import java.net.URISyntaxException;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.nio.file.StandardOpenOption;
            import java.util.ArrayList;
            import java.util.LinkedHashMap;
            import java.util.List;
            import java.util.Map;
            public final class RecoveryControl {
                /* CONSTANTS */
                private static final ControlPolicy observedPolicy = null; // Existing bootstrap control fixture.
                private record ControlPolicy(boolean bootstrap, ControlPolicyKey key, int cooldownTicks) { }
                private record ControlPolicyKey(String policyName) { }
                private static final List<Map<String,Object>> SAMPLES = new ArrayList<>(), AUTOMATIC = new ArrayList<>(), SUNSET = new ArrayList<>();
                private static int admitted = 2, checkpoint, sunsetStage;
                private static boolean automaticTruncated, u0TraceFlushAttempted, u0TraceFlushSucceeded, u0ExitGameRequested;
                private static Path output, serverOutput;
                private static String role = "a";
                private static int logouts;
                public static void run(String choice, Path directory) throws Exception {
                    if (choice.equals("sampler")) {
                        check(!typedStateChanged(new String[] {"A:ACTIVE:attempt1", "B:NONE"}, new String[] {"A:ACTIVE:attempt1", "B:NONE"}), "same typed state does not sample every advancing tick");
                        check(typedStateChanged(new String[] {"A:ACTIVE:attempt2", "B:NONE"}, new String[] {"A:ACTIVE:attempt1", "B:NONE"}), "new actual attempt samples");
                        check(typedStateChanged(new String[] {"A:NONE", "B:NONE"}, new String[] {"A:ACTIVE:attempt1", "B:NONE"}), "actual removal samples");
                        check(typedStateChanged(new String[] {"A:PENDING:attempt1", "B:NONE"}, new String[] {null, null}), "initial typed state samples");
                        check(automaticSnapshotAvailable(0) && automaticSnapshotAvailable(63), "first and last slots allowed");
                        check(!automaticSnapshotAvailable(-1) && !automaticSnapshotAvailable(64) && !automaticSnapshotAvailable(65), "no 65th snapshot or invalid count");
                    } else if (choice.equals("partial")) {
                        admitted = 0; checkpoint = 0;
                        var empty = sceneDocument("STOP");
                        check(empty.get("status").equals("PARTIAL_OBSERVATIONS_NOT_U0_ACCEPTANCE") && ((List<?>) empty.get("completed")).isEmpty() && ((List<?>) empty.get("pending")).size() == 11, "empty stop remains partial");
                        admitted = 2; checkpoint = 2; SAMPLES.add(Map.of("control", "authored scalar only")); AUTOMATIC.add(Map.of("kind", "ACTIVE"));
                        var partial = sceneDocument("FINISH");
                        check(((List<?>) partial.get("completed")).size() == 2 && ((List<?>) partial.get("pending")).size() == 9, "exact processed and pending cues");
                        check(partial.get("completionRole").equals("CHECKPOINT_CUES_PROCESSED_NOT_CASE_ACCEPTANCE"), "cue is not a pass");
                        SAMPLES.clear(); check(((List<?>) partial.get("samples")).size() == 1, "document captures immutable list prefix");
                        checkpoint = 11; automaticTruncated = true;
                        var full = sceneDocument("STOP");
                        check(full.get("status").equals("CHECKPOINTS_RECORDED_NOT_U0_ACCEPTANCE") && ((List<?>) full.get("pending")).isEmpty(), "all cues still not acceptance");
                        check(full.get("automaticTruncated").equals(true), "truncation is retained rather than silently passed");
                    } else if (choice.equals("late")) {
                        check(u0LateFinishEligible(true, true, 1, false, true), "late cue after real logout at Title");
                        check(u0LateFinishEligible(true, true, 1, false, true), "native DisconnectedScreen is also a terminal screen by the actual callsite");
                        check(!u0LateFinishEligible(false, false, 1, false, true), "live PLAY never flushes as terminal");
                        check(!u0LateFinishEligible(true, true, 0, false, true), "no forged logout");
                        check(!u0LateFinishEligible(false, true, 1, false, true) && !u0LateFinishEligible(true, false, 1, false, true), "requires actual detached Title tuple");
                        check(!u0LateFinishEligible(true, true, 1, true, true) && !u0LateFinishEligible(true, true, 1, false, false), "once and cue required");
                        output = directory.resolve("closed-client"); Files.createDirectory(output);
                        u0FlushTrace(); u0FlushTrace();
                        check(P11C4aEvidence.writes == 1 && U0OrdinaryTrace.writes == 1 && u0TraceFlushAttempted
                                && u0TraceFlushSucceeded, "exact once successful original file sinks");
                        check(Files.readString(output.resolve("trace.json")).equals("CONTROL_OBSERVATIONS_ONLY"), "retained actual control file");
                        check(u0ExitGameEligible(true, true, 1, true, false, true), "real detached terminal tuple and completed trace may request quit");
                        check(!u0ExitGameEligible(false, true, 1, true, false, true)
                                && !u0ExitGameEligible(true, false, 1, true, false, true), "no quit during PLAY or nonterminal screen");
                        check(!u0ExitGameEligible(true, true, 0, true, false, true)
                                && !u0ExitGameEligible(true, true, 1, false, false, true), "logout and successful flush both required");
                        check(!u0ExitGameEligible(true, true, 1, true, true, true)
                                && !u0ExitGameEligible(true, true, 1, true, false, false), "no duplicate or uncued quit");
                        serverOutput = directory; logouts = 1; P11C4aEvidence.exitCue = true;
                        if (u0PrepareExitGame(true, true)) NativeStop.stop();
                        check(NativeStop.calls == 1 && u0ExitGameRequested
                                && P11C4aEvidence.lastLeaf.equals("native-exit-request.json"), "exclusive scalar request precedes exactly one native stop");
                        var requested = (Map<?,?>) P11C4aEvidence.lastValue;
                        check(requested.get("status").equals("ORIGINAL_STOP_REQUESTED_NOT_PROCESS_EXIT_PROOF")
                                && requested.get("nativeProcessExitProved").equals(false), "request cannot claim actual process exit");
                        check(!u0PrepareExitGame(true, true) && NativeStop.calls == 1, "persistent cue does not repeat quit");
                        output = directory.resolve("native-stop-failed"); Files.createDirectory(output);
                        u0ExitGameRequested = false; var nativePrimary = new AssertionError("native stop primary");
                        NativeStop.failure = nativePrimary; Throwable nativeCaught = null;
                        try { if (u0PrepareExitGame(true, true)) NativeStop.stop(); }
                        catch (RuntimeException | Error failure) { nativeCaught = failure; }
                        check(nativeCaught == nativePrimary && !u0PrepareExitGame(true, true) && NativeStop.calls == 2,
                                "native Error remains identical outside observation catch and cannot retry");
                        NativeStop.failure = null;
                        output = directory.resolve("exit-request-failed"); Files.createDirectory(output);
                        u0ExitGameRequested = false; var requestPrimary = new AssertionError("exit output primary");
                        P11C4aEvidence.failure = requestPrimary; Throwable requestCaught = null;
                        try { if (u0PrepareExitGame(true, true)) NativeStop.stop(); }
                        catch (RuntimeException | Error failure) { requestCaught = failure; }
                        check(requestCaught == requestPrimary && u0ExitGameRequested && !u0PrepareExitGame(true, true)
                                && NativeStop.calls == 2, "failed request output never stops or retries");
                        P11C4aEvidence.failure = null;
                        var primary = new AssertionError("metadata control");
                        var secondary = new AssertionError("trace control");
                        for (boolean traceAlsoFails : new boolean[] {false, true}) {
                            output = directory.resolve(traceAlsoFails ? "both-failed" : "metadata-failed"); Files.createDirectory(output);
                            u0TraceFlushAttempted = false; u0TraceFlushSucceeded = false; u0ExitGameRequested = false;
                            P11C4aEvidence.failure = primary;
                            U0OrdinaryTrace.failure = traceAlsoFails ? secondary : null;
                            int before = U0OrdinaryTrace.writes; Throwable caught = null;
                            try { u0FlushTrace(); } catch (RuntimeException | Error failure) { caught = failure; }
                            check(caught == primary && U0OrdinaryTrace.writes == before + 1 && U0OrdinaryTrace.failed,
                                    "metadata failure marks failed and still attempts trace with same primary");
                            check(!u0TraceFlushSucceeded && !u0PrepareExitGame(true, true), "failed metadata flush cannot request native quit");
                            if (!traceAlsoFails) check(Files.readString(output.resolve("trace.json")).equals("FAILED_CONTROL_OBSERVATIONS_ONLY"), "failed observation retained");
                            u0FlushTrace(); check(U0OrdinaryTrace.writes == before + 1, "duplicate finish never rewrites after failure");
                        }
                        output = directory.resolve("metadata-uri-failed"); Files.createDirectory(output);
                        u0TraceFlushAttempted = false; u0TraceFlushSucceeded = false; u0ExitGameRequested = false;
                        P11C4aEvidence.failure = null;
                        U0OrdinaryTrace.failure = null; U0OrdinaryTrace.failed = false;
                        var uri = new URISyntaxException("controlled-source", "controlled URI metadata failure");
                        P11OnlineServerHarness.uriFailure = uri;
                        int beforeUri = U0OrdinaryTrace.writes; Throwable uriCaught = null;
                        try { u0FlushTrace(); } catch (IOException | URISyntaxException | RuntimeException | Error failure) { uriCaught = failure; }
                        check(uriCaught == uri && U0OrdinaryTrace.writes == beforeUri + 1 && U0OrdinaryTrace.failed,
                                "checked URI metadata failure preserves identity and still writes failed trace");
                        check(!Files.exists(output.resolve("loaded-sources-terminal.json")), "failed source metadata is not fabricated");
                        check(!u0TraceFlushSucceeded && !u0PrepareExitGame(true, true), "checked metadata failure cannot request quit");
                        u0FlushTrace(); check(U0OrdinaryTrace.writes == beforeUri + 1, "URI failure is terminal once without retry");
                        output = directory.resolve("trace-only-failed"); Files.createDirectory(output);
                        u0TraceFlushAttempted = false; u0TraceFlushSucceeded = false; u0ExitGameRequested = false;
                        P11OnlineServerHarness.uriFailure = null; U0OrdinaryTrace.failure = secondary;
                        Throwable traceCaught = null;
                        try { u0FlushTrace(); } catch (RuntimeException | Error failure) { traceCaught = failure; }
                        check(traceCaught == secondary && !u0TraceFlushSucceeded && !u0PrepareExitGame(true, true),
                                "trace sink failure preserves primary and cannot request quit");
                    } else throw new AssertionError(choice);
                }
                private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
                /* SAMPLER */
                /* DOCUMENT */
                /* CLIENT */
            }
            final class P11C4aEvidence {
                static int writes; static Error failure;
                static boolean exitCue; static String lastLeaf; static Object lastValue;
                static boolean cuePresent(Path directory, String leaf) { return exitCue; }
                static void write(Path output, String leaf, Object value) throws IOException {
                    writes++; if (failure != null) throw failure;
                    Files.writeString(output.resolve(leaf), "SOURCE_CONTROL_ONLY", StandardOpenOption.CREATE_NEW);
                    lastLeaf = leaf; lastValue = value;
                }
            }
            final class NativeStop {
                static int calls; static Error failure;
                static void stop() { calls++; if (failure != null) throw failure; }
            }
            final class P11OnlineServerHarness {
                static URISyntaxException uriFailure;
                static Map<String,Object> u0LoadedSources() throws URISyntaxException {
                    if (uriFailure != null) throw uriFailure;
                    return Map.of("role", "CONTROL_ONLY");
                }
            }
            final class U0OrdinaryTrace {
                static int writes; static boolean failed; static Error failure;
                static void note(String tag, long a, long b, long c, long d, long e, long f,
                        int first, int second, int third, String text) { if (tag.equals("OBSERVATION_FAILURE")) failed = true; }
                static void write(Path output) throws IOException {
                    writes++; if (failure != null) throw failure;
                    Files.writeString(output.resolve("trace.json"), failed ? "FAILED_CONTROL_OBSERVATIONS_ONLY" : "CONTROL_OBSERVATIONS_ONLY", StandardOpenOption.CREATE_NEW);
                }
            }
            """; }
}
