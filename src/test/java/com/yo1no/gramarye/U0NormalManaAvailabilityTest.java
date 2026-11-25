package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exact production guard/account/material bodies rehosted over finite typed values.
 * Not authentication, Minecraft world, Attachment registration, native writer or disk proof. */
final class U0NormalManaAvailabilityTest {
    @TempDir static Path temporary;
    private static URLClassLoader loader;
    private static Class<?> control;

    @BeforeAll static void compileActualGuards() throws Exception {
        var root = projectRoot().resolve("src/main/java/com/yo1no/gramarye");
        var mana = root.resolve("magic/runtime/mana");
        var source = scaffold()
                .replace("/* BRIDGE_GUARD */", method(Files.readString(mana.resolve("P6RuntimeExecutionBridge.java")),
                        "    public static boolean normalV0ManaAvailable("))
                .replace("/* ROOT_GUARD */", method(Files.readString(root.resolve("SkillRuntimeService.java")),
                        "    boolean currentV0Actor("))
                .replace("/* CAPTURE_GUARD */", method(Files.readString(root.resolve("V0SlotRuntime.java")),
                        "    private boolean current(PendingCapture capture)"))
                .replace("/* EXISTING_ATTACHMENT */", method(Files.readString(mana.resolve("ManaAttachments.java")),
                        "    static ManaState existing(ServerPlayer player)"))
                .replace("/* ATTACHMENT_READ */", method(Files.readString(mana.resolve("ManaAttachments.java")),
                        "    private static ManaState read(ServerPlayer player, boolean installDefault)"));
        var actualTypes = new StringBuilder();
        for (var leaf : List.of("V0ManaAccountAccess.java", "ManaAccountAccess.java", "ManaState.java",
                "ManaAvailability.java", "ManaDecodeFailure.java", "P6ManaBounds.java")) {
            // Only package/import declarations are relocated; original declarations and bodies stay exact.
            actualTypes.append(Files.readString(mana.resolve(leaf))
                    .replaceAll("(?m)^package [^;]+;\\s*", "")
                    .replaceAll("(?m)^import [^;]+;\\s*", "")).append('\n');
        }
        source = source.replace("/* ACTUAL_TYPES */", actualTypes);
        var java = temporary.resolve("Control.java");
        var classes = Files.createDirectory(temporary.resolve("classes"));
        Files.writeString(java, source, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler);
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            assertTrue(Boolean.TRUE.equals(compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none", "-Xlint:all,-serial,-auxiliaryclass", "-Werror",
                            "-classpath", classes.toString(), "-d", classes.toString()), null,
                    manager.getJavaFileObjects(java)).call()), () -> diagnostics.getDiagnostics().toString());
        }
        loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        control = loader.loadClass("com.yo1no.gramarye.Control");
    }

    @AfterAll static void closeLoader() throws Exception { if (loader != null) loader.close(); }

    @Test void actualExistingManaGateRejectsMissingUnavailableAndWrongThreadWithoutMutation() throws Exception {
        control.getMethod("run", String.class).invoke(null, "material");
    }

    @Test void actualCurrentSourceAndCaptureGuardsRejectSameUuidAndChangedOwnership() throws Exception {
        control.getMethod("run", String.class).invoke(null, "identity");
    }

    private static Path projectRoot() {
        for (var p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("settings.gradle"))) return p;
        }
        throw new AssertionError("project root missing");
    }

    private static String method(String source, String head) {
        int start = source.indexOf(head); assertTrue(start >= 0, head);
        assertEquals(start, source.lastIndexOf(head), head);
        int open = source.indexOf('{', start), end = open + 1, depth = 1;
        while (depth != 0) {
            char c = source.charAt(end++); if (c == '{') depth++; else if (c == '}') depth--;
        }
        return source.substring(start, end) + '\n';
    }

    private static String scaffold() { return """
            package com.yo1no.gramarye;
            import java.util.*;
            public final class Control {
                private static final P6RuntimeExecutionCapability.V0 CAP = new P6RuntimeExecutionCapability.V0();
                /* BRIDGE_GUARD */
                private static void require(boolean value) { if (!value) throw new AssertionError("guard control"); }
                public static void run(String which) {
                    if (which.equals("material")) material(); else if (which.equals("identity")) identity();
                    else throw new AssertionError("unknown finite control");
                }
                private static void material() {
                    var s = new MinecraftServer(); var a = new ServerPlayer(s);
                    boolean refusedNull = false;
                    try { normalV0ManaAvailable(null, a); } catch (NullPointerException expected) { refusedNull = true; }
                    require(refusedNull && a.hasReads == 0 && a.dataReads == 0);
                    require(!normalV0ManaAvailable(CAP, null));
                    a.server = null; require(!normalV0ManaAvailable(CAP, a)); a.server = s;
                    s.main = false; require(!normalV0ManaAvailable(CAP, a)); s.main = true;
                    require(a.hasReads == 0 && a.dataReads == 0);
                    require(!normalV0ManaAvailable(CAP, a));
                    require(a.hasReads == 1 && a.dataReads == 0 && a.defaultInstalls == 0);
                    a.material = ManaState.unavailable(ManaDecodeFailure.WRONG_BALANCE_TYPE);
                    a.clearReads(); require(!normalV0ManaAvailable(CAP, a));
                    require(a.hasReads == 1 && a.dataReads == 1);
                    for (long b : new long[]{0, P6ManaBounds.MAX_MANA_VALUE}) {
                        a.material = ManaState.available(b); a.clearReads();
                        require(normalV0ManaAvailable(CAP, a));
                        require(a.hasReads == 2 && a.dataReads == 2 && a.material.balance() == b);
                    }
                    // Malformed balances cannot become legal existing material through the actual type.
                    for (long b : new long[]{-1, P6ManaBounds.MAX_MANA_VALUE + 1, Long.MAX_VALUE}) {
                        boolean rejected = false;
                        try { ManaState.available(b); } catch (IllegalArgumentException expected) { rejected = true; }
                        require(rejected);
                    }
                    for (Throwable primary : new Throwable[]{new AssertionError("same primary"),
                            new IllegalStateException("same primary")}) {
                        a.material = ManaState.available(0); a.clearReads(); a.readFailure = primary;
                        Throwable caught = null;
                        try { normalV0ManaAvailable(CAP, a); } catch (RuntimeException | Error failure) { caught = failure; }
                        require(caught == primary && a.dataReads == 0); a.readFailure = null;
                    }
                    require(a.dataWrites == 0 && a.defaultInstalls == 0 && P11ManaMaterial.captures == 0);
                }
                private static void identity() {
                    var f = new Fixture(); require(f.current());
                    var replacement = new ServerPlayer(f.server); replacement.id = f.actor.id;
                    var original = f.capture.actor; f.capture.actor = replacement; require(!f.current());
                    f.capture.actor = original; require(f.current());
                    var source = f.service.foundation.owner;
                    f.service.foundation.owner = new P11QualifiedSourceOwner(); require(!f.current());
                    f.service.foundation.owner = source;
                    var body = f.source.values.get(f.actor);
                    f.source.values.put(f.actor, new P11QualifiedSourceOwner.Body()); require(!f.current());
                    f.source.values.put(f.actor, body);
                    var proof = body.source; body.source = new P11ReceiptLedger.Source(); require(!f.current()); body.source = proof;
                    f.source.qualified = false; require(!f.current()); f.source.qualified = true;
                    f.witness.current = false; require(!f.current()); f.witness.current = true;
                    f.witness.actor = replacement; require(!f.current()); f.witness.actor = f.actor;
                    var authority = f.runtime.authority; f.runtime.authority = new V0CatalogAuthority();
                    require(!f.current()); f.runtime.authority = authority;
                    var slot = f.service.slots.get(f.server);
                    slot.state = ServerSlot.State.STOPPED; require(!f.current()); slot.state = ServerSlot.State.RUNNING;
                    var v0 = slot.v0; slot.v0 = new V0SlotRuntime(); require(!f.current()); slot.v0 = v0;
                    f.server.main = false; require(!f.current()); f.server.main = true;
                    f.server.running = false; require(!f.current()); f.server.running = true;
                    f.server.stopped = true; require(!f.current()); f.server.stopped = false;
                    f.actor.removed = true; require(!f.current()); f.actor.removed = false;
                    f.actor.alive = false; require(!f.current()); f.actor.alive = true;
                    var listener = f.actor.connection; f.actor.connection = new Object(); require(!f.current()); f.actor.connection = listener;
                    var level = f.actor.level; f.actor.level = new Object(); require(!f.current()); f.actor.level = level;
                    f.capture.geometry.loaded = false; require(!f.current()); f.capture.geometry.loaded = true;
                    require(f.current() && f.actor.dataWrites == 0 && f.actor.defaultInstalls == 0);
                }
            }
            final class Fixture {
                final MinecraftServer server = new MinecraftServer();
                final ServerPlayer actor = new ServerPlayer(server);
                final P11QualifiedSourceOwner source = new P11QualifiedSourceOwner();
                final SkillRuntimeService service = new SkillRuntimeService();
                final V0SlotRuntime runtime = new V0SlotRuntime();
                final V0CatalogAuthority authority = new V0CatalogAuthority();
                final P7ServerAuthorizationBoundary.V0PlayWitness witness = new P7ServerAuthorizationBoundary.V0PlayWitness();
                final V0SlotRuntime.PendingCapture capture = new V0SlotRuntime.PendingCapture();
                Fixture() {
                    service.foundation = new Foundation(); service.foundation.owner = source;
                    runtime.runtime = service; runtime.authority = authority;
                    var slot = new ServerSlot(); slot.state = ServerSlot.State.RUNNING; slot.v0 = runtime;
                    service.slots.put(server, slot);
                    var body = new P11QualifiedSourceOwner.Body(); body.source = new P11ReceiptLedger.Source();
                    source.values.put(actor, body);
                    witness.authority = authority; witness.server = server; witness.actor = actor;
                    capture.actor = actor; capture.listener = actor.connection; capture.geometry = new Geometry();
                    capture.geometry.level = actor.level; capture.witness = witness; capture.source = source;
                    capture.body = body; capture.sourceProof = body.source;
                }
                boolean current() { return runtime.probe(capture); }
            }
            final class SkillRuntimeService {
                Foundation foundation;
                final IdentityHashMap<MinecraftServer,ServerSlot> slots = new IdentityHashMap<>();
                /* ROOT_GUARD */
            }
            final class V0SlotRuntime {
                SkillRuntimeService runtime; V0CatalogAuthority authority;
                boolean hasAuthority(V0CatalogAuthority expected) { return authority == expected; }
                boolean probe(PendingCapture capture) { return current(capture); }
                /* CAPTURE_GUARD */
                static final class PendingCapture {
                    ServerPlayer actor; Object listener; Geometry geometry;
                    P7ServerAuthorizationBoundary.V0PlayWitness witness; P11QualifiedSourceOwner source;
                    P11QualifiedSourceOwner.Body body; P11ReceiptLedger.Source sourceProof;
                }
            }
            final class Geometry { Object level; boolean loaded = true; boolean loaded() { return loaded; } }
            final class ServerSlot { enum State { RUNNING, STOPPED } State state; V0SlotRuntime v0; }
            final class V0CatalogAuthority {}
            final class Foundation {
                P11QualifiedSourceOwner owner;
                P11QualifiedSourceOwner sourceOwner(MinecraftServer server) { return owner; }
            }
            final class P11QualifiedSourceOwner {
                boolean qualified = true;
                final IdentityHashMap<ServerPlayer,Body> values = new IdentityHashMap<>();
                Body body(ServerPlayer actor) { return values.get(actor); }
                boolean qualifiedForV0Capture(Body body) { return qualified; }
                static final class Body { P11ReceiptLedger.Source source; }
            }
            final class P11ReceiptLedger { static final class Source {} }
            final class P7ServerAuthorizationBoundary {
                static final class V0PlayWitness {
                    V0CatalogAuthority authority; MinecraftServer server; ServerPlayer actor; boolean current = true;
                }
                static boolean currentV0Play(V0CatalogAuthority authority, V0PlayWitness witness,
                        MinecraftServer server, ServerPlayer actor) {
                    return witness.current && witness.authority == authority && witness.server == server && witness.actor == actor;
                }
            }
            final class MinecraftServer {
                boolean main = true, running = true, stopped;
                boolean isSameThread() { return main; }
                boolean isRunning() { return running; }
                boolean isStopped() { return stopped; }
            }
            final class ServerPlayer {
                MinecraftServer server; UUID id = UUID.randomUUID(); Object connection = new Object(), level = new Object();
                boolean alive = true, removed;
                ManaState material; int hasReads, dataReads, dataWrites, defaultInstalls; Throwable readFailure;
                ServerPlayer(MinecraftServer server) { this.server = server; }
                MinecraftServer getServer() { return server; }
                UUID getUUID() { return id; }
                Object serverLevel() { return level; }
                boolean isAlive() { return alive; }
                boolean isRemoved() { return removed; }
                boolean hasData(Object type) {
                    hasReads++;
                    if (readFailure instanceof RuntimeException r) throw r;
                    if (readFailure instanceof Error e) throw e;
                    return material != null;
                }
                ManaState getData(Object type) {
                    dataReads++; if (material == null) { defaultInstalls++; material = ManaState.freshDefault(); }
                    return material;
                }
                void setData(Object type, ManaState value) { dataWrites++; material = value; }
                void clearReads() { hasReads = 0; dataReads = 0; }
            }
            final class P6RuntimeExecutionCapability { static final class V0 {} }
            final class ManaAttachments {
                private static final Object PLAYER_MANA = new Object();
                /* EXISTING_ATTACHMENT */
                /* ATTACHMENT_READ */
                static void replace(ServerPlayer player, ManaState value) { player.setData(PLAYER_MANA, value); }
            }
            final class P11ManaMaterial {
                static int captures;
                static State capture(ServerPlayer actor) { captures++; return new State(); }
                static final class State { boolean isCurrent(ServerPlayer actor) { return true; } }
            }
            final class ManaStateCodec { static final long UNAVAILABLE_PERSISTENCE_BALANCE = -1L; }
            /* ACTUAL_TYPES */
            """; }
}
