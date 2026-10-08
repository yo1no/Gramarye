package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executes the complete actual cooldown owner and wire projection over typed platform/source fakes.
 * The fakes are not authentication, Attachment serialization, physical writer, or native evidence. */
final class P11CooldownProjectionTest {
    @TempDir static Path temporary;
    private static URLClassLoader loader;
    private static Class<?> harness;

    @BeforeAll static void compileActualOwner() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        var root = projectRoot().resolve("src/main/java/com/yo1no/gramarye");
        var sources = temporary.resolve("sources");
        var classes = Files.createDirectories(temporary.resolve("classes"));
        var units = new ArrayList<Path>();
        units.add(root.resolve("P11CastCooldownService.java"));
        units.add(root.resolve("P11CastCooldownData.java"));
        for (String leaf : List.of("SkillCooldownSnapshot.java", "CooldownSnapshotEntry.java",
                "P7NetworkBounds.java", "P7SemanticInvariantException.java")) {
            units.add(root.resolve("magic/network").resolve(leaf));
        }
        var boundary = Files.readString(root.resolve("magic/network/P7ServerAuthorizationBoundary.java"));
        var actualProjection = boundary.substring(boundary.indexOf("    /** Root-injected projection only:"),
                boundary.indexOf("    @FunctionalInterface\n    public interface RootIngressPort"));
        units.add(write(sources, "com/yo1no/gramarye/magic/network/P7ServerAuthorizationBoundary.java", """
                package com.yo1no.gramarye.magic.network;
                import java.util.List;
                import java.util.Objects;
                import com.yo1no.gramarye.magic.definition.document.SkillReference;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.server.level.ServerPlayer;
                public final class P7ServerAuthorizationBoundary {
                %s
                }
                """.formatted(actualProjection)));
        for (String type : List.of("SkillId", "SkillRevision", "SkillReference")) {
            String pkg = type.equals("SkillReference") ? "com.yo1no.gramarye.magic.definition.document"
                    : "com.yo1no.gramarye.magic.api.id";
            String fields = switch (type) {
                case "SkillId" -> "java.util.UUID value";
                case "SkillRevision" -> "int value";
                default -> "SkillId skillId, SkillRevision revision";
            };
            units.add(write(sources, pkg.replace('.', '/') + "/" + type + ".java",
                    "package " + pkg + "; import com.yo1no.gramarye.magic.api.id.*; public record "
                            + type + "(" + fields + ") {}"));
        }
        units.add(write(sources, "net/minecraft/nbt/Tag.java", """
                package net.minecraft.nbt;
                public final class Tag {
                    public static final int TAG_END = 0;
                    public int getId() { return 10; }
                    public Tag copy() { return new Tag(); }
                }
                """));
        units.add(write(sources, "net/minecraft/server/MinecraftServer.java", """
                package net.minecraft.server;
                import java.util.HashMap;
                import java.util.UUID;
                import net.minecraft.server.level.ServerPlayer;
                public final class MinecraftServer {
                    public boolean main = true;
                    public World world = new World();
                    public final Players players = new Players();
                    public boolean isSameThread() { return main; }
                    public World overworld() { return world; }
                    public Players getPlayerList() { return players; }
                    public static final class World {
                        public long time = 1000;
                        public long getGameTime() { return time; }
                    }
                    public static final class Players {
                        public final HashMap<UUID, ServerPlayer> values = new HashMap<>();
                        public ServerPlayer getPlayer(UUID id) { return values.get(id); }
                    }
                }
                """));
        units.add(write(sources, "net/minecraft/server/level/ServerPlayer.java", """
                package net.minecraft.server.level;
                import java.util.UUID;
                import net.minecraft.server.MinecraftServer;
                public final class ServerPlayer {
                    public final MinecraftServer server;
                    public final UUID id;
                    public final Listener connection = new Listener();
                    public Object cooldown;
                    public java.util.function.Consumer<Object> publication;
                    public Runnable beforePublication = () -> {};
                    public ServerPlayer(MinecraftServer server, UUID id) {
                        this.server = server; this.id = id; connection.player = this;
                    }
                    public MinecraftServer getServer() { return server; }
                    public UUID getUUID() { return id; }
                    public boolean isRemoved() { return false; }
                    public boolean isAlive() { return true; }
                    public static final class Listener {
                        public ServerPlayer player;
                        public boolean accepting = true;
                        public final Connection actual = new Connection();
                        public boolean isAcceptingMessages() { return accepting; }
                        public Connection getConnection() { return actual; }
                    }
                    public static final class Connection {
                        public boolean connected = true;
                        public boolean isConnected() { return connected; }
                    }
                }
                """));
        units.add(write(sources, "com/yo1no/gramarye/ProjectionPlatform.java", platformSource()));
        units.add(write(sources, "com/yo1no/gramarye/ProjectionHarness.java", harnessSource()));
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            assertTrue(Boolean.TRUE.equals(compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none", "-Xlint:all,-serial,-auxiliaryclass", "-Werror",
                            "-classpath", classes.toString(), "-d", classes.toString()),
                    null, manager.getJavaFileObjectsFromPaths(units)).call()),
                    () -> diagnostics.getDiagnostics().toString());
        }
        loader = new URLClassLoader(new java.net.URL[] {classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        harness = loader.loadClass("com.yo1no.gramarye.ProjectionHarness");
    }

    @AfterAll static void closeLoader() throws Exception {
        try {
            if (loader != null) { loader.close(); }
        } finally {
            harness = null;
            loader = null;
        }
    }
    @Test void unknownEquipmentEmptyRoutingAndUnknownSourceRemainDistinct() throws Exception { run("routing"); }
    @Test void localAndWholeQuarantinePreserveEveryKnownEquippedReference() throws Exception { run("quarantine"); }
    @Test void realAttemptsOrphanRecoveryAndFailedWriterStatesDoNotInventReadiness() throws Exception { run("obligations"); }
    @Test void captureRejectsActualSourceCellEquipmentClockAndActorChanges() throws Exception { run("capture"); }
    @Test void policyReentryAndOriginalFailuresNeverProduceACurrentMixedCapture() throws Exception { run("reentry"); }
    @Test void sameAccountReplacementKeepsExactOldActorAttemptAndArmsOnlyCurrentMaterial() throws Exception { run("handoff"); }
    @Test void actualAdmissionChecksOldObligationsBeforeZeroAndRevisionChanges() throws Exception { run("admission"); }
    @Test void actualPendingInstallationRejectsStaleCapturesWithoutPublishing() throws Exception { run("installation"); }
    @Test void actualAdmissionUsesTrustedClockAndFiniteReleaseBounds() throws Exception { run("bounds"); }
    @Test void actualReceiptSettlementPreservesLatestPublicationAndFailedSaveTruth() throws Exception { run("publication"); }

    private static void run(String name) throws Exception { harness.getMethod("run", String.class).invoke(null, name); }
    private static Path write(Path root, String name, String source) throws Exception {
        var path = root.resolve(name); Files.createDirectories(path.getParent());
        return Files.writeString(path, source, StandardCharsets.UTF_8);
    }
    private static Path projectRoot() {
        var path = Path.of("").toAbsolutePath().normalize();
        while (path != null && !Files.isRegularFile(path.resolve("settings.gradle"))) { path = path.getParent(); }
        return java.util.Objects.requireNonNull(path, "project root");
    }

    private static String platformSource() { return """
            package com.yo1no.gramarye;
            import java.util.*;
            import net.minecraft.server.MinecraftServer;
            import net.minecraft.server.level.ServerPlayer;
            import com.yo1no.gramarye.magic.definition.document.SkillReference;
            enum CooldownRejectionReason { ACTIVE, PENDING, RECOVERY, CLOCK, UNAVAILABLE }
            final class P11ControlBudgets { static final class Resources { static final class AccountOwner {} } }
            final class P11ReceiptLedger {
                record Source(long epoch, long version) {}
                enum WriterKind { PLAYER_DATA, STATISTICS }
            }
            final class P11FoundationService {
                P11QualifiedSourceOwner source;
                P11QualifiedSourceOwner sourceOwner(MinecraftServer server) { return source; }
                Optional<Boolean> startupState(MinecraftServer server) { return Optional.of(true); }
            }
            final class P11QualifiedSourceOwner {
                enum InputKind { PRIMARY, HOST_PRIMARY, ABSENT, MEMORY, OTHER }
                static final class SourceUnavailable extends RuntimeException {}
                static final class WorkReservation {
                    final ServerPlayer actor;
                    WorkReservation(ServerPlayer actor) { this.actor = actor; }
                    boolean qualifies(ServerPlayer exact) { return exact == actor; }
                }
                static final class Account {
                    final P11ControlBudgets.Resources.AccountOwner resource = new P11ControlBudgets.Resources.AccountOwner();
                    Body current, candidate;
                }
                static final class Material {
                    P11CastCooldownData data;
                    final ServerPlayer actor;
                    Material(ServerPlayer actor, P11CastCooldownData data) { this.actor = actor; this.data = data; }
                    boolean current(ServerPlayer exact) { return actor == exact && exact.cooldown == data; }
                }
                static final class Body {
                    final ServerPlayer actor;
                    final Account account;
                    final Material cooldown;
                    P11ReceiptLedger.Source source = new P11ReceiptLedger.Source(11, 7);
                    InputKind inputKind = InputKind.PRIMARY;
                    Body(ServerPlayer actor, P11CastCooldownData data) {
                        this(actor, data, new Account());
                    }
                    Body(ServerPlayer actor, P11CastCooldownData data, Account account) {
                        this.actor = actor; this.account = account;
                        cooldown = new Material(actor, data); account.current = this; actor.cooldown = data;
                    }
                }
                record Equipped(int slot, SkillReference reference) {}
                static final class Equipment {
                    boolean available = true;
                    long generation;
                    int captures;
                    List<Equipped> entries = List.of();
                    Snapshot captureP11Equipment(ServerPlayer actor) {
                        captures++; return new Snapshot(actor, generation, available, List.copyOf(entries), this);
                    }
                }
                record Snapshot(ServerPlayer actor, long generation, boolean available,
                        List<Equipped> entries, Equipment owner) {
                    boolean isCurrent(ServerPlayer exact) {
                        return exact == actor && owner.generation == generation && owner.available == available;
                    }
                }
                Body value;
                boolean copy = true, serialize = true;
                final Equipment equipment = new Equipment();
                Body body(ServerPlayer actor) { return value != null && value.actor == actor ? value : null; }
                Body current(UUID id) { return value != null && value.actor.getUUID().equals(id) ? value : null; }
                boolean canCopy(Body body) { return copy && body != null && value == body; }
                boolean canSerialize(Body body) { return serialize && canCopy(body); }
                Body cooldownWorkRecipient(WorkReservation work, ServerPlayer actor) {
                    return work.qualifies(actor) && value != null && value.actor.getUUID().equals(actor.getUUID()) ? value : null;
                }
                Equipment cooldownEquipmentOwner() { return equipment; }
            }
            final class P11CastCooldownAttachments {
                static P11CastCooldownData existing(ServerPlayer actor) { return (P11CastCooldownData) actor.cooldown; }
                static void replace(ServerPlayer actor, P11CastCooldownData previous, P11CastCooldownData next) {
                    if (actor.cooldown != previous) { throw new IllegalStateException("test publication mismatch"); }
                    actor.beforePublication.run();
                    actor.cooldown = next; actor.publication.accept(next);
                }
            }
            // NBT is deliberately not under test. The actual data container is used;
            // only its external copy/encoding boundaries are typed inert placeholders.
            final class P11CastCooldownNbtSize {
                record Measurement(boolean fits) {}
                static Measurement measure(net.minecraft.nbt.Tag tag, long maximum, int depth) { return new Measurement(true); }
            }
            final class P11CastCooldownCodec { static Object write(P11CastCooldownData data) { return data; } }
            """; }

    private static String harnessSource() { return """
            package com.yo1no.gramarye;
            import java.util.*;
            import net.minecraft.server.MinecraftServer;
            import net.minecraft.server.level.ServerPlayer;
            import com.yo1no.gramarye.magic.api.id.*;
            import com.yo1no.gramarye.magic.definition.document.SkillReference;
            import static com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary.*;
            public final class ProjectionHarness {
                static final UUID ID = new UUID(1, 1), OTHER = new UUID(1, 2), ATTEMPT = new UUID(2, 3);
                static SkillReference ref(UUID id, int revision) { return new SkillReference(new SkillId(id), new SkillRevision(revision)); }
                static P11CastCooldownData.Entry pending(UUID id) { return P11CastCooldownData.Entry.pending(id, 3, 120, 1000, ATTEMPT, 1101); }
                static P11CastCooldownData data(P11CastCooldownData.Entry... entries) {
                    var values = new HashMap<UUID, P11CastCooldownData.Entry>();
                    for (var entry : entries) values.put(entry.skillId, entry);
                    return P11CastCooldownData.routed(1000, values);
                }
                static final class Receipt implements P11CastCooldownService.ReleaseReceipt {
                    P11CastCooldownService.ReleaseFact fact = P11CastCooldownService.ReleaseFact.UNPUBLISHED;
                    long released = -1;
                    public UUID attemptId() { return ATTEMPT; }
                    public P11CastCooldownService.ReleaseFact fact() { return fact; }
                    public long releasedAt() { return released; }
                }
                static final class Fixture {
                    final MinecraftServer server = new MinecraftServer();
                    final ServerPlayer actor = new ServerPlayer(server, new UUID(9, 9));
                    final P11QualifiedSourceOwner.WorkReservation work = new P11QualifiedSourceOwner.WorkReservation(actor);
                    final P11QualifiedSourceOwner source = new P11QualifiedSourceOwner();
                    final P11FoundationService foundation = new P11FoundationService();
                    final P11QualifiedSourceOwner.Body body;
                    final P11CastCooldownService service;
                    int policy = 120, policyCalls, publications;
                    Runnable policyCallback = () -> {};
                    Runnable afterPublication = () -> {};
                    boolean publishVersion = true;
                    Fixture(P11CastCooldownData material) {
                        body = new P11QualifiedSourceOwner.Body(actor, material); source.value = body; foundation.source = source;
                        server.players.values.put(actor.id, actor);
                        equip(new P11QualifiedSourceOwner.Equipped(0, ref(ID, 7)));
                        actor.publication = value -> {
                            body.cooldown.data = (P11CastCooldownData) value; publications++;
                            if (publishVersion) body.source = new P11ReceiptLedger.Source(11, body.source.version() + 1);
                            afterPublication.run();
                        };
                        service = new P11CastCooldownService(foundation, (s, a, r) -> {
                            check(s == server && a == source.value.actor, "policy exact current actor"); policyCalls++; policyCallback.run();
                            return policy == -1 ? OptionalInt.empty() : OptionalInt.of(policy);
                        });
                        service.started(server); check(service.materialAdopted(source, body), "actual cell adoption");
                    }
                    void equip(P11QualifiedSourceOwner.Equipped... entries) {
                        source.equipment.entries = List.of(entries); source.equipment.generation++;
                    }
                    SyncCapture capture() { return service.prepareAndCapture(server, actor); }
                    SyncProjection valid() { var capture = capture(); check(capture.isCurrent(), "capture current"); return capture.projection(); }
                    void failedWriter(boolean failed) { service.writerFinished(source, body, P11ReceiptLedger.WriterKind.PLAYER_DATA, !failed); }
                    P11CastCooldownService.Admission prepare(int duration, SkillReference reference, Receipt receipt) {
                        return service.prepareAdmission(server, actor, reference, duration, 0, 101, receipt, work);
                    }
                    P11CastCooldownService.Prepared install(Receipt receipt) {
                        var admission = prepare(120, ref(ID, 7), receipt);
                        check(admission instanceof P11CastCooldownService.Prepared, "actual prepared admission");
                        var prepared = (P11CastCooldownService.Prepared) admission;
                        check(service.installPending(prepared), "actual pending publication"); return prepared;
                    }
                    P11QualifiedSourceOwner.Body replacement() {
                        var nextActor = new ServerPlayer(server, actor.id);
                        var next = new P11QualifiedSourceOwner.Body(nextActor, body.cooldown.data, body.account);
                        next.source = new P11ReceiptLedger.Source(12, 0);
                        source.value = next; server.players.values.put(actor.id, nextActor);
                        actor.connection.actual.connected = false;
                        nextActor.publication = value -> {
                            next.cooldown.data = (P11CastCooldownData) value; publications++;
                            next.source = new P11ReceiptLedger.Source(next.source.epoch(), next.source.version() + 1);
                        };
                        return next;
                    }
                }
                public static void run(String scenario) {
                    switch (scenario) {
                        case "routing" -> routing(); case "quarantine" -> quarantine();
                        case "obligations" -> obligations(); case "capture" -> capture(); case "reentry" -> reentry();
                        case "handoff" -> handoff();
                        case "admission" -> admission(); case "installation" -> installation();
                        case "bounds" -> bounds(); case "publication" -> publication();
                        default -> throw new AssertionError(scenario);
                    }
                }
                static void routing() {
                    var f = new Fixture(data()); f.equip();
                    var empty = f.valid(); check(empty.sourceState() == SyncSourceState.AVAILABLE && empty.entries().isEmpty(), "true empty");
                    check(empty.sourceEpoch() == 11 && empty.sourceVersion() == 7 && f.source.equipment.captures == 1, "single equipment/version");
                    f.source.equipment.available = false;
                    var unknown = f.valid(); check(unknown.sourceReason() == SyncReason.EQUIPMENT_UNKNOWN && unknown.entries().isEmpty()
                            && unknown.sourceEpoch() == 11, "unknown routing is not empty Ready or source sentinel");
                    f.source.copy = false;
                    var missing = f.capture(); check(missing.isCurrent() && missing.projection().sourceEpoch() == 0
                            && missing.projection().sourceVersion() == 0 && missing.projection().sourceReason() == SyncReason.SOURCE_UNAVAILABLE
                            && missing.projection().entries().isEmpty(), "only unknown source sentinel");
                    f.source.copy = true; check(!missing.isCurrent(), "source recovered before submit");
                    f.source.value = null; var absent = f.capture(); check(absent.isCurrent(), "missing body sentinel current");
                    f.source.value = f.body; check(!absent.isCurrent(), "new body invalidates unavailable capture");
                    var notReady = new Fixture(P11CastCooldownData.unbound()).valid();
                    check(notReady.sourceEpoch() == 11 && notReady.sourceReason() == SyncReason.NOT_READY
                            && notReady.entries().getFirst().reference().equals(ref(ID, 7)), "known unbound source preserves routing");
                }
                static void quarantine() {
                    for (var reason : List.of(P11CastCooldownData.Reason.MALFORMED, P11CastCooldownData.Reason.UNSUPPORTED,
                            P11CastCooldownData.Reason.BYTE_LIMIT)) {
                        var local = reason == P11CastCooldownData.Reason.BYTE_LIMIT
                                ? P11CastCooldownData.Entry.marker(OTHER, reason, 298, 297)
                                : P11CastCooldownData.Entry.raw(OTHER, reason, new net.minecraft.nbt.Tag());
                        var f = new Fixture(data(local));
                        f.equip(new P11QualifiedSourceOwner.Equipped(0, ref(ID, 7)), new P11QualifiedSourceOwner.Equipped(5, ref(OTHER, 9)));
                        var p = f.valid(); check(p.sourceState() == SyncSourceState.PARTIAL && p.entries().size() == 2, "local partial");
                        check(p.entries().getFirst().state() == SyncEntryState.READY && p.entries().get(1).state() == SyncEntryState.UNAVAILABLE,
                                "local quarantine cannot poison unrelated key or omit broken equipped key");
                        check(p.entries().get(1).reason() == p.sourceReason(), "local reason map");
                        f.equip(new P11QualifiedSourceOwner.Equipped(0, ref(ID, 7)));
                        check(f.valid().sourceState() == SyncSourceState.PARTIAL, "unequipped quarantine remains partial source");
                        var whole = new Fixture(reason == P11CastCooldownData.Reason.BYTE_LIMIT
                                ? P11CastCooldownData.marker(reason, 90181, 90180)
                                : P11CastCooldownData.raw(reason, new net.minecraft.nbt.Tag()));
                        whole.equip(new P11QualifiedSourceOwner.Equipped(0, ref(ID, 7)), new P11QualifiedSourceOwner.Equipped(63, ref(OTHER, 9)));
                        var blocked = whole.valid(); check(blocked.sourceEpoch() == 11 && blocked.sourceState() == SyncSourceState.UNAVAILABLE
                                && blocked.entries().size() == 2, "whole quarantine keeps real source and every ref");
                        for (var entry : blocked.entries()) check(entry.state() == SyncEntryState.UNAVAILABLE
                                && entry.reason() == blocked.sourceReason() && entry.remainingTicks() == 0, "whole reason parity");
                    }
                }
                static void obligations() {
                    var live = new Fixture(data()); var receipt = new Receipt(); live.install(receipt);
                    check(live.valid().entries().getFirst().state() == SyncEntryState.PENDING, "unpublished owned receipt is pending");
                    receipt.fact = P11CastCooldownService.ReleaseFact.PENDING;
                    check(live.valid().entries().getFirst().reason() == SyncReason.PENDING_RELEASE, "published pending");
                    live.failedWriter(true); check(live.valid().entries().getFirst().reason() == SyncReason.SAVE_FAILED, "pending failed writer");
                    var orphan = new Fixture(data(pending(ID))); var p = orphan.valid();
                    check(p.sourceState() == SyncSourceState.AVAILABLE && p.entries().getFirst().state() == SyncEntryState.RECOVERY_REQUIRED,
                            "orphan is not live pending or source unavailable");
                    var uncertain = new Fixture(data(pending(ID).uncertain(P11CastCooldownData.Reason.PUBLICATION_UNKNOWN, 1000, false, -1)));
                    check(uncertain.valid().entries().getFirst().reason() == SyncReason.OUTCOME_UNCERTAIN, "uncertain no countdown");
                    var active = new Fixture(data(pending(ID).active(1000))); active.failedWriter(true);
                    active.equip(new P11QualifiedSourceOwner.Equipped(0, ref(ID, 9)), new P11QualifiedSourceOwner.Equipped(5, ref(ID, 10)));
                    p = active.valid(); check(p.sourceState() == SyncSourceState.AVAILABLE && p.entries().size() == 2, "writer not whole downgrade");
                    for (var e : p.entries()) check(e.state() == SyncEntryState.ACTIVE && e.reason() == SyncReason.SAVE_FAILED
                            && e.remainingTicks() == 120, "same skill/current refs share original expiry");
                    check(p.entries().getFirst().reference().revision().value() == 9 && p.entries().get(1).reference().revision().value() == 10,
                            "snapshot must not replay obligation revision");
                    active.server.world.time = 1120; p = active.valid();
                    check(p.entries().getFirst().state() == SyncEntryState.UNAVAILABLE
                            && p.entries().getFirst().reason() == SyncReason.SAVE_FAILED, "pruned but failed save is not Active or Ready");
                    active.failedWriter(false); check(active.valid().entries().getFirst().state() == SyncEntryState.READY, "successful writer permits ready");
                    var clear = new Fixture(data()); clear.failedWriter(true);
                    check(clear.valid().entries().getFirst().reason() == SyncReason.SAVE_FAILED, "known clear failed save");
                }
                static void capture() {
                    for (int mutation = 0; mutation < 10; mutation++) {
                        var f = new Fixture(data()); var capture = f.capture(); check(capture.isCurrent(), "baseline current");
                        switch (mutation) {
                            case 0 -> f.body.source = new P11ReceiptLedger.Source(11, 8);
                            case 1 -> f.equip(new P11QualifiedSourceOwner.Equipped(0, ref(OTHER, 8)));
                            case 2 -> f.server.world.time++;
                            case 3 -> f.server.main = false;
                            case 4 -> f.actor.connection.actual.connected = false;
                            case 5 -> f.server.players.values.remove(f.actor.id);
                            case 6 -> f.body.account.candidate = f.body;
                            case 7 -> f.failedWriter(true);
                            case 8 -> { f.publishVersion = false; f.install(new Receipt()); }
                            case 9 -> f.actor.connection.player = new ServerPlayer(f.server, f.actor.id);
                            default -> throw new AssertionError();
                        }
                        check(!capture.isCurrent(), "stale capture accepted mutation " + mutation);
                    }
                    var f = new Fixture(data()); f.server.world.time = 999;
                    var rollback = f.valid(); check(rollback.sourceReason() == SyncReason.CLOCK && rollback.sourceEpoch() == 11
                            && rollback.entries().getFirst().state() == SyncEntryState.UNAVAILABLE, "clock rollback is unavailable not clamped");
                    f.server.world = null; check(f.valid().sourceReason() == SyncReason.CLOCK, "missing overworld");
                }
                static void reentry() {
                    for (int policy : new int[] {-1, -2, 601}) {
                        var f = new Fixture(data()); f.policy = policy;
                        check(f.valid().sourceState() == SyncSourceState.AVAILABLE
                                && f.valid().entries().getFirst().reason() == SyncReason.PROVIDER, "bad policy is local unavailable not zero");
                    }
                    var f = new Fixture(data());
                    f.policyCallback = () -> f.equip(new P11QualifiedSourceOwner.Equipped(0, ref(OTHER, 99)));
                    var capture = f.capture(); check(!capture.isCurrent() && capture.projection().entries().getFirst().reference().equals(ref(ID, 7)),
                            "equipment reentry must discard immutable old capture, not mix new reference");
                    var changed = new Fixture(data());
                    changed.policyCallback = () -> changed.body.source = new P11ReceiptLedger.Source(12, 0);
                    check(!changed.capture().isCurrent(), "policy source reentry");
                    for (boolean error : new boolean[] {false, true}) {
                        var failing = new Fixture(data());
                        Throwable primary = error ? new AssertionError("owned test primary") : new IllegalStateException("owned test primary");
                        failing.policyCallback = () -> { if (primary instanceof Error e) throw e; throw (RuntimeException) primary; };
                        try { failing.capture(); throw new AssertionError("policy fault swallowed"); }
                        catch (RuntimeException | Error observed) { check(observed == primary, "primary changed"); }
                        check(failing.publications == 0, "observation fault created material");
                    }
                }
                static void handoff() {
                    for (boolean generationChanged : new boolean[] {false, true}) {
                        var f = new Fixture(data()); var receipt = new Receipt(); var pending = f.install(receipt);
                        receipt.fact = P11CastCooldownService.ReleaseFact.PENDING;
                        var oldCapture = f.capture(); var oldMaterial = f.body.cooldown.data;
                        var next = f.replacement();
                        check(f.service.materialAdopted(f.source, next), "same-account current B adoption");
                        check(!oldCapture.isCurrent(), "old A capture invalid after B");
                        var current = f.service.prepareAndCapture(f.server, next.actor);
                        check(current.isCurrent() && current.projection().sourceEpoch() == 12
                                && current.projection().entries().getFirst().state() == SyncEntryState.PENDING,
                                "B retains the exact live pending receipt, not orphan recovery");
                        check(f.service.prepareArm(pending, f.work, next.actor, 1) == null, "B cannot impersonate original cause A");
                        check(f.service.prepareArm(pending, new P11QualifiedSourceOwner.WorkReservation(f.actor), f.actor, 1) == null,
                                "same A cannot substitute another work receipt");
                        next.account.candidate = next;
                        check(f.service.prepareArm(pending, f.work, f.actor, 1) == null, "candidate cannot arm");
                        next.account.candidate = null;
                        var arm = f.service.prepareArm(pending, f.work, f.actor, 1);
                        check(arm != null, "retained original A arms qualified current B through same W");
                        if (generationChanged) {
                            f.server.world.time++;
                            check(f.service.prepareAndCapture(f.server, next.actor).isCurrent(), "real reconciliation advances cell generation");
                        }
                        receipt.released = arm.releasedAt(); receipt.fact = P11CastCooldownService.ReleaseFact.ARM;
                        int before = f.publications;
                        f.service.completeArm(arm);
                        var active = next.cooldown.data.entries.get(ID);
                        check(active.kind == 0 && active.releasedAt == 1000 && active.expiresAt == 1120
                                && active.attemptId.equals(ATTEMPT), "exact ARM survives handoff and generation drift");
                        check(f.body.cooldown.data == oldMaterial && oldMaterial.entries.get(ID).kind == 1
                                && f.actor.cooldown == oldMaterial, "no old A material publication");
                        check(f.publications == before + 1 && next.source.epoch() == 12, "one current B publication");
                        f.service.completeArm(arm);
                        check(f.publications == before + 1, "already settled exact ARM is not replayed");
                    }
                    var wrong = new Fixture(data()); var receipt = new Receipt(); var pending = wrong.install(receipt);
                    receipt.fact = P11CastCooldownService.ReleaseFact.PENDING;
                    var next = wrong.replacement(); next.actor.cooldown = data(); next.cooldown.data = (P11CastCooldownData) next.actor.cooldown;
                    check(!wrong.service.materialAdopted(wrong.source, next), "nonmatching B material not adopted");
                    check(wrong.service.prepareArm(pending, wrong.work, wrong.actor, 1) == null, "nonmatching B cannot arm");
                    var revoked = new Fixture(data()); var noRelease = new Receipt(); var held = revoked.install(noRelease);
                    noRelease.fact = P11CastCooldownService.ReleaseFact.NO_RELEASE;
                    check(revoked.service.prepareArm(held, revoked.work, revoked.actor, 1) == null, "NO_RELEASE cannot arm");
                }
                static void admission() {
                    var zero = new Fixture(data()); var untouched = zero.body.cooldown.data;
                    check(zero.prepare(0, ref(ID, 7), new Receipt()) instanceof P11CastCooldownService.Zero,
                            "actual zero admission after qualification");
                    check(zero.publications == 0 && zero.body.cooldown.data == untouched && zero.policyCalls == 0,
                            "D0 creates no pending obligation or replacement policy lookup");
                    for (int duration : new int[] {1, 120, 600}) {
                        var f = new Fixture(data()); var receipt = new Receipt();
                        var pending = prepared(f.prepare(duration, ref(ID, 7), receipt));
                        check(f.publications == 0 && f.body.cooldown.data.entries.isEmpty(), "prepare is unpublished");
                        check(f.service.installPending(pending) && f.publications == 1, "positive install once");
                        var actual = f.body.cooldown.data.entries.get(ID);
                        check(actual.kind == 1 && actual.duration == duration && actual.revision == 7
                                && actual.acceptedAt == 1000 && actual.releaseNotAfter == 1101
                                && actual.attemptId.equals(ATTEMPT), "exact positive pending values");
                        check(!f.service.installPending(pending) && f.publications == 1, "duplicate install is refused");
                    }
                    var obligations = List.of(pending(ID).active(1000), pending(ID),
                            pending(ID).uncertain(P11CastCooldownData.Reason.RELEASE_UNKNOWN, 1000, false, -1),
                            P11CastCooldownData.Entry.raw(ID, P11CastCooldownData.Reason.MALFORMED, new net.minecraft.nbt.Tag()));
                    var expected = List.of(CooldownRejectionReason.ACTIVE, CooldownRejectionReason.PENDING,
                            CooldownRejectionReason.RECOVERY, CooldownRejectionReason.UNAVAILABLE);
                    for (int index = 0; index < obligations.size(); index++) {
                        var f = new Fixture(data(obligations.get(index)));
                        for (int duration : new int[] {0, 120}) {
                            reject(f.prepare(duration, ref(ID, 99), new Receipt()), expected.get(index));
                        }
                        check(f.publications == 0, "old SkillId obligation cannot be washed by zero/new revision");
                    }
                    for (int mutation = 0; mutation < 8; mutation++) {
                        var f = new Fixture(data());
                        switch (mutation) {
                            case 0 -> f.server.main = false;
                            case 1 -> f.actor.connection.accepting = false;
                            case 2 -> f.actor.connection.actual.connected = false;
                            case 3 -> f.actor.connection.player = new ServerPlayer(f.server, f.actor.id);
                            case 4 -> f.server.players.values.remove(f.actor.id);
                            case 5 -> f.source.copy = false;
                            case 6 -> f.body.account.candidate = f.body;
                            case 7 -> f.source.value = null;
                            default -> throw new AssertionError();
                        }
                        reject(f.prepare(0, ref(ID, 7), new Receipt()), CooldownRejectionReason.UNAVAILABLE);
                        check(f.publications == 0, "invalid zero source cannot initialize or publish");
                    }
                    var unbound = new Fixture(P11CastCooldownData.unbound());
                    reject(unbound.prepare(0, ref(ID, 7), new Receipt()), CooldownRejectionReason.UNAVAILABLE);
                    var failed = new Fixture(data()); failed.failedWriter(true);
                    reject(failed.prepare(0, ref(ID, 7), new Receipt()), CooldownRejectionReason.UNAVAILABLE);
                    failed.failedWriter(false);
                    check(failed.prepare(0, ref(ID, 7), new Receipt()) instanceof P11CastCooldownService.Zero, "actual completed writer clears failure guard");
                }
                static void installation() {
                    for (int mutation = 0; mutation < 6; mutation++) {
                        var f = new Fixture(data()); var receipt = new Receipt();
                        var pending = prepared(f.prepare(120, ref(ID, 7), receipt));
                        switch (mutation) {
                            case 0 -> f.body.source = new P11ReceiptLedger.Source(11, 8);
                            case 1 -> { f.publishVersion = false; f.install(new Receipt()); }
                            case 2 -> f.body.account.candidate = f.body;
                            case 3 -> f.actor.connection.actual.connected = false;
                            case 4 -> receipt.fact = P11CastCooldownService.ReleaseFact.NO_RELEASE;
                            case 5 -> receipt.fact = P11CastCooldownService.ReleaseFact.UNKNOWN;
                            default -> throw new AssertionError();
                        }
                        int before = f.publications; var material = f.actor.cooldown;
                        check(!f.service.installPending(pending), "stale/closed prepared install refused " + mutation);
                        check(f.publications == before && f.actor.cooldown == material, "refused install leaves latest material unchanged");
                    }
                    var f = new Fixture(data()); var pending = prepared(f.prepare(120, ref(ID, 7), new Receipt()));
                    var foreign = new Fixture(data());
                    check(!foreign.service.installPending(pending), "foreign owner cannot install receipt");
                    check(f.service.installPending(pending), "foreign refusal did not consume original custody");
                }
                static void bounds() {
                    for (long time : new long[] {-1, 999}) {
                        var f = new Fixture(data()); f.server.world.time = time;
                        reject(f.prepare(0, ref(ID, 7), new Receipt()), CooldownRejectionReason.CLOCK);
                        check(f.publications == 0, "rollback never lowers floor");
                    }
                    var missing = new Fixture(data()); missing.server.world = null;
                    reject(missing.prepare(0, ref(ID, 7), new Receipt()), CooldownRejectionReason.CLOCK);
                    for (int duration : new int[] {-1, 601}) {
                        var f = new Fixture(data()); reject(f.prepare(duration, ref(ID, 7), new Receipt()), CooldownRejectionReason.UNAVAILABLE);
                        check(f.publications == 0, "invalid duration cannot initialize");
                    }
                    var runtime = new Fixture(data());
                    reject(runtime.service.prepareAdmission(runtime.server, runtime.actor, ref(ID, 7), 1,
                            Long.MAX_VALUE - 100, Long.MAX_VALUE, new Receipt(), runtime.work), CooldownRejectionReason.UNAVAILABLE);
                    reject(runtime.service.prepareAdmission(runtime.server, runtime.actor, ref(ID, 7), 1,
                            0, 100, new Receipt(), runtime.work), CooldownRejectionReason.UNAVAILABLE);
                    var exact = new Fixture(data()); exact.server.world.time = Long.MAX_VALUE - 101 - 600;
                    var bound = prepared(exact.prepare(600, ref(ID, 7), new Receipt()));
                    check(exact.service.installPending(bound), "last representable U accepted");
                    check(exact.body.cooldown.data.entries.get(ID).releaseNotAfter == Long.MAX_VALUE - 600, "exact representable bound");
                    var overflow = new Fixture(data()); overflow.server.world.time = Long.MAX_VALUE - 101 - 600 + 1;
                    reject(overflow.prepare(600, ref(ID, 7), new Receipt()), CooldownRejectionReason.UNAVAILABLE);
                    check(overflow.body.cooldown.data.entries.isEmpty(), "overflow cannot publish pending");
                    var f = new Fixture(data()); var receipt = new Receipt(); var pending = f.install(receipt);
                    receipt.fact = P11CastCooldownService.ReleaseFact.PENDING; f.server.world.time = 1101;
                    check(f.service.prepareArm(pending, f.work, f.actor, 100) != null, "G bound equality allows release");
                    check(f.service.prepareArm(pending, f.work, f.actor, 101) == null, "runtime deadline equality does not release");
                    f.server.world.time = 1102;
                    check(f.service.prepareArm(pending, f.work, f.actor, 100) == null, "G past bound cannot extend release");
                }
                static void publication() {
                    for (boolean error : new boolean[] {false, true}) {
                        for (boolean installedFirst : new boolean[] {false, true}) {
                            var f = new Fixture(data()); var receipt = new Receipt();
                            var pending = prepared(f.prepare(120, ref(ID, 7), receipt));
                            Throwable primary = error ? new AssertionError("owned publication Error") : new IllegalStateException("owned publication RuntimeException");
                            Runnable throwing = () -> raise(primary);
                            if (installedFirst) f.afterPublication = throwing; else f.actor.beforePublication = throwing;
                            sameFailure(() -> f.service.installPending(pending), primary);
                            check(f.publications == (installedFirst ? 1 : 0), "actual publication extent retained");
                            f.afterPublication = () -> {}; f.actor.beforePublication = () -> {};
                            if (installedFirst) {
                                check(f.valid().entries().getFirst().state() == SyncEntryState.PENDING, "installed owned receipt is not orphaned after throw");
                            } else {
                                check(f.body.cooldown.data.entries.isEmpty() && f.valid().entries().getFirst().state() == SyncEntryState.READY,
                                        "pre-publication failure leaves clear material without invented pending");
                            }
                            receipt.fact = P11CastCooldownService.ReleaseFact.NO_RELEASE; pending.settle();
                            check(f.body.cooldown.data.entries.isEmpty(), "known no-release clears only actual installed pending");
                        }
                        for (var fact : List.of(P11CastCooldownService.ReleaseFact.UNKNOWN,
                                P11CastCooldownService.ReleaseFact.NO_RELEASE, P11CastCooldownService.ReleaseFact.ARM)) {
                            var f = new Fixture(data()); var receipt = new Receipt(); var pending = f.install(receipt);
                            receipt.fact = fact; receipt.released = 1000;
                            Throwable primary = error ? new AssertionError("owned settlement Error") : new IllegalStateException("owned settlement RuntimeException");
                            f.afterPublication = () -> raise(primary);
                            sameFailure(pending::settle, primary);
                            var latest = f.body.cooldown.data; int writes = f.publications;
                            check(f.actor.cooldown == latest && writes == 2, "settlement replacement survives later bookkeeping fault");
                            if (fact == P11CastCooldownService.ReleaseFact.UNKNOWN) check(latest.entries.get(ID).kind == 2, "unknown remains uncertain");
                            if (fact == P11CastCooldownService.ReleaseFact.ARM) check(latest.entries.get(ID).kind == 0
                                    && latest.entries.get(ID).expiresAt == 1120, "known ARM never rolls back");
                            if (fact == P11CastCooldownService.ReleaseFact.NO_RELEASE) check(latest.entries.isEmpty(), "known clear remains clear");
                            f.afterPublication = () -> {}; pending.settle();
                            check(f.body.cooldown.data == latest && f.publications == writes, "settlement retry does not replay installed replacement");
                            f.failedWriter(true);
                            var observed = f.valid().entries().getFirst();
                            check(observed.reason() == SyncReason.SAVE_FAILED, "durability failure distinguished from fact");
                            check(observed.state() == (fact == P11CastCooldownService.ReleaseFact.ARM ? SyncEntryState.ACTIVE
                                    : fact == P11CastCooldownService.ReleaseFact.UNKNOWN ? SyncEntryState.RECOVERY_REQUIRED : SyncEntryState.UNAVAILABLE),
                                    "save failure cannot invent Active or Ready");
                            reject(f.prepare(0, ref(ID, 99), new Receipt()), CooldownRejectionReason.UNAVAILABLE);
                            f.failedWriter(false);
                            if (fact == P11CastCooldownService.ReleaseFact.NO_RELEASE) check(f.prepare(0, ref(ID, 99), new Receipt())
                                    instanceof P11CastCooldownService.Zero, "known clear after completed writer permits zero without new root replay");
                        }
                    }
                }
                static P11CastCooldownService.Prepared prepared(P11CastCooldownService.Admission admission) {
                    check(admission instanceof P11CastCooldownService.Prepared, "expected actual Prepared");
                    return (P11CastCooldownService.Prepared) admission;
                }
                static void reject(P11CastCooldownService.Admission admission, CooldownRejectionReason expected) {
                    check(admission instanceof P11CastCooldownService.Rejected rejected && rejected.reason() == expected,
                            "actual rejection must be " + expected + ", was " + admission);
                }
                static void raise(Throwable primary) {
                    if (primary instanceof Error error) throw error;
                    throw (RuntimeException) primary;
                }
                static void sameFailure(Runnable action, Throwable primary) {
                    try { action.run(); throw new AssertionError("original failure swallowed"); }
                    catch (RuntimeException | Error observed) { check(observed == primary, "original failure replaced"); }
                }
                static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
            }
            """; }
}
